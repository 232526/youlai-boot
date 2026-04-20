package com.youlai.boot.market.order.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.youlai.boot.framework.security.util.SecurityUtils;
import com.youlai.boot.market.order.enums.OrderStatusEnum;
import com.youlai.boot.market.order.mapper.SmsPhoneRecordMapper;
import com.youlai.boot.market.order.model.entity.SmsOrder;
import com.youlai.boot.market.order.model.entity.SmsPhoneRecord;
import com.youlai.boot.market.order.model.entity.UserTransaction;
import com.youlai.boot.market.order.model.query.SmsPhoneRecordQuery;
import com.youlai.boot.market.order.model.vo.SmsPhoneRecordPageVO;
import com.youlai.boot.market.order.service.SmsOrderService;
import com.youlai.boot.market.order.service.SmsPhoneRecordService;
import com.youlai.boot.market.order.service.UserTransactionService;
import com.youlai.boot.market.order.strategy.SmsChannelContext;
import com.youlai.boot.market.order.strategy.SmsChannelStrategy;
import com.youlai.boot.system.mapper.UserMapper;
import com.youlai.boot.system.model.entity.SysUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
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
public class SmsPhoneRecordServiceImpl extends ServiceImpl<SmsPhoneRecordMapper, SmsPhoneRecord> implements SmsPhoneRecordService {

    private final SmsPhoneRecordMapper smsPhoneRecordMapper;
    private final SmsOrderService smsOrderService;
    private final SmsChannelContext smsChannelContext;
    private final UserTransactionService userTransactionService;
    private final com.youlai.boot.system.service.UserService userService;
    private final UserMapper userMapper;

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
    public void saveSendResult(String orderNo, SmsChannelStrategy.SmsSendResult sendResult, String channelCode) {
        if (sendResult == null || CollUtil.isEmpty(sendResult.msgIds())) {
            log.warn("发送结果为空或消息ID列表为空，订单编号: {}", orderNo);
            return;
        }

        List<String> msgIds = sendResult.msgIds();

        // 只查主键ID，避免加载完整实体到内存
        LambdaQueryWrapper<SmsPhoneRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(SmsPhoneRecord::getRecordId)
                .eq(SmsPhoneRecord::getOrderNo, orderNo)
                .isNull(SmsPhoneRecord::getMsgId) // 只更新还没有msgId的记录
                .last("LIMIT " + msgIds.size());

        List<Object> recordIds = smsPhoneRecordMapper.selectObjs(wrapper);

        if (CollUtil.isEmpty(recordIds)) {
            log.warn("未找到需要更新的手机号记录，订单编号: {}", orderNo);
            return;
        }

        // 根据主键ID直接更新，每条记录分配对应的msgId
        LocalDateTime now = LocalDateTime.now();
        int updateCount = Math.min(recordIds.size(), msgIds.size());
        for (int i = 0; i < updateCount; i++) {
            LambdaUpdateWrapper<SmsPhoneRecord> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(SmsPhoneRecord::getRecordId, recordIds.get(i))
                    .set(SmsPhoneRecord::getMsgId, msgIds.get(i))
                    .set(SmsPhoneRecord::getChannel, channelCode)
                    .set(SmsPhoneRecord::getSendStatus, 1) // 发送中（等待状态报告）
                    .set(SmsPhoneRecord::getSendTime, now);
            smsPhoneRecordMapper.update(null, updateWrapper);
        }

        log.info("保存发送结果成功，订单编号: {}, 更新记录数: {}", orderNo, updateCount);
    }

