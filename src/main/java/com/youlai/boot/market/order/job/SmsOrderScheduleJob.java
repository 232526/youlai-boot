package com.youlai.boot.market.order.job;

import com.youlai.boot.market.order.enums.OrderStatusEnum;
import com.youlai.boot.market.order.model.entity.SmsMessageContent;
import com.youlai.boot.market.order.model.entity.SmsOrder;
import com.youlai.boot.market.order.model.entity.SmsPhoneRecord;
import com.youlai.boot.market.order.service.SmsOrderService;
import com.youlai.boot.market.order.service.SmsPhoneRecordService;
import com.youlai.boot.market.order.strategy.SmsChannelContext;
import com.youlai.boot.market.order.strategy.SmsChannelStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

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
     * 定时检查并执行待发送的订单
     * <p>
     * 每分钟执行一次，查询状态为待发送且预约时间已到或已过的订单
     */
    @Scheduled(cron = "0 * * * * ?")
    public void executePendingOrders() {
        log.debug("开始执行订单定时任务...");

        try {
            // 查询当前需要执行的待发送订单
            java.util.List<com.youlai.boot.market.order.model.entity.SmsOrder> pendingOrders = smsOrderService.getPendingOrdersToExecute();

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
    private void processOrder(com.youlai.boot.market.order.model.entity.SmsOrder order) {
        log.info("开始处理订单，订单ID: {}, 订单编号: {}, 预约时间: {}, 渠道: {}",
            order.getId(), order.getOrderNo(), order.getScheduledTime(), order.getChannel());

        // 1. 获取订单关联的手机号和短信内容
        List<SmsPhoneRecord> phoneRecords = smsOrderService.getPhoneRecordsByOrderId(order.getId());
        List<SmsMessageContent> messageContents = smsOrderService.getMessageContentsByOrderId(order.getId());

        if (CollectionUtils.isEmpty(phoneRecords) || CollectionUtils.isEmpty(messageContents)) {
            log.warn("订单没有关联的手机号或短信内容，订单ID: {}", order.getId());
            return;
        }

        // 2. 提取手机号列表（去重）
        List<String> phoneNumbers = phoneRecords.stream()
            .map(SmsPhoneRecord::getPhoneNumber)
            .distinct()
            .collect(Collectors.toList());

        // 3. 获取短信内容（取第一条内容，或者根据业务逻辑合并多条内容）
        String content = messageContents.get(0).getContent();

        // 4. 获取发送号码（可以从国家配置或订单中获取）
        String senderId = getSenderId(order);

        // 5. 获取短信渠道策略
        String channelCode = order.getChannel() != null ? order.getChannel() : "ONBUKA"; // 默认使用 ONBUKA
        SmsChannelStrategy strategy = smsChannelContext.getStrategy(channelCode);

        log.info("使用短信渠道: {} 发送短信，订单ID: {}", strategy.getChannelName(), order.getId());

        // 6. 调用短信发送接口
        SmsChannelStrategy.SmsSendResult sendResult = strategy.sendSms(phoneNumbers, senderId, content);

        // 7. 更新订单状态和发送结果
        if (sendResult.success()) {
            // 更新订单状态为发送中
            order.setStatus(OrderStatusEnum.SENDING.getValue());
            boolean updated = smsOrderService.updateById(order);

            if (updated) {
                log.info("订单状态已更新为发送中，订单ID: {}, 消息IDs: {}", order.getId(), sendResult.msgIds());

                // 保存消息ID到发送记录表
                smsPhoneRecordService.saveSendResult(order.getId(), sendResult, channelCode);
            } else {
                log.warn("订单状态更新失败，订单ID: {}", order.getId());
            }
        } else {
            log.error("短信发送失败，订单ID: {}, 错误信息: {}", order.getId(), sendResult.message());
            // 更新订单状态为发送失败
            order.setStatus(OrderStatusEnum.FAILED.getValue());
            smsOrderService.updateById(order);
        }
    }

    /**
     * 定时查询并更新短信状态报告
     * <p>
     * 每5分钟执行一次，查询发送中的订单的状态报告
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void queryAndUpdateReports() {
        log.debug("开始执行状态报告查询任务...");

        try {
            // 查询所有发送中的订单
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SmsOrder> wrapper = 
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            wrapper.eq(SmsOrder::getStatus, OrderStatusEnum.SENDING.getValue());
            
            List<SmsOrder> sendingOrders = smsOrderService.list(wrapper);
            
            if (sendingOrders == null || sendingOrders.isEmpty()) {
                log.debug("当前没有发送中的订单");
                return;
            }

            log.info("发现 {} 个发送中的订单，开始查询状态报告", sendingOrders.size());

            // 遍历处理每个订单
            for (SmsOrder order : sendingOrders) {
                try {
                    String channelCode = order.getChannel() != null ? order.getChannel() : "ONBUKA";
                    smsPhoneRecordService.queryAndUpdateReport(order.getId(), channelCode);
                } catch (Exception e) {
                    log.error("查询订单状态报告失败，订单ID: {}, 订单编号: {}", 
                            order.getId(), order.getOrderNo(), e);
                }
            }

            log.info("状态报告查询任务执行完成，共处理 {} 个订单", sendingOrders.size());

        } catch (Exception e) {
            log.error("状态报告查询任务执行异常", e);
        }
    }

    /**
     * 获取发送号码
     *
     * @param order 订单实体
     * @return 发送号码
     */
    private String getSenderId(com.youlai.boot.market.order.model.entity.SmsOrder order) {
        // TODO: 根据国家ID或订单配置获取发送号码
        // 这里可以根据实际业务逻辑实现
        return "15013893072"; // 示例：返回默认的发送号码
    }
}
