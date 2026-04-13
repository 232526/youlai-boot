package com.youlai.boot.market.order.strategy;

import java.math.BigDecimal;
import java.util.List;

/**
 * 短信发送渠道策略接口
 * <p>
 * 定义统一的短信发送接口，支持多渠道切换
 */
public interface SmsChannelStrategy {

    /**
     * 获取渠道标识
     *
     * @return 渠道标识
     */
    String getChannelCode();

    /**
     * 获取渠道名称
     *
     * @return 渠道名称
     */
    String getChannelName();

    /**
     * 发送短信
     *
     * @param phoneNumbers 接收号码列表
     * @param senderId     发送号码
     * @param content      短信内容
     * @return 发送结果（平台返回的消息ID或其他标识）
     */
    SmsSendResult sendSms(List<String> phoneNumbers, String senderId, String content);

    /**
     * 查询短信发送状态报告
     *
     * @param msgIds 消息ID列表
     * @return 状态报告结果
     */
    SmsReportResult queryReport(List<String> msgIds);

    /**
     * 查询账户余额
     *
     * @return 余额结果
     */
    BalanceResult queryBalance();

    /**
     * 短信发送结果
     */
    record SmsSendResult(
        boolean success,
        String message,
        List<String> msgIds,
        String failReason
    ) {
        public SmsSendResult(boolean success, String message, List<String> msgIds) {
            this(success, message, msgIds, null);
        }
    }

    /**
     * 短信状态报告结果
     */
    record SmsReportResult(
        boolean success,
        String message,
        List<SmsStatus> statusList
    ) {
        /**
         * 短信状态
         */
        public record SmsStatus(
            String msgId,
            String phoneNumber,
            Integer status,
            String statusDesc,
            String receiveTime,
            PriceDetail priceDetail
        ) {
        }

        /**
         * 费用详情
         */
        public record PriceDetail(
            BigDecimal payAmount,
            String currency,
            Integer chargeCount,
            BigDecimal unitPrice,
            BigDecimal quoteExchange,
            BigDecimal settlePay,
            String settleCurrency,
            BigDecimal settleUnitPrice
        ) {
        }
    }

    /**
     * 账户余额查询结果
     */
    record BalanceResult(
        boolean success,
        String message,
        BigDecimal balance,
        BigDecimal gift,
        BigDecimal credit,
        String currency
    ) {
    }
}
