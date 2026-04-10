package com.youlai.boot.market.order.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.youlai.boot.framework.security.util.SecurityUtils;
import com.youlai.boot.market.order.enums.OrderStatusEnum;
import com.youlai.boot.market.order.mapper.SmsPhoneRecordMapper;
import com.youlai.boot.market.order.model.entity.SmsOrder;
import com.youlai.boot.market.order.model.entity.SmsPhoneRecord;
import com.youlai.boot.market.order.model.query.SmsPhoneRecordQuery;
import com.youlai.boot.market.order.model.vo.SmsPhoneRecordPageVO;
import com.youlai.boot.market.order.service.SmsOrderService;
import com.youlai.boot.market.order.service.SmsPhoneRecordService;
import com.youlai.boot.market.order.strategy.SmsChannelContext;
import com.youlai.boot.market.order.strategy.SmsChannelStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 短信发送记录业务实现类
 *
 * @author Ray.Hao
 * @since 2026/04/07
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmsPhoneRecordServiceImpl implements SmsPhoneRecordService {

    private final SmsPhoneRecordMapper smsPhoneRecordMapper;
    private final SmsOrderService smsOrderService;
    private final SmsChannelContext smsChannelContext;

    @Override
    public Page<SmsPhoneRecordPageVO> getSmsPhoneRecordPage(SmsPhoneRecordQuery queryParams) {
        // 数据权限：非管理员只能查看自己的记录
        Long currentUserId = SecurityUtils.getUserId();
        boolean isRoot = SecurityUtils.isRoot();

        Page<SmsPhoneRecordPageVO> page = new Page<>(queryParams.getPageNum(), queryParams.getPageSize());
        return smsPhoneRecordMapper.getSmsPhoneRecordPage(page, queryParams, currentUserId, isRoot);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSendResult(Long orderNo, SmsChannelStrategy.SmsSendResult sendResult, String channelCode) {
        if (sendResult == null || CollUtil.isEmpty(sendResult.msgIds())) {
            log.warn("发送结果为空或消息ID列表为空，订单编号: {}", orderNo);
            return;
        }

        List<String> msgIds = sendResult.msgIds();

        // 查询该订单的所有手机号记录
        LambdaQueryWrapper<SmsPhoneRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SmsPhoneRecord::getOrderNo, orderNo)
            .isNull(SmsPhoneRecord::getMsgId); // 只更新还没有msgId的记录

        List<SmsPhoneRecord> records = smsPhoneRecordMapper.selectList(wrapper);

        if (CollUtil.isEmpty(records)) {
            log.warn("未找到需要更新的手机号记录，订单编号: {}", orderNo);
            return;
        }

        // 将msgId分配给对应的记录（按顺序）
        for (int i = 0; i < records.size() && i < msgIds.size(); i++) {
            SmsPhoneRecord record = records.get(i);
            record.setMsgId(msgIds.get(i));
            record.setChannel(channelCode);
            record.setSendStatus(1); // 发送成功
            record.setSendTime(LocalDateTime.now());
        }

        // 批量更新
        for (SmsPhoneRecord record : records) {
            smsPhoneRecordMapper.updateById(record);
        }

        log.info("保存发送结果成功，订单编号: {}, 更新记录数: {}", orderNo, records.size());
    }

    @Override
    public void updateReportResult(SmsChannelStrategy.SmsReportResult reportResult) {
        if (reportResult == null || !reportResult.success() || CollUtil.isEmpty(reportResult.statusList())) {
            log.warn("状态报告结果为空或失败");
            return;
        }

        List<SmsChannelStrategy.SmsReportResult.SmsStatus> statusList = reportResult.statusList();
        int updateCount = 0;

        for ( SmsChannelStrategy.SmsReportResult.SmsStatus status : statusList) {
            if (StrUtil.isBlank(status.msgId())) {
                continue;
            }

            // 根据msgId查询记录
            LambdaQueryWrapper<SmsPhoneRecord> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SmsPhoneRecord::getMsgId, status.msgId());
            SmsPhoneRecord record = smsPhoneRecordMapper.selectOne(wrapper);

            if (record == null) {
                log.warn("未找到对应的短信记录，msgId: {}", status.msgId());
                continue;
            }

            // 更新状态报告信息
            LambdaUpdateWrapper<SmsPhoneRecord> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(SmsPhoneRecord::getRecordId, record.getRecordId())
                .set(SmsPhoneRecord::getReceiveTime, parseReceiveTime(status.receiveTime()));

            // 根据状态码更新发送状态和失败原因
            Integer sendStatus = convertToSendStatus(status.status());
            if (sendStatus != null) {
                updateWrapper.set(SmsPhoneRecord::getSendStatus, sendStatus);
            }

            String failReason = getStatusDescription(status.status());
            if (StrUtil.isNotBlank(failReason)) {
                updateWrapper.set(SmsPhoneRecord::getFailReason, failReason);
            }

            // 更新费用详情
            SmsChannelStrategy.SmsReportResult.PriceDetail priceDetail = status.priceDetail();
            if (priceDetail != null) {
                updateWrapper.set(SmsPhoneRecord::getPayAmount, priceDetail.payAmount())
                    .set(SmsPhoneRecord::getCurrency, priceDetail.currency())
                    .set(SmsPhoneRecord::getChargeCount, priceDetail.chargeCount())
                    .set(SmsPhoneRecord::getUnitPrice, priceDetail.unitPrice())
                    .set(SmsPhoneRecord::getQuoteExchange, priceDetail.quoteExchange())
                    .set(SmsPhoneRecord::getSettlePay, priceDetail.settlePay())
                    .set(SmsPhoneRecord::getSettleCurrency, priceDetail.settleCurrency())
                    .set(SmsPhoneRecord::getMePayAmount, priceDetail.settlePay())
                    .set(SmsPhoneRecord::getSettleUnitPrice, priceDetail.settleUnitPrice());
            }

            smsPhoneRecordMapper.update(null, updateWrapper);
            updateCount++;
        }

        log.info("更新状态报告成功，共更新 {} 条记录", updateCount);
    }

    @Override
    public void queryAndUpdateReport(Long orderNo, String channelCode) {
        // 1. 查询该订单下所有已发送但未收到状态报告的记录
        LambdaQueryWrapper<SmsPhoneRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SmsPhoneRecord::getOrderNo, orderNo)
            .isNotNull(SmsPhoneRecord::getMsgId)
            .eq(SmsPhoneRecord::getSendStatus, 1); // 已发送

        List<SmsPhoneRecord> records = smsPhoneRecordMapper.selectList(wrapper);

        if (CollUtil.isEmpty(records)) {
            log.debug("订单 {} 没有需要查询状态报告的记录", orderNo);
            return;
        }

        // 2. 提取msgId列表
        List<String> msgIds = records.stream()
            .map(SmsPhoneRecord::getMsgId)
            .filter(StrUtil::isNotBlank)
            .collect(Collectors.toList());

        if (CollUtil.isEmpty(msgIds)) {
            log.debug("订单 {} 没有有效的msgId", orderNo);
            return;
        }

        // 3. 调用渠道策略查询状态报告
        try {
            SmsChannelStrategy strategy = smsChannelContext.getStrategy(channelCode);
            SmsChannelStrategy.SmsReportResult reportResult = strategy.queryReport(msgIds);

            // 4. 更新状态报告
            if (reportResult != null && reportResult.success()) {
                updateReportResult(reportResult);

                // 5. 检查是否所有记录都已完成，更新订单状态
                checkAndUpdateOrderStatus(orderNo);
            } else {
                log.warn("查询状态报告失败，订单编号: {}, 错误信息: {}", orderNo,
                    reportResult != null ? reportResult.message() : "未知错误");
            }
        } catch (Exception e) {
            log.error("查询状态报告异常，订单编号: {}", orderNo, e);
        }
    }

    /**
     * 解析接收时间
     *
     * @param receiveTimeStr 接收时间字符串
     * @return LocalDateTime
     */
    private LocalDateTime parseReceiveTime(String receiveTimeStr) {
        if (StrUtil.isBlank(receiveTimeStr)) {
            return null;
        }

        try {
            // Onbuka返回的格式：2021-02-12T09:30:03+08:00
            DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
            return LocalDateTime.parse(receiveTimeStr, formatter);
        } catch (Exception e) {
            log.warn("解析接收时间失败: {}", receiveTimeStr, e);
            return null;
        }
    }

    /**
     * 获取状态描述
     *
     * @param statusCode 状态码
     * @return 状态描述
     */
    private String getStatusDescription(Integer statusCode) {
        if (statusCode == null) {
            return null;
        }

        return switch (statusCode) {
            case 0 -> "送达";
            case -1 -> "发送中";
            case 1 -> "发送失败";
            default -> "未知状态(" + statusCode + ")";
        };
    }

    /**
     * 转换状态码为发送状态
     *
     * @param statusCode 状态码
     * @return 发送状态：0=待发送，1=发送成功，2=发送失败
     */
    private Integer convertToSendStatus(Integer statusCode) {
        if (statusCode == null) {
            return null;
        }

        return switch (statusCode) {
            case 0 -> 1; // 送达 -> 发送成功
            case -1 -> 1; // 发送中 -> 发送成功（临时状态）
            case 1 -> 2; // 发送失败
            default -> null;
        };
    }

    /**
     * 检查并更新订单状态
     *
     * @param orderNo 订单编号
     */
    private void checkAndUpdateOrderStatus(Long orderNo) {
        // 查询订单下所有记录的状态
        LambdaQueryWrapper<SmsPhoneRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SmsPhoneRecord::getOrderNo, orderNo);

        List<SmsPhoneRecord> allRecords = smsPhoneRecordMapper.selectList(wrapper);

        if (CollUtil.isEmpty(allRecords)) {
            return;
        }

        // 统计各种状态的记录数
        Map<Integer, Long> statusCount = allRecords.stream()
            .collect(Collectors.groupingBy(SmsPhoneRecord::getSendStatus, Collectors.counting()));

        long successCount = statusCount.getOrDefault(1, 0L);
        long failCount = statusCount.getOrDefault(2, 0L);
        long pendingCount = statusCount.getOrDefault(0, 0L);

        // 如果所有记录都已完成（成功或失败），更新订单状态
        if (pendingCount == 0) {
            SmsOrder order = smsOrderService.getById(orderNo);
            if (order != null) {
                order.setStatus(OrderStatusEnum.COMPLETED.getValue());
                order.setSuccessCount((int) successCount);
                order.setFailCount((int) failCount);
                smsOrderService.updateById(order);

                log.info("订单 {} 所有短信发送完成，成功: {}, 失败: {}", orderNo, successCount, failCount);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateFailedRecords(Long orderNo, String channelCode, String failReason) {
        // 查询该订单下所有待发送的手机号记录
        LambdaQueryWrapper<SmsPhoneRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SmsPhoneRecord::getOrderNo, orderNo)
            .eq(SmsPhoneRecord::getSendStatus, 0); // 只更新待发送的记录

        List<SmsPhoneRecord> records = smsPhoneRecordMapper.selectList(wrapper);

        if (CollUtil.isEmpty(records)) {
            log.warn("未找到需要更新的待发送记录，订单编号: {}", orderNo);
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // 批量更新为发送失败状态
        for (SmsPhoneRecord record : records) {
            record.setChannel(channelCode); // 设置渠道代码
            record.setSendStatus(2); // 发送失败
            record.setFailReason(failReason);
            record.setSendTime(now);
        }

        // 批量更新数据库
        for (SmsPhoneRecord record : records) {
            smsPhoneRecordMapper.updateById(record);
        }

        log.info("批量更新发送失败记录成功，订单编号: {}, 渠道: {}, 更新记录数: {}, 失败原因: {}",
            orderNo, channelCode, records.size(), failReason);
    }

}