    @Override
    public void updateReportResult(SmsChannelStrategy.SmsReportResult reportResult) {
        if (reportResult == null || !reportResult.success() || CollUtil.isEmpty(reportResult.statusList())) {
            log.warn("状态报告结果为空或失败");
            return;
        }

        List<SmsChannelStrategy.SmsReportResult.SmsStatus> statusList = reportResult.statusList();
        int updateCount = 0;

        // 第一步：收集所有失败的记录，用于后续反转处理
        List<SmsPhoneRecord> failedRecords = new ArrayList<>();
        Map<String, SmsChannelStrategy.SmsReportResult.SmsStatus> statusMap = new HashMap<>();

        for (SmsChannelStrategy.SmsReportResult.SmsStatus status : statusList) {
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

            //获取用户信息（使用缓存）
            SysUser user = userService.getUserById(record.getCreateBy());
            //反转率
            Integer flipRate = user.getFlipRate();

            // 根据状态码更新发送状态和失败原因
            Integer sendStatus = convertToSendStatus(status.status());

            // 如果上游返回失败，且设置了反转率，则先收集起来后续处理
            if (sendStatus != null && sendStatus == -1 && flipRate != null && flipRate > 0) {
                failedRecords.add(record);
                statusMap.put(record.getMsgId(), status);
            } else {
                // 非失败记录或没有反转率，直接更新
                updateRecordStatus(record, status, sendStatus, user);
                updateCount++;
            }
        }

        // 第二步：处理失败记录的反转逻辑
        if (!failedRecords.isEmpty()) {
            int flippedCount = applyFlipRate(failedRecords, statusMap);
            updateCount += flippedCount;
            log.info("应用反转率，共处理 {} 条失败记录，其中 {} 条反转为成功", failedRecords.size(), flippedCount);
        }

        log.info("更新状态报告成功，共更新 {} 条记录", updateCount);
    }

    @Override
    public void queryAndUpdateReport(String orderNo, String channelCode) {
        // 1. 查询该订单下所有已发送但未收到状态报告的记录
        LambdaQueryWrapper<SmsPhoneRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SmsPhoneRecord::getOrderNo, orderNo).isNotNull(SmsPhoneRecord::getMsgId).eq(SmsPhoneRecord::getSendStatus, 1); // 发送中（等待状态报告）

        List<SmsPhoneRecord> records = smsPhoneRecordMapper.selectList(wrapper);

        if (CollUtil.isEmpty(records)) {
            log.debug("订单 {} 没有需要查询状态报告的记录", orderNo);
            return;
        }

        // 2. 提取msgId列表
        List<String> msgIds = records.stream().map(SmsPhoneRecord::getMsgId).filter(StrUtil::isNotBlank).collect(Collectors.toList());

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
                log.warn("查询状态报告失败，订单编号: {}, 错误信息: {}", orderNo, reportResult != null ? reportResult.message() : "未知错误");
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
     * @param statusCode 状态码（Onbuka平台返回：0=送达，-1=发送中，1=发送失败）
     * @return 发送状态：0=未发送，1=发送中，2=发送成功，-1=发送失败
     */
    private Integer convertToSendStatus(Integer statusCode) {
        if (statusCode == null) {
            return null;
        }

        return switch (statusCode) {
            case 0 -> 2;   // Onbuka送达(0) -> 发送成功(2)
            case -1 -> 1;  // Onbuka发送中(-1) -> 发送中(1)
            case 1 -> -1;  // Onbuka发送失败(1) -> 发送失败(-1)
            default -> null;
        };
    }

    /**
     * 应用反转率：将部分失败记录反转为成功
     *
     * @param failedRecords 失败的记录列表
     * @param statusMap     状态映射
     * @return 反转为成功的记录数量
     */
    private int applyFlipRate(List<SmsPhoneRecord> failedRecords, Map<String, SmsChannelStrategy.SmsReportResult.SmsStatus> statusMap) {
        if (CollUtil.isEmpty(failedRecords)) {
            return 0;
        }

        // 按用户分组，因为不同用户的反转率可能不同
        Map<Long, List<SmsPhoneRecord>> recordsByUser = failedRecords.stream().collect(Collectors.groupingBy(SmsPhoneRecord::getCreateBy));

        int totalFlippedCount = 0;

        // 遍历每个用户的失败记录
        for (Map.Entry<Long, List<SmsPhoneRecord>> entry : recordsByUser.entrySet()) {
            Long userId = entry.getKey();
            List<SmsPhoneRecord> userFailedRecords = entry.getValue();

            // 获取用户信息
            SysUser user = userService.getUserById(userId);
            Integer flipRate = user.getFlipRate();

            if (flipRate == null || flipRate <= 0) {
                // 没有设置反转率或反转率为0，全部标记为失败
                for (SmsPhoneRecord record : userFailedRecords) {
                    SmsChannelStrategy.SmsReportResult.SmsStatus status = statusMap.get(record.getMsgId());
                    updateRecordStatus(record, status, -1, user);
                }
                continue;
            }

            // 计算需要反转的数量
            int totalCount = userFailedRecords.size();
            int flipCount = (int) Math.round(totalCount * flipRate / 100.0);
            // 确保至少有一条可以反转（如果有失败记录且反转率>0）
            flipCount = Math.min(flipCount, totalCount);
            int failCount = totalCount - flipCount;

            log.info("用户 {} 的失败记录应用反转率：总数={}, 反转率={}%, 反转成功数={}, 保持失败数={}", userId, totalCount, flipRate, flipCount, failCount);

            // 随机打乱记录顺序，确保公平性
            Collections.shuffle(userFailedRecords);

            // 前 flipCount 条反转为成功
            for (int i = 0; i < flipCount; i++) {
                SmsPhoneRecord record = userFailedRecords.get(i);
                SmsChannelStrategy.SmsReportResult.SmsStatus status = statusMap.get(record.getMsgId());
                updateFlipRecordStatus(record, status, user);
            }
            totalFlippedCount += flipCount;

            // 剩余的保持失败
            for (int i = flipCount; i < totalCount; i++) {
                SmsPhoneRecord record = userFailedRecords.get(i);
                SmsChannelStrategy.SmsReportResult.SmsStatus status = statusMap.get(record.getMsgId());
                updateRecordStatus(record, status, -1, user);
            }
        }

        return totalFlippedCount;
    }

