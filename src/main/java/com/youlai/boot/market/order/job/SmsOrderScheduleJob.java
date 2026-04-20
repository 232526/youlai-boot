package com.youlai.boot.market.order.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

        // 1. 获取订单关联的手机号和短信内容
        List<SmsPhoneRecord> phoneRecords = smsOrderService.getPhoneRecordsByOrderId(order.getOrderNo());
        List<SmsMessageContent> messageContents = smsOrderService.getMessageContentsByOrderId(order.getOrderNo());

        if (CollectionUtils.isEmpty(phoneRecords) || CollectionUtils.isEmpty(messageContents)) {
            log.warn("订单没有关联的手机号或短信内容，订单ID: {}", order.getId());
            return;
        }

        // 2. 提取手机号列表（去重）
        List<String> phoneNumbers = phoneRecords.stream().map(SmsPhoneRecord::getPhoneNumber).distinct().collect(Collectors.toList());

        // 3. 获取短信内容（取第一条内容，或者根据业务逻辑合并多条内容）
        String content = messageContents.get(0).getContent();

        // 4. 获取发送号码（可以从国家配置或订单中获取）
        String senderId = getSenderId(order);

        // 5. 获取短信渠道策略
        String channelCode = order.getChannel() != null ? order.getChannel() : "ONBUKA"; // 默认使用 ONBUKA
        SmsChannelStrategy strategy = smsChannelContext.getStrategy(channelCode);

        log.info("使用短信渠道: {} 发送短信，订单ID: {}, 手机号总数: {}", strategy.getChannelName(), order.getId(), phoneNumbers.size());

        // 6. 分批发送短信，每批最多 SEND_BATCH_SIZE 条
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < phoneNumbers.size(); i += SEND_BATCH_SIZE) {
            batches.add(phoneNumbers.subList(i, Math.min(i + SEND_BATCH_SIZE, phoneNumbers.size())));
        }

        boolean allSuccess = true;
        List<String> allMsgIds = new ArrayList<>();
        String lastFailReason = null;

        for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
            List<String> batchPhones = batches.get(batchIndex);
            log.info("订单 {} 发送第 {}/{} 批，本批数量: {}", order.getOrderNo(), batchIndex + 1, batches.size(), batchPhones.size());

            SmsChannelStrategy.SmsSendResult sendResult = strategy.sendSms(batchPhones, senderId, content);

            if (sendResult.success()) {
                if (sendResult.msgIds() != null) {
                    allMsgIds.addAll(sendResult.msgIds());
                }
                log.info("订单 {} 第 {}/{} 批发送成功，消息IDs: {}", order.getOrderNo(), batchIndex + 1, batches.size(), sendResult.msgIds());
            } else {
                allSuccess = false;
                lastFailReason = sendResult.failReason() != null ? sendResult.failReason() : sendResult.message();
                log.error("订单 {} 第 {}/{} 批发送失败，错误: {}", order.getOrderNo(), batchIndex + 1, batches.size(), lastFailReason);
                break; // 某批失败后停止发送后续批次
            }
        }

        // 7. 更新订单状态和发送结果
        if (allSuccess) {
            // 更新订单状态为发送中
            order.setStatus(OrderStatusEnum.SENDING.getValue());
            boolean updated = smsOrderService.updateById(order);

            if (updated) {
                log.info("订单状态已更新为发送中，订单ID: {}, 消息IDs数量: {}", order.getOrderNo(), allMsgIds.size());

                // 保存消息ID到发送记录表
                SmsChannelStrategy.SmsSendResult aggregatedResult = new SmsChannelStrategy.SmsSendResult(true, "success", allMsgIds);
                smsPhoneRecordService.saveSendResult(order.getOrderNo(), aggregatedResult, channelCode);
            } else {
                log.warn("订单状态更新失败，订单ID: {}", order.getOrderNo());
            }
        } else {
            log.error("短信发送失败，订单ID: {}, 错误信息: {}", order.getOrderNo(), lastFailReason);
            // 更新订单状态为发送失败，并保存失败原因
            order.setStatus(OrderStatusEnum.FAILED.getValue());
            order.setFailMsg(lastFailReason);
            smsOrderService.updateById(order);

            // 更新该订单下所有手机号记录的状态为发送失败
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
            // 查询 sms_phone_record 表中所有发送中的记录（sendStatus = -1）
            LambdaQueryWrapper<SmsPhoneRecord> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SmsPhoneRecord::getSendStatus, 1)  // 发送中状态
                .isNotNull(SmsPhoneRecord::getMsgId);       // 必须有msgId才能查询

            List<SmsPhoneRecord> sendingRecords = smsPhoneRecordService.list(wrapper);

            if (sendingRecords == null || sendingRecords.isEmpty()) {
                log.debug("当前没有发送中的手机号记录");
                return;
            }

            log.info("发现 {} 个发送中的手机号记录，开始查询状态报告", sendingRecords.size());

            // 按订单编号和渠道分组，批量查询状态报告
            var recordsByOrderAndChannel = sendingRecords.stream().collect(Collectors.groupingBy(record -> record.getOrderNo() + "_" + (record.getChannel() != null ? record.getChannel() : "ONBUKA")));

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

            log.info("状态报告查询任务执行完成，共处理 {} 条记录", sendingRecords.size());

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
