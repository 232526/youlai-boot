package com.youlai.boot.market.order.strategy.impl;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.onbuka.api.sdk.client.SmsSdkClient;
import com.onbuka.api.sdk.model.ApiData;
import com.onbuka.api.sdk.model.smsdto.ReportDTO;
import com.onbuka.api.sdk.model.smsdto.SendSmsDTO;
import com.youlai.boot.market.order.enums.SmsChannelEnum;
import com.youlai.boot.market.order.strategy.SmsChannelStrategy;
import com.youlai.boot.system.service.ChannelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
@RequiredArgsConstructor
public class OnbukaSmsChannelStrategy implements SmsChannelStrategy {

    private final ChannelService channelService;

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
            HttpRequest request = HttpRequest.post(API_URL + "/sendSms");

            //generate md5 key
            String datetime = String.valueOf(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().getEpochSecond());
            String sign = SecureUtil.md5(APP_KEY.concat(APP_SECRET).concat(datetime));

            request.header(Header.CONNECTION, "Keep-Alive")
                .header(Header.CONTENT_TYPE, "application/json;charset=UTF-8")
                .header("Sign", sign)
                .header("Timestamp", datetime)
                .header("Api-Key", APP_KEY);

            final String params = JSONUtil.createObj()
                .set("appId", APP_ID)
                .set("numbers", String.join(",", phoneNumbers))
                .set("content", content)
                .set("senderId", senderId)
                .toString();

