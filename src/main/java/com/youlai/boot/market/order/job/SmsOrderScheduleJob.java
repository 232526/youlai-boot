package com.youlai.boot.market.order.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.youlai.boot.market.order.enums.OrderStatusEnum;
import com.youlai.boot.market.order.model.entity.SmsMessageContent;
import com.youlai.boot.market.order.model.entity.SmsOrder;
import com.youlai.boot.market.order.model.entity.SmsPhoneRecord;
import com.youlai.boot.market.order.service.SmsOrderService;
import com.youlai.boot.market.order.service.SmsPhoneRecordService;
import com.youlai.boot.market.order.strategy.SmsChannelContext;
import com.youlai.boot.market.order.strategy.SmsChannelStrategy;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 短信订单定时任务
 * <p>
 * 每分钟检查是否有需要执行的订单任务
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SmsOrderScheduleJob {

    private final SmsOrderService smsOrderService;
    private final SmsPhoneRecordService smsPhoneRecordService;
    private final SmsChannelContext smsChannelContext;

    /**
     * 每批发送的最大手机号数量
     */
    private static final int SEND_BATCH_SIZE = 1000;

    /**
     * 定时检查并执行待发送的订单
     * <p>
     * 每分钟执行一次，查询状态为待发送且预约时间已到或已过的订单
     */
    @Scheduled(cron = "0 * * * * ?")
    public void executePendingOrders() {
        log.debug("开始执行订单定时任务...");

        try {
            // 查询当前需要执行的待发送订单
            List<SmsOrder> pendingOrders = smsOrderService.getPendingOrdersToExecute();

            if (pendingOrders == null || pendingOrders.isEmpty()) {
                log.debug("当前没有需要执行的订单任务");
                return;
            }

            log.info("发现 {} 个待执行的订单任务", pendingOrders.size());

            // 遍历处理每个订单
            for (SmsOrder order : pendingOrders) {
                try {
                    processOrder(order);
                } catch (Exception e) {
                    log.error("处理订单失败，订单ID: {}, 订单编号: {}", order.getId(), order.getOrderNo(), e);
                }
            }

            log.info("订单定时任务执行完成，共处理 {} 个订单", pendingOrders.size());

        } catch (Exception e) {
            log.error("订单定时任务执行异常", e);
        }
    }

    /**
     * 处理单个订单
     * <p>
     * 将订单状态从“待发送”更新为“发送中”，并触发实际的短信发送逻辑
     *
     * @param order 订单实体
     */
    private void processOrder(SmsOrder order) {
        log.info("开始处理订单，订单ID: {}, 订单编号: {}, 预约时间: {}, 渠道: {}", order.getId(), order.getOrderNo(), order.getScheduledTime(), order.getChannel());

        // 1. 获取订单关联的短信内容
        List<SmsMessageContent> messageContents = smsOrderService.getMessageContentsByOrderId(order.getOrderNo());

        if (CollectionUtils.isEmpty(messageContents)) {
            log.warn("订单没有关联的短信内容，订单ID: {}", order.getId());
            return;
        }

        // 2. 获取短信内容（取第一条内容）
        String content = messageContents.get(0).getContent();

        // 3. 获取发送号码
        String senderId = getSenderId(order);

        // 4. 获取短信渠道策略
        String channelCode = order.getChannel() != null ? order.getChannel() : "ONBUKA";
        SmsChannelStrategy strategy = smsChannelContext.getStrategy(channelCode);

        log.info("使用短信渠道: {} 发送短信，订单ID: {}", strategy.getChannelName(), order.getId());

        // 5. 分批从数据库查询待发送的手机号并发送，每批 SEND_BATCH_SIZE 条
        boolean allSuccess = true;
        List<String> allMsgIds = new ArrayList<>();
        String lastFailReason = null;
        int batchIndex = 0;

        while (true) {
            // 每次都查第1页，因为上一批发送成功后 sendStatus 已被更新，不再是待发送状态
            Page<SmsPhoneRecord> page = new Page<>(1, SEND_BATCH_SIZE, false);
            LambdaQueryWrapper<SmsPhoneRecord> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SmsPhoneRecord::getOrderNo, order.getOrderNo())
                .eq(SmsPhoneRecord::getSendStatus, 0)
                .orderByAsc(SmsPhoneRecord::getOrderNo);
            Page<SmsPhoneRecord> recordPage = smsPhoneRecordService.page(page, wrapper);

            List<SmsPhoneRecord> batchRecords = recordPage.getRecords();
            if (CollectionUtils.isEmpty(batchRecords)) {
                break;
            }

            // 提取本批手机号（去重）
            List<String> batchPhones = batchRecords.stream()
                .map(SmsPhoneRecord::getPhoneNumber)
                .distinct()
                .collect(Collectors.toList());

            batchIndex++;
            log.info("订单 {} 发送第 {} 批，本批数量: {}", order.getOrderNo(), batchIndex, batchPhones.size());

            // 调用短信发送接口
            SmsChannelStrategy.SmsSendResult sendResult = strategy.sendSms(batchPhones, senderId, content);

            if (sendResult.success()) {
                if (sendResult.msgIds() != null) {
                    allMsgIds.addAll(sendResult.msgIds());
                }
                // 每批发送成功后立即更新该批手机号的状态
                smsPhoneRecordService.saveSendResult(order.getOrderNo(), sendResult, channelCode);
                log.info("订单 {} 第 {} 批发送成功并更新状态，消息IDs数量: {}", order.getOrderNo(), batchIndex, sendResult.msgIds() != null ? sendResult.msgIds().size() : 0);
            } else {
                allSuccess = false;
                lastFailReason = sendResult.failReason() != null ? sendResult.failReason() : sendResult.message();
                log.error("订单 {} 第 {} 批发送失败，错误: {}", order.getOrderNo(), batchIndex, lastFailReason);
                break;
            }

            // 如果本批不足 SEND_BATCH_SIZE 条，说明已经没有更多记录了
            if (batchRecords.size() < SEND_BATCH_SIZE) {
                break;
            }
        }

        // 6. 更新订单状态和发送结果
        if (allSuccess) {
            order.setStatus(OrderStatusEnum.SENDING.getValue());
            boolean updated = smsOrderService.updateById(order);

            if (updated) {
                log.info("订单状态已更新为发送中，订单ID: {}, 消息IDs总数: {}", order.getOrderNo(), allMsgIds.size());
            } else {
                log.warn("订单状态更新失败，订单ID: {}", order.getOrderNo());
            }
        } else {
            lastFailReason = lastFailReason.substring(0, Math.min(lastFailReason.length(), 255));
            log.error("短信发送失败，订单ID: {}, 错误信息: {}", order.getOrderNo(), lastFailReason);
            order.setStatus(OrderStatusEnum.FAILED.getValue());
            order.setFailMsg(lastFailReason);
            smsOrderService.updateById(order);

            // 更新该订单下剩余未发送的手机号记录状态为发送失败
            smsPhoneRecordService.updateFailedRecords(order.getOrderNo(), channelCode, lastFailReason);
        }
    }

    /**
     * 定时查询并更新短信状态报告
     */
    @PostConstruct
    @Scheduled(fixedDelay = 120000)
    public void queryAndUpdateReports() {
        log.debug("开始执行状态报告查询任务...");

        try {
            int totalProcessed = 0;

            // 分页查询发送中的记录，每页 SEND_BATCH_SIZE 条，避免全量加载导致内存溢出
            while (true) {
                // 每次查第1页，因为处理完的记录 sendStatus 会被更新，不再满足查询条件
                Page<SmsPhoneRecord> page = new Page<>(1, SEND_BATCH_SIZE, false);
                LambdaQueryWrapper<SmsPhoneRecord> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(SmsPhoneRecord::getSendStatus, 1)  // 发送中状态
                    .isNotNull(SmsPhoneRecord::getMsgId)       // 必须有msgId才能查询
                    .orderByAsc(SmsPhoneRecord::getOrderNo);
                Page<SmsPhoneRecord> recordPage = smsPhoneRecordService.page(page, wrapper);

                List<SmsPhoneRecord> batchRecords = recordPage.getRecords();
                if (CollectionUtils.isEmpty(batchRecords)) {
                    if (totalProcessed == 0) {
                        log.debug("当前没有发送中的手机号记录");
                    }
                    break;
                }

                log.info("本批查询到 {} 条发送中的记录，开始查询状态报告", batchRecords.size());

                // 按订单编号和渠道分组，批量查询状态报告
                var recordsByOrderAndChannel = batchRecords.stream().collect(Collectors.groupingBy(record -> record.getOrderNo() + "_" + (record.getChannel() != null ? record.getChannel() : "ONBUKA")));

                // 遍历每个订单+渠道组合
                for (var entry : recordsByOrderAndChannel.entrySet()) {
                    try {
                        List<SmsPhoneRecord> records = entry.getValue();
                        if (records.isEmpty()) {
                            continue;
                        }

                        SmsPhoneRecord firstRecord = records.get(0);
                        String orderNo = firstRecord.getOrderNo();
                        String channelCode = firstRecord.getChannel() != null ? firstRecord.getChannel() : "ONBUKA";

                        // 提取该组的所有msgId
                        List<String> msgIds = records.stream().map(SmsPhoneRecord::getMsgId).filter(msgId -> msgId != null && !msgId.isEmpty()).distinct().collect(Collectors.toList());

                        if (msgIds.isEmpty()) {
                            log.warn("订单 {} 没有有效的msgId", orderNo);
                            continue;
                        }

                        log.info("查询订单 {} 的状态报告，渠道: {}, msgId数量: {}", orderNo, channelCode, msgIds.size());

                        // 调用渠道策略查询状态报告
                        SmsChannelStrategy strategy = smsChannelContext.getStrategy(channelCode);
                        SmsChannelStrategy.SmsReportResult reportResult = strategy.queryReport(msgIds);

                        // 更新状态报告
                        if (reportResult != null && reportResult.success()) {
                            smsPhoneRecordService.updateReportResult(reportResult);

                            // 检查是否所有记录都已完成，更新订单状态
                            smsPhoneRecordService.checkAndUpdateOrderStatus(orderNo);

                            log.info("订单 {} 状态报告更新成功", orderNo);
                        } else {
                            log.warn("查询订单 {} 状态报告失败，错误信息: {}", orderNo, reportResult != null ? reportResult.message() : "未知错误");
                        }
                    } catch (Exception e) {
                        log.error("查询订单状态报告失败，分组key: {}", entry.getKey(), e);
                    }
                }

                totalProcessed += batchRecords.size();

                // 如果本批不足 SEND_BATCH_SIZE 条，说明没有更多记录了
                if (batchRecords.size() < SEND_BATCH_SIZE) {
                    break;
                }
            }

            if (totalProcessed > 0) {
                log.info("状态报告查询任务执行完成，共处理 {} 条记录", totalProcessed);
            }

        } catch (Exception e) {
            log.error("状态报告查询任务执行异常", e);
        }
    }


    /**
     * 定时查询并更新余额
     */
    @PostConstruct
    @Scheduled(fixedDelay = 120000)
    public void queryAndUpdatePrice() {
        log.debug("开始执行余额查询任务...");

        try {
            // 获取所有可用的短信渠道
            List<String> availableChannels = smsChannelContext.getAvailableChannels();

            if (availableChannels == null || availableChannels.isEmpty()) {
                log.debug("当前没有可用的短信渠道");
                return;
            }

            log.info("发现 {} 个短信渠道，开始查询余额", availableChannels.size());

            // 遍历每个渠道查询余额
            for (String channelCode : availableChannels) {
                try {
                    log.info("查询渠道 {} 的账户余额", channelCode);

                    // 调用渠道策略查询余额
                    SmsChannelStrategy.BalanceResult balanceResult = smsChannelContext.queryBalance(channelCode);

                    if (balanceResult != null && balanceResult.success()) {
                        log.info("渠道 {} 余额查询成功 - 余额: {} {}, 消息: {}",
                            channelCode,
                            balanceResult.balance(),
                            balanceResult.currency(),
                            balanceResult.message()
                        );

                    } else {
                        log.warn("渠道 {} 余额查询失败 - 错误信息: {}",
                            channelCode,
                            balanceResult != null ? balanceResult.message() : "未知错误"
                        );
                    }
                } catch (Exception e) {
                    log.error("查询渠道 {} 余额失败", channelCode, e);
                }
            }

            log.info("余额查询任务执行完成，共查询 {} 个渠道", availableChannels.size());

        } catch (Exception e) {
            log.error("余额查询任务执行异常", e);
        }
    }


    /**
     * 获取发送号码
     *
     * @param order 订单实体
     * @return 发送号码
     */
    private String getSenderId(SmsOrder order) {
        // 这里可以根据实际业务逻辑实现
        // todo 默认不传
        return "";
    }
}