    /**
     * 更新反转单条记录的状态
     *
     * @param record 短信记录
     * @param status 状态报告
     * @param user   用户信息
     */
    private void updateFlipRecordStatus(SmsPhoneRecord record, SmsChannelStrategy.SmsReportResult.SmsStatus status, SysUser user) {
        LambdaUpdateWrapper<SmsPhoneRecord> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(SmsPhoneRecord::getRecordId, record.getRecordId()).set(SmsPhoneRecord::getReceiveTime, parseReceiveTime(status.receiveTime()));

        // 更新发送状态
        updateWrapper.set(SmsPhoneRecord::getSendStatus, 2);

        updateWrapper.set(SmsPhoneRecord::getFlipFailReason, status.statusDesc());

        // 更新费用详情
        SmsChannelStrategy.SmsReportResult.PriceDetail priceDetail = status.priceDetail();
        if (priceDetail != null) {
            // 使用 BigDecimal 计算外部费用，避免精度丢失
            BigDecimal outUnitPrice = user.getSmsUnitPrice() != null ? BigDecimal.valueOf(user.getSmsUnitPrice()) : BigDecimal.ZERO;
            BigDecimal chargeCount = priceDetail.chargeCount() != null ? BigDecimal.valueOf(priceDetail.chargeCount()) : BigDecimal.valueOf(1);
            BigDecimal outPayAmount = outUnitPrice.multiply(chargeCount);

            updateWrapper.set(SmsPhoneRecord::getPayAmount, priceDetail.payAmount()).set(SmsPhoneRecord::getCurrency, priceDetail.currency()).set(SmsPhoneRecord::getChargeCount, chargeCount).set(SmsPhoneRecord::getUnitPrice, priceDetail.unitPrice()).set(SmsPhoneRecord::getQuoteExchange, priceDetail.quoteExchange()).set(SmsPhoneRecord::getSettlePay, priceDetail.settlePay()).set(SmsPhoneRecord::getSettleCurrency, priceDetail.settleCurrency()).set(SmsPhoneRecord::getMePayAmount, priceDetail.settlePay()).set(SmsPhoneRecord::getOutUnitPrice, user.getSmsUnitPrice()).set(SmsPhoneRecord::getIsFlip, 1).set(SmsPhoneRecord::getOutPayAmount, outPayAmount.doubleValue()).set(SmsPhoneRecord::getSettleUnitPrice, priceDetail.settleUnitPrice());
        }

        smsPhoneRecordMapper.update(null, updateWrapper);
    }