            HttpResponse response = request.body(params).execute();
            if (response.isOk()) {
                String result = response.body();
                // 这里需要根据实际的返回格式进行解析
                List<String> msgIds = parseMsgIds(result);
                return new SmsSendResult(true, "发送成功", msgIds);
            }
            // HTTP请求失败，提取错误信息
            String failReason = "HTTP请求失败: " + response.getStatus();
            log.error("Onbuka短信发送HTTP请求失败: {}", failReason);
            return new SmsSendResult(false, failReason, null, failReason);


        } catch (RuntimeException e) {
            // 业务异常（包括状态码失败）
            log.error("Onbuka短信发送失败", e);
            String failReason = e.getMessage();
            return new SmsSendResult(false, failReason, null, failReason);
        } catch (Exception e) {
            log.error("Onbuka短信发送失败", e);
            String failReason = "发送失败: " + e.getMessage();
            return new SmsSendResult(false, failReason, null, failReason);
        }
    }

    @Override
    public SmsReportResult queryReport(List<String> msgIds) {
        try {
            // 多个msgId用英文逗号分隔，单次查询最大200个msgId
            String msgIdsStr = String.join(",", msgIds);

            // 构造URL: https://api.onbuka.com/v3/getReport?appId={}&msgIds={}
            String url = API_URL + "/getReport";
            String queryString = "?appId=" + APP_ID + "&msgIds=" + msgIdsStr;

            HttpRequest request = HttpRequest.get(url + queryString);

            //generate md5 key
            String datetime = String.valueOf(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().getEpochSecond());
            String sign = SecureUtil.md5(APP_KEY.concat(APP_SECRET).concat(datetime));

            request.header(Header.CONNECTION, "Keep-Alive")
                .header(Header.CONTENT_TYPE, "application/json;charset=UTF-8")
                .header("Sign", sign)
                .header("Timestamp", datetime)
                .header("Api-Key", APP_KEY);

            HttpResponse response = request.execute();
            if (response.isOk()) {
                String reportResult = response.body();
                log.info("Onbuka短信状态报告: {}", reportResult);

                // 这里需要根据实际的返回格式进行解析
                List<SmsReportResult.SmsStatus> statusList = parseReportResult(reportResult);

                return new SmsReportResult(true, "查询成功", statusList);
            } else {
                String errorMsg = "HTTP请求失败: " + response.getStatus();
                log.error("Onbuka短信状态报告查询HTTP请求失败: {}", errorMsg);
                return new SmsReportResult(false, errorMsg, null);
            }

        } catch (Exception e) {
            log.error("Onbuka短信状态报告查询失败", e);
            return new SmsReportResult(false, "查询失败: " + e.getMessage(), null);
        }
    }

    @Override
    public BalanceResult queryBalance() {
        try {
            String url = API_URL + "/getBalance";

            HttpRequest request = HttpRequest.get(url);

            //generate md5 key
            String datetime = String.valueOf(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().getEpochSecond());
            String sign = SecureUtil.md5(APP_KEY.concat(APP_SECRET).concat(datetime));

            request.header(Header.CONNECTION, "Keep-Alive")
                .header(Header.CONTENT_TYPE, "application/json;charset=UTF-8")
                .header("Sign", sign)
                .header("Timestamp", datetime)
                .header("Api-Key", APP_KEY);

            HttpResponse response = request.execute();
            if (response.isOk()) {
                String balanceResult = response.body();
                log.info("Onbuka账户余额查询结果: {}", balanceResult);

                // 解析余额结果
                BalanceResult balanceResultObj = parseBalanceResult(balanceResult);

                // 如果查询成功，更新数据库中的余额
                if (balanceResultObj.success()) {
                    try {
                        // 将 BigDecimal 转换为 Double
                        Double balance = balanceResultObj.balance() != null ?
                            balanceResultObj.balance().doubleValue() : null;
                        Double gift = balanceResultObj.gift() != null ?
                            balanceResultObj.gift().doubleValue() : null;
                        Double credit = balanceResultObj.credit() != null ?
                            balanceResultObj.credit().doubleValue() : null;
                        String currency = balanceResultObj.currency() != null ? balanceResultObj.currency() : "美元";

                        // 调用 ChannelService 更新余额
                        boolean updated = channelService.updateBalanceByCode(
                            getChannelCode(),
                            balance,
                            gift,
                            credit,
                            currency
                        );

                        if (updated) {
                            log.info("渠道 {} 余额已更新到数据库 - 余额: {}, 赠送: {}, 信用: {}, 币种: {}",
                                getChannelCode(), balance, gift, credit, currency);
                        } else {
                            log.warn("渠道 {} 余额更新到数据库失败", getChannelCode());
                        }
                    } catch (Exception e) {
                        log.error("更新渠道 {} 余额到数据库时发生异常", getChannelCode(), e);
                    }
                }

                return balanceResultObj;
            } else {
                String errorMsg = "HTTP请求失败: " + response.getStatus();
                log.error("Onbuka账户余额查询HTTP请求失败: {}", errorMsg);
                return new BalanceResult(false, errorMsg, null, null, null, null);
            }

        } catch (Exception e) {
            log.error("Onbuka账户余额查询失败", e);
            return new BalanceResult(false, "查询失败: " + e.getMessage(), null, null, null, null);
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
                // 获取失败原因
                String failReason = jsonObject.toString();
                log.warn("Onbuka短信发送失败，状态码: {}, 失败原因: {}", status, failReason);
                throw new RuntimeException("短信发送失败: " + failReason);
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
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析Onbuka短信发送结果失败", e);
            throw new RuntimeException("解析发送结果失败: " + e.getMessage(), e);
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

    /**
     * 解析余额查询结果
     *
     * @param balanceResult 余额查询结果JSON字符串
     * @return 余额结果对象
     */
    private BalanceResult parseBalanceResult(String balanceResult) {
        try {
            JSONObject jsonObject = JSONUtil.parseObj(balanceResult);

            // 检查状态是否为成功
            String status = jsonObject.getStr("status");
            if (!"0".equals(status)) {
                String failReason = jsonObject.getStr("msg");
                log.warn("Onbuka账户余额查询失败，状态码: {}, 失败原因: {}", status, failReason);
                return new BalanceResult(false, "查询失败: " + failReason, null, null, null, null);
            }

            // 获取余额信息
            BigDecimal balance = null;
            BigDecimal gift = null;
            BigDecimal credit = null;
            String currency = null;

            // 尝试从 data 对象中获取
            JSONObject data = jsonObject.getJSONObject("data");
            if (data != null) {
                String balanceStr = data.getStr("balance");
                if (cn.hutool.core.util.StrUtil.isNotBlank(balanceStr)) {
                    balance = new BigDecimal(balanceStr);
                }

                String giftStr = data.getStr("gift");
                if (cn.hutool.core.util.StrUtil.isNotBlank(giftStr)) {
                    gift = new BigDecimal(giftStr);
                }

                String creditStr = data.getStr("credit");
                if (cn.hutool.core.util.StrUtil.isNotBlank(creditStr)) {
                    credit = new BigDecimal(creditStr);
                }

                currency = data.getStr("currency");
            } else {
                // 直接从根对象获取
                String balanceStr = jsonObject.getStr("balance");
                if (cn.hutool.core.util.StrUtil.isNotBlank(balanceStr)) {
                    balance = new BigDecimal(balanceStr);
                }

                String giftStr = jsonObject.getStr("gift");
                if (cn.hutool.core.util.StrUtil.isNotBlank(giftStr)) {
                    gift = new BigDecimal(giftStr);
                }

                String creditStr = jsonObject.getStr("credit");
                if (cn.hutool.core.util.StrUtil.isNotBlank(creditStr)) {
                    credit = new BigDecimal(creditStr);
                }

                currency = jsonObject.getStr("currency");
            }

            log.info("成功解析Onbuka账户余额 - 余额: {}, 赠送: {}, 信用: {}, 币种: {}",
                balance, gift, credit, currency);
            return new BalanceResult(true, "查询成功", balance, gift, credit, currency);

        } catch (Exception e) {
            log.error("解析Onbuka账户余额结果失败", e);
            return new BalanceResult(false, "解析失败: " + e.getMessage(), null, null, null, null);
        }
    }
}
