package com.youlai.boot.market.order.strategy.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.onbuka.api.sdk.client.SmsSdkClient;
import com.onbuka.api.sdk.model.ApiData;
import com.onbuka.api.sdk.model.smsdto.ReportDTO;
import com.onbuka.api.sdk.model.smsdto.SendSmsDTO;
import com.youlai.boot.market.order.enums.SmsChannelEnum;
import com.youlai.boot.market.order.strategy.SmsChannelStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Onbuka 短信渠道策略实现
 * <p>
 * https://www.onbuka.com/zh-cn/sms-api4/
 * <p>
 * https://my.onbuka.com/
 * 账号：yuan777
 * 密码：qwe123456
 *
 * @author Ray.Hao
 * @since 2026/04/09
 */
@Component
@Slf4j
public class OnbukaSmsChannelStrategy implements SmsChannelStrategy {

    private static final String APP_KEY = "MzxFjN79";
    private static final String APP_SECRET = "YYXoTBQv";
    private static final String API_URL = "https://api.onbuka.com/v3";
    private static final String APP_ID = "8A5isF3J";

    @Override
    public String getChannelCode() {
        return SmsChannelEnum.ONBUKA.getValue();
    }

    @Override
    public String getChannelName() {
        return SmsChannelEnum.ONBUKA.getLabel();
    }

    @Override
    public SmsSendResult sendSms(List<String> phoneNumbers, String senderId, String content) {
        try {
            ApiData apiData = new ApiData(APP_KEY, APP_SECRET, API_URL);
            SmsSdkClient smsSdkClient = SmsSdkClient.getInstance(apiData);

            SendSmsDTO sendSmsDTO = new SendSmsDTO();
            sendSmsDTO.setAppId(APP_ID);
            // 多个号码用英文逗号分隔
            sendSmsDTO.setNumbers(String.join(",", phoneNumbers));
            sendSmsDTO.setSenderId(senderId);
            sendSmsDTO.setContent(content);

            String result = smsSdkClient.sendSmsV3(sendSmsDTO);
            log.info("Onbuka短信发送结果: {}", result);

            // 这里需要根据实际的返回格式进行解析
            List<String> msgIds = parseMsgIds(result);

            return new SmsSendResult(true, "发送成功", msgIds);

        } catch (Exception e) {
            log.error("Onbuka短信发送失败", e);
            return new SmsSendResult(false, "发送失败: " + e.getMessage(), null);
        }
    }

    @Override
    public SmsReportResult queryReport(List<String> msgIds) {
        try {
            ApiData apiData = new ApiData(APP_KEY, APP_SECRET, API_URL);
            SmsSdkClient smsSdkClient = SmsSdkClient.getInstance(apiData);

            ReportDTO reportDTO = new ReportDTO();
            reportDTO.setAppId(APP_ID);
            // 多个msgId用英文逗号分隔
            reportDTO.setMsgIds(String.join(",", msgIds));

            String reportResult = smsSdkClient.getReport(reportDTO);
            log.info("Onbuka短信状态报告: {}", reportResult);

            // 这里需要根据实际的返回格式进行解析
            List<SmsReportResult.SmsStatus> statusList = parseReportResult(reportResult);

            return new SmsReportResult(true, "查询成功", statusList);

        } catch (Exception e) {
            log.error("Onbuka短信状态报告查询失败", e);
            return new SmsReportResult(false, "查询失败: " + e.getMessage(), null);
        }
    }

    /**
     * 解析发送结果，提取消息ID列表
     *
     * @param result 发送结果JSON字符串
     * @return 消息ID列表
     */
    private List<String> parseMsgIds(String result) {
        List<String> msgIds = new ArrayList<>();

        try {
            JSONObject jsonObject = JSONUtil.parseObj(result);

            // 检查状态是否为成功
            String status = jsonObject.getStr("status");
            if (!"0".equals(status)) {
                log.warn("Onbuka短信发送失败，状态码: {}", status);
                return msgIds;
            }

            // 获取array数组
            JSONArray array = jsonObject.getJSONArray("array");
            if (array != null && !array.isEmpty()) {
                for (int i = 0; i < array.size(); i++) {
                    JSONObject item = array.getJSONObject(i);
                    String msgId = item.getStr("msgId");
                    if (msgId != null && !msgId.isEmpty()) {
                        msgIds.add(msgId);
                    }
                }
            }

            log.info("成功解析{}个消息ID", msgIds.size());
        } catch (Exception e) {
            log.error("解析Onbuka短信发送结果失败", e);
        }

        return msgIds;
    }