    /**
     * 更新单条记录的状态
     *
     * @param record     短信记录
     * @param status     状态报告
     * @param sendStatus 发送状态：0=未发送，1=发送中，2=发送成功，-1=发送失败
     * @param user       用户信息
     */
    private void updateRecordStatus(SmsPhoneRecord record, SmsChannelStrategy.SmsReportResult.SmsStatus status, Integer sendStatus, SysUser user) {
        LambdaUpdateWrapper<SmsPhoneRecord> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(SmsPhoneRecord::getRecordId, record.getRecordId()).set(SmsPhoneRecord::getReceiveTime, parseReceiveTime(status.receiveTime()));

        // 更新发送状态
        if (sendStatus != null) {
            updateWrapper.set(SmsPhoneRecord::getSendStatus, sendStatus);
        }

        // 更新失败原因（只有失败时才设置）
        if (sendStatus != null && sendStatus == -1) {
            String reason = status.statusDesc();
            if (StrUtil.isNotBlank(reason)) {
                updateWrapper.set(SmsPhoneRecord::getFailReason, reason);
            }
        } else if (sendStatus != null && sendStatus == 2) {
            // 成功时清空失败原因
            updateWrapper.set(SmsPhoneRecord::getFailReason, null);
        }

        // 更新费用详情
        SmsChannelStrategy.SmsReportResult.PriceDetail priceDetail = status.priceDetail();
        if (priceDetail != null) {
            // 使用 BigDecimal 计算外部费用，避免精度丢失
            BigDecimal outUnitPrice = user.getSmsUnitPrice() != null ? BigDecimal.valueOf(user.getSmsUnitPrice()) : BigDecimal.ZERO;
            BigDecimal chargeCount = priceDetail.chargeCount() != null ? BigDecimal.valueOf(priceDetail.chargeCount()) : BigDecimal.ZERO;
            BigDecimal outPayAmount = outUnitPrice.multiply(chargeCount);

            updateWrapper.set(SmsPhoneRecord::getPayAmount, priceDetail.payAmount()).set(SmsPhoneRecord::getCurrency, priceDetail.currency()).set(SmsPhoneRecord::getChargeCount, priceDetail.chargeCount()).set(SmsPhoneRecord::getUnitPrice, priceDetail.unitPrice()).set(SmsPhoneRecord::getQuoteExchange, priceDetail.quoteExchange()).set(SmsPhoneRecord::getSettlePay, priceDetail.settlePay()).set(SmsPhoneRecord::getSettleCurrency, priceDetail.settleCurrency()).set(SmsPhoneRecord::getMePayAmount, priceDetail.settlePay()).set(SmsPhoneRecord::getOutUnitPrice, user.getSmsUnitPrice()).set(SmsPhoneRecord::getOutPayAmount, outPayAmount.doubleValue()).set(SmsPhoneRecord::getSettleUnitPrice, priceDetail.settleUnitPrice());
        }

        smsPhoneRecordMapper.update(null, updateWrapper);
    }

