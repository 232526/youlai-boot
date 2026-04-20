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
        List<SmsPhoneRecord> recordsToUpdate = new ArrayList<>(updateCount);
        for (int i = 0; i < updateCount; i++) {
            SmsPhoneRecord record = new SmsPhoneRecord();
            record.setRecordId((Long) recordIds.get(i));
            record.setMsgId(msgIds.get(i));
            record.setChannel(channelCode);
            record.setSendStatus(1); // 发送中（等待状态报告）
            record.setSendTime(now);
            recordsToUpdate.add(record);
        }
        if (!recordsToUpdate.isEmpty()) {
            this.updateBatchById(recordsToUpdate);
        }

        log.info("保存发送结果成功，订单编号: {}, 更新记录数: {}", orderNo, updateCount);
    }

    /**
     * IN查询分批大小，避免SQL过长
     */
    private static final int IN_BATCH_SIZE = 500;

    @Override
    public void updateReportResult(SmsChannelStrategy.SmsReportResult reportResult) {
        if (reportResult == null || !reportResult.success() || CollUtil.isEmpty(reportResult.statusList())) {
            log.warn("状态报告结果为空或失败");
            return;
        }

        List<SmsChannelStrategy.SmsReportResult.SmsStatus> statusList = reportResult.statusList();

        // 第一步：收集所有有效且去重的 msgId
        List<String> validMsgIds = statusList.stream()
            .map(SmsChannelStrategy.SmsReportResult.SmsStatus::msgId)
            .filter(StrUtil::isNotBlank)
            .distinct()
            .collect(Collectors.toList());

        if (CollUtil.isEmpty(validMsgIds)) {
            return;
        }

        // 第二步：分批查询记录（避免IN子句过大），仅查必要字段
        Map<String, SmsPhoneRecord> recordMap = new HashMap<>(validMsgIds.size());
        for (int i = 0; i < validMsgIds.size(); i += IN_BATCH_SIZE) {
            int end = Math.min(i + IN_BATCH_SIZE, validMsgIds.size());
            List<String> batchIds = validMsgIds.subList(i, end);

            LambdaQueryWrapper<SmsPhoneRecord> batchWrapper = new LambdaQueryWrapper<>();
            batchWrapper.select(
                    SmsPhoneRecord::getRecordId,
                    SmsPhoneRecord::getMsgId,
                    SmsPhoneRecord::getCreateBy
                )
                .in(SmsPhoneRecord::getMsgId, batchIds);
            List<SmsPhoneRecord> batchRecords = smsPhoneRecordMapper.selectList(batchWrapper);
            for (SmsPhoneRecord r : batchRecords) {
                recordMap.put(r.getMsgId(), r);
            }
        }

        // 第三步：预加载所有涉及的用户（避免循环中逐个查询）
        Set<Long> userIds = recordMap.values().stream()
            .map(SmsPhoneRecord::getCreateBy)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, SysUser> userCache = new HashMap<>(userIds.size());
        for (Long userId : userIds) {
            SysUser user = userService.getUserById(userId);
            if (user != null) {
                userCache.put(userId, user);
            }
        }

        // 第四步：分拣记录——跳过仍在发送中的、分拣直接更新 vs 需要反转
        List<SmsPhoneRecord> recordsToUpdate = new ArrayList<>();
        List<SmsPhoneRecord> failedRecords = new ArrayList<>();
        Map<String, SmsChannelStrategy.SmsReportResult.SmsStatus> statusMap = new HashMap<>();
        int skippedCount = 0;

        for (SmsChannelStrategy.SmsReportResult.SmsStatus status : statusList) {
            if (StrUtil.isBlank(status.msgId())) continue;

            SmsPhoneRecord record = recordMap.get(status.msgId());
            if (record == null) {
                log.warn("未找到对应的短信记录，msgId: {}", status.msgId());
                continue;
            }

            Integer sendStatus = convertToSendStatus(status.status());

            // 跳过仍在发送中的记录（状态未变化，无需更新）
            if (sendStatus == null || sendStatus == 1) {
                skippedCount++;
                continue;
            }

            SysUser user = userCache.get(record.getCreateBy());
            if (user == null) {
                log.warn("未找到用户信息，userId: {}, msgId: {}", record.getCreateBy(), status.msgId());
                continue;
            }

            Integer flipRate = user.getFlipRate();

            if (sendStatus == -1 && flipRate != null && flipRate > 0) {
                // 上游返回失败且用户配置了反转率，收集起来后续处理
                failedRecords.add(record);
                statusMap.put(record.getMsgId(), status);
            } else {
                recordsToUpdate.add(buildUpdateEntity(record, status, sendStatus, user));
            }
        }

        if (skippedCount > 0) {
            log.debug("跳过 {} 条仍在发送中的记录（状态未变化）", skippedCount);
        }

        // 第五步：分批更新正常记录（避免单次事务过大）
        if (!recordsToUpdate.isEmpty()) {
            batchUpdate(recordsToUpdate);
        }

        // 第六步：处理失败记录的反转逻辑
        int flippedCount = 0;
        if (!failedRecords.isEmpty()) {
            flippedCount = applyFlipRate(failedRecords, statusMap);
            log.info("应用反转率，共处理 {} 条失败记录，其中 {} 条反转为成功", failedRecords.size(), flippedCount);
        }

        log.info("更新状态报告成功，实际更新 {} 条记录，跳过 {} 条未变化记录", recordsToUpdate.size() + flippedCount, skippedCount);
    }

    /**
     * 分批执行批量更新，每批 UPDATE_BATCH_SIZE 条，避免单次事务过大
     */
    private static final int UPDATE_BATCH_SIZE = 500;

    private void batchUpdate(List<SmsPhoneRecord> records) {
        if (records.size() <= UPDATE_BATCH_SIZE) {
            this.updateBatchById(records);
        } else {
            for (int i = 0; i < records.size(); i += UPDATE_BATCH_SIZE) {
                int end = Math.min(i + UPDATE_BATCH_SIZE, records.size());
                this.updateBatchById(records.subList(i, end));
            }
        }
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

        List<SmsPhoneRecord> allUpdates = new ArrayList<>();
        int totalFlippedCount = 0;

        // 遇历每个用户的失败记录
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
                    allUpdates.add(buildUpdateEntity(record, status, -1, user));
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
                allUpdates.add(buildFlipUpdateEntity(record, status, user));
            }
            totalFlippedCount += flipCount;

            // 剩余的保持失败
            for (int i = flipCount; i < totalCount; i++) {
                SmsPhoneRecord record = userFailedRecords.get(i);
                SmsChannelStrategy.SmsReportResult.SmsStatus status = statusMap.get(record.getMsgId());
                allUpdates.add(buildUpdateEntity(record, status, -1, user));
            }
        }

        // 批量更新全部反转和失败记录
        if (!allUpdates.isEmpty()) {
            this.updateBatchById(allUpdates);
        }

        return totalFlippedCount;
    }

    /**
     * 构建反转成功记录的更新实体
     *
     * @param record 短信记录
     * @param status 状态报告
     * @param user   用户信息
     * @return 更新实体
     */
    private SmsPhoneRecord buildFlipUpdateEntity(SmsPhoneRecord record, SmsChannelStrategy.SmsReportResult.SmsStatus status, SysUser user) {
        SmsPhoneRecord entity = new SmsPhoneRecord();
        entity.setRecordId(record.getRecordId());
        entity.setReceiveTime(parseReceiveTime(status.receiveTime()));
        entity.setSendStatus(2); // 反转为成功
        entity.setFlipFailReason(status.statusDesc());

        SmsChannelStrategy.SmsReportResult.PriceDetail priceDetail = status.priceDetail();
        if (priceDetail != null) {
            BigDecimal outUnitPrice = user.getSmsUnitPrice() != null ? BigDecimal.valueOf(user.getSmsUnitPrice()) : BigDecimal.ZERO;
            BigDecimal chargeCount = priceDetail.chargeCount() != null ? BigDecimal.valueOf(priceDetail.chargeCount()) : BigDecimal.valueOf(1);
            BigDecimal outPayAmount = outUnitPrice.multiply(chargeCount);

            entity.setPayAmount(priceDetail.payAmount());
            entity.setCurrency(priceDetail.currency());
            entity.setChargeCount(chargeCount.intValue());
            entity.setUnitPrice(priceDetail.unitPrice());
            entity.setQuoteExchange(priceDetail.quoteExchange());
            entity.setSettlePay(priceDetail.settlePay());
            entity.setSettleCurrency(priceDetail.settleCurrency());
            entity.setSettleUnitPrice(priceDetail.settleUnitPrice());
            entity.setMePayAmount(priceDetail.settlePay());
            entity.setOutUnitPrice(user.getSmsUnitPrice() != null ? BigDecimal.valueOf(user.getSmsUnitPrice()) : null);
            entity.setIsFlip(1);
            entity.setOutPayAmount(outPayAmount);
        }

        return entity;
    }

    /**
     * 构建常规记录的更新实体
     *
     * @param record     短信记录
     * @param status     状态报告
     * @param sendStatus 发送状态：0=未发送，1=发送中，2=发送成功，-1=发送失败
     * @param user       用户信息
     * @return 更新实体
     */
    private SmsPhoneRecord buildUpdateEntity(SmsPhoneRecord record, SmsChannelStrategy.SmsReportResult.SmsStatus status, Integer sendStatus, SysUser user) {
        SmsPhoneRecord entity = new SmsPhoneRecord();
        entity.setRecordId(record.getRecordId());
        entity.setReceiveTime(parseReceiveTime(status.receiveTime()));

        // 更新发送状态
        if (sendStatus != null) {
            entity.setSendStatus(sendStatus);
        }

        // 更新失败原因（只有失败时才设置）
        if (sendStatus != null && sendStatus == -1) {
            String reason = status.statusDesc();
            if (StrUtil.isNotBlank(reason)) {
                entity.setFailReason(reason);
            }
        } else if (sendStatus != null && sendStatus == 2) {
            // 成功时清空失败原因（failReason 已配置 ALWAYS 更新策略，支持设为 null）
            entity.setFailReason(null);
        }

        // 更新费用详情
        SmsChannelStrategy.SmsReportResult.PriceDetail priceDetail = status.priceDetail();
        if (priceDetail != null) {
            BigDecimal outUnitPrice = user.getSmsUnitPrice() != null ? BigDecimal.valueOf(user.getSmsUnitPrice()) : BigDecimal.ZERO;
            BigDecimal chargeCount = priceDetail.chargeCount() != null ? BigDecimal.valueOf(priceDetail.chargeCount()) : BigDecimal.ZERO;
            BigDecimal outPayAmount = outUnitPrice.multiply(chargeCount);

            entity.setPayAmount(priceDetail.payAmount());
            entity.setCurrency(priceDetail.currency());
            entity.setChargeCount(priceDetail.chargeCount());
            entity.setUnitPrice(priceDetail.unitPrice());
            entity.setQuoteExchange(priceDetail.quoteExchange());
            entity.setSettlePay(priceDetail.settlePay());
            entity.setSettleCurrency(priceDetail.settleCurrency());
            entity.setSettleUnitPrice(priceDetail.settleUnitPrice());
            entity.setMePayAmount(priceDetail.settlePay());
            entity.setOutUnitPrice(user.getSmsUnitPrice() != null ? BigDecimal.valueOf(user.getSmsUnitPrice()) : null);
            entity.setOutPayAmount(outPayAmount);
        }

        return entity;
    }

    /**
     * 检查并更新订单状态
     *
     * @param orderNo 订单编号
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkAndUpdateOrderStatus(String orderNo) {
        // 使用聚合查询统计各状态数量，避免加载大量数据到内存
        List<Map<String, Object>> statusCountList = smsPhoneRecordMapper.countByOrderNoGroupByStatus(orderNo);

        if (CollUtil.isEmpty(statusCountList)) {
            return;
        }

        // 解析统计结果
        Map<Integer, Long> statusCount = statusCountList.stream()
            .collect(Collectors.toMap(
                m -> (Integer) m.get("send_status"),
                m -> ((Number) m.get("count")).longValue()
            ));

        long successCount = statusCount.getOrDefault(2, 0L);  // 发送成功
        long failCount = statusCount.getOrDefault(-1, 0L);   // 发送失败
        long pendingCount = statusCount.getOrDefault(0, 0L) + statusCount.getOrDefault(1, 0L); // 未发送 + 发送中

        // 如果所有记录都已完成（成功或失败），更新订单状态

        LambdaQueryWrapper<SmsOrder> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.eq(SmsOrder::getOrderNo, orderNo);
        SmsOrder order = smsOrderService.getOne(orderWrapper);
        if (order != null) {
            Integer oldStatus = order.getStatus();
            order.setSuccessCount((int) successCount);
            order.setFailCount((int) failCount);
            order.setReportCount((int) (successCount + failCount));
            order.setPaidCount((int) successCount);
            // 如果订单从非完成状态变为完成状态，生成流水记录
            if (pendingCount == 0) {
                order.setStatus(OrderStatusEnum.COMPLETED.getValue());
                smsOrderService.updateById(order);
                log.info("订单 {} 所有短信发送完成，成功: {}, 失败: {}", orderNo, successCount, failCount);
                if (!OrderStatusEnum.COMPLETED.getValue().equals(oldStatus)) {
                    createTransactionRecord(order, (int) successCount);
                }
            } else {
                smsOrderService.updateById(order);
            }
        }

    }

    /**
     * 创建交易流水记录
     *
     * @param order        订单信息
     * @param successCount 成功发送数量
     */
    private void createTransactionRecord(SmsOrder order, int successCount) {
        try {
            // 如果没有成功记录，不生成流水
            if (successCount <= 0) {
                log.debug("订单 {} 没有产生费用，不生成流水记录", order.getOrderNo());
                return;
            }

            // 获取用户ID（订单的创建人）
            Long userId = order.getCreateBy();
            if (userId == null) {
                log.warn("订单 {} 没有创建人信息，无法生成流水记录", order.getOrderNo());
                return;
            }

            // 查询用户当前余额（不走缓存，确保余额实时性）
            SysUser user = userService.getUserByIdNoCache(userId);
            if (user == null) {
                log.warn("订单 {} 对应的用户不存在，userId: {}", order.getOrderNo(), userId);
                return;
            }

            // 获取当前余额（Double类型）
            Double currentBalance = user.getPrice() != null ? user.getPrice() : 0.0;

            // 计算总费用：单价 × 成功发送数量（使用BigDecimal避免精度丢失）
            BigDecimal unitPrice = user.getSmsUnitPrice() != null ? BigDecimal.valueOf(user.getSmsUnitPrice()) : BigDecimal.ZERO;
            BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(successCount));
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