    /**
     * 解析状态报告结果
     *
     * @param reportResult 状态报告JSON字符串
     * @return 状态列表
     */
    private List<SmsReportResult.SmsStatus> parseReportResult(String reportResult) {
        List<SmsReportResult.SmsStatus> statusList = new ArrayList<>();

        try {
            JSONObject jsonObject = JSONUtil.parseObj(reportResult);

            // 检查状态是否为成功
            String status = jsonObject.getStr("status");
            if (!"0".equals(status)) {
                log.warn("Onbuka短信状态报告查询失败，状态码: {}", status);
                return statusList;
            }

            // 获取array数组
            JSONArray array = jsonObject.getJSONArray("array");
            if (array != null && !array.isEmpty()) {
                for (int i = 0; i < array.size(); i++) {
                    JSONObject item = array.getJSONObject(i);

                    String msgId = item.getStr("msgId");
                    String phoneNumber = item.getStr("number");
                    String receiveTime = item.getStr("receiveTime");
                    String smsStatus = item.getStr("status");

                    // 转换状态码为整数
                    Integer statusCode = null;
                    String statusDesc = null;
                    if (smsStatus != null) {
                        try {
                            statusCode = Integer.parseInt(smsStatus);
                            // 根据状态码设置描述
                            statusDesc = getStatusDescription(smsStatus);
                        } catch (NumberFormatException e) {
                            log.warn("无法解析状态码: {}", smsStatus);
                        }
                    }

                    if (msgId != null && !msgId.isEmpty()) {
                        // 解析 pricedetail
                        SmsReportResult.PriceDetail priceDetail = parsePriceDetail(item.getJSONObject("pricedetail"));
                        
                        SmsReportResult.SmsStatus smsStatusObj = new SmsReportResult.SmsStatus(
                                msgId,
                                phoneNumber,
                                statusCode,
                                statusDesc,
                                receiveTime,
                                priceDetail
                        );
                        statusList.add(smsStatusObj);
                    }
                }
            }

            log.info("成功解析{}条短信状态报告", statusList.size());
        } catch (Exception e) {
            log.error("解析Onbuka短信状态报告失败", e);
        }

        return statusList;
    }

    /**
     * 获取状态描述
     *
     * @param statusCode 状态码
     * @return 状态描述
     */
    private String getStatusDescription(String statusCode) {
        return switch (statusCode) {
            case "0" -> "送达";
            case "-1" -> "发送中";
            case "1" -> "发送失败";
            default -> "未知状态" + statusCode;
        };
    }

    /**
     * 解析费用详情
     *
     * @param priceDetailJson 费用详情JSON对象
     * @return 费用详情对象
     */
    private SmsReportResult.PriceDetail parsePriceDetail(JSONObject priceDetailJson) {
        if (priceDetailJson == null || priceDetailJson.isEmpty()) {
            return null;
        }

        try {
            java.math.BigDecimal payAmount = null;
            String currency = priceDetailJson.getStr("currency");
            Integer chargeCount = null;
            java.math.BigDecimal unitPrice = null;
            java.math.BigDecimal quoteExchange = null;
            java.math.BigDecimal settlePay = null;
            String settleCurrency = priceDetailJson.getStr("settleCurrency");
            java.math.BigDecimal settleUnitPrice = null;

            // 解析总费用
            String payStr = priceDetailJson.getStr("pay");
            if (cn.hutool.core.util.StrUtil.isNotBlank(payStr)) {
                payAmount = new java.math.BigDecimal(payStr);
            }

            // 解析计费条数
            chargeCount = priceDetailJson.getInt("chargeCnt");

            // 解析单价
            String priceStr = priceDetailJson.getStr("price");
            if (cn.hutool.core.util.StrUtil.isNotBlank(priceStr)) {
                unitPrice = new java.math.BigDecimal(priceStr);
            }

            // 解析汇率（可选字段）
            String exchangeStr = priceDetailJson.getStr("quoteExchange");
            if (cn.hutool.core.util.StrUtil.isNotBlank(exchangeStr)) {
                quoteExchange = new java.math.BigDecimal(exchangeStr);
            }

            // 解析结算总费用（可选字段）
            String settlePayStr = priceDetailJson.getStr("settlePay");
            if (cn.hutool.core.util.StrUtil.isNotBlank(settlePayStr)) {
                settlePay = new java.math.BigDecimal(settlePayStr);
            }

            // 解析结算单价（可选字段）
            String settlePriceStr = priceDetailJson.getStr("settlePrice");
            if (cn.hutool.core.util.StrUtil.isNotBlank(settlePriceStr)) {
                settleUnitPrice = new java.math.BigDecimal(settlePriceStr);
            }

            return new SmsReportResult.PriceDetail(
                    payAmount,
                    currency,
                    chargeCount,
                    unitPrice,
                    quoteExchange,
                    settlePay,
                    settleCurrency,
                    settleUnitPrice
            );
        } catch (Exception e) {
            log.error("解析费用详情失败", e);
            return null;
        }
    }
}