    /**
     * 检查并更新订单状态
     *
     * @param orderNo 订单编号
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkAndUpdateOrderStatus(String orderNo) {
        // 查询订单下所有记录的状态
        LambdaQueryWrapper<SmsPhoneRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SmsPhoneRecord::getOrderNo, orderNo);

        List<SmsPhoneRecord> allRecords = smsPhoneRecordMapper.selectList(wrapper);

        if (CollUtil.isEmpty(allRecords)) {
            return;
        }

        // 统计各种状态的记录数
        Map<Integer, Long> statusCount = allRecords.stream().collect(Collectors.groupingBy(SmsPhoneRecord::getSendStatus, Collectors.counting()));

        long successCount = statusCount.getOrDefault(2, 0L);  // 发送成功
        long failCount = statusCount.getOrDefault(-1, 0L);   // 发送失败
        long pendingCount = statusCount.getOrDefault(0, 0L) + statusCount.getOrDefault(1, 0L); // 未发送 + 发送中

        // 如果所有记录都已完成（成功或失败），更新订单状态
        if (pendingCount == 0) {
            LambdaQueryWrapper<SmsOrder> orderWrapper = new LambdaQueryWrapper<>();
            orderWrapper.eq(SmsOrder::getOrderNo, orderNo);
            SmsOrder order = smsOrderService.getOne(orderWrapper);
            if (order != null) {
                Integer oldStatus = order.getStatus();
                order.setStatus(OrderStatusEnum.COMPLETED.getValue());
                order.setSuccessCount((int) successCount);
                order.setFailCount((int) failCount);
                order.setReportCount((int) (successCount + failCount));
                order.setPaidCount((int) successCount);
                smsOrderService.updateById(order);

                log.info("订单 {} 所有短信发送完成，成功: {}, 失败: {}", orderNo, successCount, failCount);

                // 如果订单从非完成状态变为完成状态，生成流水记录
                if (!OrderStatusEnum.COMPLETED.getValue().equals(oldStatus)) {
                    createTransactionRecord(order, allRecords);
                }
            }
        }
    }

    /**
     * 创建交易流水记录
     *
     * @param order        订单信息
     * @param phoneRecords 手机号发送记录列表
     */
    private void createTransactionRecord(SmsOrder order, List<SmsPhoneRecord> phoneRecords) {
        try {
            // 计算总支出金额（只统计成功的记录）
            List<SmsPhoneRecord> successRecords = phoneRecords.stream().filter(record -> record.getSendStatus() != null && record.getSendStatus() == 2).toList();


            // 如果没有支出，不生成流水
            if (successRecords.size() <= 0) {
                log.debug("订单 {} 没有产生费用，不生成流水记录", order.getOrderNo());
                return;
            }

            // 获取用户ID（订单的创建人）
            Long userId = order.getCreateBy();
            if (userId == null) {
                log.warn("订单 {} 没有创建人信息，无法生成流水记录", order.getOrderNo());
                return;
            }

            // 查询用户当前余额（使用缓存）
            SysUser user = userService.getUserById(userId);
            if (user == null) {
                log.warn("订单 {} 对应的用户不存在，userId: {}", order.getOrderNo(), userId);
                return;
            }

            // 获取当前余额（Double类型）
            Double currentBalance = user.getPrice() != null ? user.getPrice() : 0.0;

            // 计算总费用：单价 × 成功发送数量（使用BigDecimal避免精度丢失）
            BigDecimal unitPrice = user.getSmsUnitPrice() != null ? BigDecimal.valueOf(user.getSmsUnitPrice()) : BigDecimal.ZERO;
            BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(successRecords.size()));
            double amountDouble = totalAmount.doubleValue();

            // 记录余额信息（允许扣成负数）
            if (currentBalance < amountDouble) {
                log.warn("订单 {} 用户余额不足，将扣成负数，当前余额: {}, 需要扣除: {}, 扣后余额: {}", order.getOrderNo(), currentBalance, amountDouble, currentBalance - amountDouble);
            }

            // 计算新余额
            double newBalance = currentBalance - amountDouble;

            // 更新用户余额
            SysUser updateUser = new SysUser();
            updateUser.setId(userId);
            updateUser.setPrice(newBalance);
            userMapper.updateById(updateUser);

            log.info("用户 {} 余额更新成功，原余额: {}, 扣除: {}, 新余额: {}", userId, currentBalance, amountDouble, newBalance);

            // 生成流水号：TXN + 时间戳 + 随机6位
            String transNo = "TXN" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + IdUtil.fastSimpleUUID().substring(0, 6).toUpperCase();

            // 创建流水记录
            UserTransaction transaction = new UserTransaction();
            transaction.setUserId(userId);
            transaction.setTransNo(transNo);
            transaction.setTransType(2); // 2=支出
            transaction.setBizType("营销短信发送");
            transaction.setAmount(totalAmount);
            transaction.setPreBalance(BigDecimal.valueOf(currentBalance)); // 原金额
            transaction.setBalance(BigDecimal.valueOf(newBalance)); // 使用扣减后的新余额
            transaction.setRelatedOrderNo(order.getOrderNo());
            transaction.setStatus(1); // 1=成功
            transaction.setRemark(String.format("订单%s短信发送费用，成功%d条，失败%d条", order.getOrderNo(), order.getSuccessCount(), order.getFailCount()));

            userTransactionService.save(transaction);
            log.info("生成流水记录成功，流水号: {}, 订单号: {}, 金额: {}, 余额: {}", transNo, order.getOrderNo(), totalAmount, newBalance);
        } catch (Exception e) {
            log.error("生成流水记录失败，订单号: {}", order.getOrderNo(), e);
            // 不抛出异常，避免影响订单状态更新
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateFailedRecords(String orderNo, String channelCode, String failReason) {
        // 直接通过 UPDATE 语句批量更新，避免全量查询到内存
        LambdaUpdateWrapper<SmsPhoneRecord> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(SmsPhoneRecord::getOrderNo, orderNo)
                .eq(SmsPhoneRecord::getSendStatus, 0) // 只更新待发送的记录
                .set(SmsPhoneRecord::getChannel, channelCode)
                .set(SmsPhoneRecord::getSendStatus, -1) // 发送失败
                .set(SmsPhoneRecord::getFailReason, failReason)
                .set(SmsPhoneRecord::getSendTime, LocalDateTime.now());

        int updatedCount = smsPhoneRecordMapper.update(null, updateWrapper);

        if (updatedCount == 0) {
            log.warn("未找到需要更新的待发送记录，订单编号: {}", orderNo);
            return;
        }

        log.info("批量更新发送失败记录成功，订单编号: {}, 渠道: {}, 更新记录数: {}, 失败原因: {}", orderNo, channelCode, updatedCount, failReason);
    }

}
