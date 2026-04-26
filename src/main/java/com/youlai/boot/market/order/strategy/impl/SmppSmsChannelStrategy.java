package com.youlai.boot.market.order.strategy.impl;

import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONObject;
import com.youlai.boot.common.constant.RedisConstants;
import com.youlai.boot.market.order.enums.SmsChannelEnum;
import com.youlai.boot.market.order.strategy.SmsChannelStrategy;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsmpp.InvalidResponseException;
import org.jsmpp.PDUException;
import org.jsmpp.bean.*;
import org.jsmpp.extra.NegativeResponseException;
import org.jsmpp.extra.ProcessRequestException;
import org.jsmpp.extra.ResponseTimeoutException;
import org.jsmpp.session.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SMPP 短信渠道策略实现
 * <p>
 * 通过 SMPP 协议发送短信
 * <p>
 * 服务器IP: 13.234.236.170
 * 端口: 8082
 * 账号: 3000358
 * 密码: kMoCBDD5
 * 最大速率: 300
 * 最大连接数: 1
 *
 * @author Ray.Hao
 * @since 2026/04/24
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SmppSmsChannelStrategy implements SmsChannelStrategy {

    private static final String SMPP_HOST = "13.234.236.170";
    private static final int SMPP_PORT = 8082;
    private static final String SYSTEM_ID = "3000358";
    private static final String PASSWORD = "kMoCBDD5";
    private static final int MAX_RATE = 300;

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * SMPP 会话(长连接复用)
     */
    private volatile SMPPSession session;

    /**
     * 连接锁,保证只建立一个连接
     */
    private final ReentrantLock connectionLock = new ReentrantLock();

    /**
     * SMPP DLR 正文格式正则
     * 示例: id:12345 sub:001 dlvrd:001 submit date:2106241200 done date:2106241201 stat:DELIVRD err:000 text:...
     */
    private static final Pattern DLR_PATTERN = Pattern.compile(
        "id:([^ ]+)\\s+sub:\\d+\\s+dlvrd:\\d+\\s+submit date:(\\d+)\\s+done date:(\\d+)\\s+stat:([A-Z]+)\\s+err:(\\d+)"
    );

    @Override
    public String getChannelCode() {
        return SmsChannelEnum.BESTSMS.getValue();
    }

    @Override
    public String getChannelName() {
        return SmsChannelEnum.BESTSMS.getLabel();
    }

    @Override
    public SmsSendResult sendSms(List<String> phoneNumbers, String senderId, String content) {
        return doSendSms(phoneNumbers, senderId, content, false);
    }

    /**
     * 执行短信发送（支持重试一次）
     * <p>
     * 遇到否定响应、响应超时、无效响应时，先关闭旧连接再重试一次，避免复用已失效的会话
     *
     * @param phoneNumbers 手机号列表
     * @param senderId     发送者ID
     * @param content      短信内容
     * @param isRetry      是否为重试（重试不再递归，避免无限循环）
     * @return 发送结果
     */
    private SmsSendResult doSendSms(List<String> phoneNumbers, String senderId, String content, boolean isRetry) {
        try {
            SMPPSession smppSession = getOrCreateSession();
            List<String> msgIds = new ArrayList<>();

            for (String phoneNumber : phoneNumbers) {
                // 添加+91区号前缀
                String fullNumber = phoneNumber.startsWith("+91") ? phoneNumber : "+91" + phoneNumber;
                // SMPP 协议中号码不带+号
                String destAddress = fullNumber.startsWith("+") ? fullNumber.substring(1) : fullNumber;

                try {
                    String msgId = submitSingleMessage(smppSession, senderId, destAddress, content);
                    if (msgId != null && !msgId.isEmpty()) {
                        msgIds.add(msgId);
                    }
                } catch (NegativeResponseException e) {
                    if (!isRetry) {
                        log.error("SMPP否定响应, 号码: {}, 将关闭连接并重试", destAddress, e);
                        resetSession();
                        return doSendSms(phoneNumbers, senderId, content, true);
                    }
                    log.error("SMPP重试后仍然否定响应, 号码: {}", destAddress, e);
                    return new SmsSendResult(false, "否定响应: " + e.getMessage(), msgIds, e.getMessage());
                } catch (PDUException e) {
                    log.error("SMPP{}PDU异常, 号码: {}", isRetry ? "重试后" : "", destAddress, e);
                    return new SmsSendResult(false, "PDU异常: " + e.getMessage(), msgIds, e.getMessage());
                } catch (ResponseTimeoutException e) {
                    if (!isRetry) {
                        log.error("SMPP响应超时, 号码: {}, 将关闭连接并重试", destAddress, e);
                        resetSession();
                        return doSendSms(phoneNumbers, senderId, content, true);
                    }
                    log.error("SMPP重试后响应超时, 号码: {}", destAddress, e);
                    return new SmsSendResult(false, "响应超时: " + e.getMessage(), msgIds, e.getMessage());
                } catch (InvalidResponseException e) {
                    if (!isRetry) {
                        log.error("SMPP无效响应, 号码: {}, 将关闭连接并重试", destAddress, e);
                        resetSession();
                        return doSendSms(phoneNumbers, senderId, content, true);
                    }
                    log.error("SMPP重试后无效响应, 号码: {}", destAddress, e);
                    return new SmsSendResult(false, "无效响应: " + e.getMessage(), msgIds, e.getMessage());
                }
            }

            log.info("SMPP{}发送完成, 成功{}条", isRetry ? "重试" : "短信", msgIds.size());
            return new SmsSendResult(true, "发送成功", msgIds);

        } catch (Exception e) {
            log.error("SMPP{}发送失败", isRetry ? "重试" : "短信", e);
            String failReason = (isRetry ? "重试发送失败: " : "发送失败: ") + e.getMessage();
            return new SmsSendResult(false, failReason, null, failReason);
        }
    }

    /**
     * 提交单条短信到 SMSC
     *
     * @param smppSession SMPP 会话
     * @param senderId    发送者ID
     * @param destAddress 目标地址（不含+号）
     * @param content     短信内容
     * @return 消息ID
     */
    private String submitSingleMessage(SMPPSession smppSession, String senderId, String destAddress, String content)
        throws PDUException, ResponseTimeoutException, InvalidResponseException,
        NegativeResponseException, IOException {

        // 自动检测编码：纯GSM字符用GSM 7-bit（单条160字符），否则用UCS2（单条70字符）
        boolean useGsm7bit = isGsm7BitCompatible(content);
        byte[] contentBytes;
        Alphabet alphabet;
        int singleSmsMaxBytes;

        if (useGsm7bit) {
            // GSM 7-bit 编码：ASCII字符1字节，单条上限160字节
            contentBytes = content.getBytes(StandardCharsets.US_ASCII);
            alphabet = Alphabet.ALPHA_DEFAULT;
            singleSmsMaxBytes = 160;
        } else {
            // UCS2 编码：每字符2字节，单条上限140字节（70字符）
            contentBytes = content.getBytes(StandardCharsets.UTF_16BE);
            alphabet = Alphabet.ALPHA_UCS2;
            singleSmsMaxBytes = 140;
        }

        if (contentBytes.length <= singleSmsMaxBytes) {
            // 单条短信
            SubmitSmResult result = smppSession.submitShortMessage(
                "CMT",                                    // service type
                TypeOfNumber.ALPHANUMERIC,                // source addr TON
                NumberingPlanIndicator.UNKNOWN,            // source addr NPI
                senderId != null ? senderId : SYSTEM_ID,  // source addr
                TypeOfNumber.INTERNATIONAL,               // dest addr TON
                NumberingPlanIndicator.ISDN,              // dest addr NPI
                destAddress,                              // destination addr
                new ESMClass(),                           // ESM class
                (byte) 0,                                 // protocol id
                (byte) 1,                                 // priority flag
                null,                                     // schedule delivery time
                null,                                     // validity period
                new RegisteredDelivery(SMSCDeliveryReceipt.SUCCESS_FAILURE), // registered delivery
                (byte) 0,                                 // replace if present flag
                new GeneralDataCoding(alphabet),           // data coding
                (byte) 0,                                 // sm default msg id
                contentBytes                              // short message
            );
            return result.getMessageId();
        } else {
            // 长短信：使用 UDH 分片发送
            return sendLongMessage(smppSession, senderId, destAddress, content, useGsm7bit);
        }
    }

    @Override
    public SmsReportResult queryReport(List<String> msgIds) {
        List<SmsReportResult.SmsStatus> statusList = new ArrayList<>();
        SmsReportResult.PriceDetail priceDetail = new SmsReportResult.PriceDetail(
            BigDecimal.valueOf(0.0031), "USD", 1, BigDecimal.valueOf(0.0031), null, null, null, null
        );
        // 收集Redis中未命中的msgId，后续通过query_sm主动查询
        List<String> missedMsgIds = new ArrayList<>();

        for (String msgId : msgIds) {
            Object value = redisTemplate.opsForHash().get(RedisConstants.Sms.SMPP_DLR_HASH, msgId);
            if (value != null) {
                try {
                    JSONObject jsonObject = JSONUtil.parseObj(value.toString());
                    SmsReportResult.SmsStatus status = new SmsReportResult.SmsStatus(
                        jsonObject.getStr("msgId"),
                        jsonObject.getStr("phoneNumber"),
                        jsonObject.getInt("status"),
                        jsonObject.getStr("statusDesc"),
                        jsonObject.getStr("receiveTime"),
                        priceDetail
                    );
                    statusList.add(status);
                    redisTemplate.opsForHash().delete(RedisConstants.Sms.SMPP_DLR_HASH, msgId);
                } catch (Exception e) {
                    log.warn("解析Redis中的SMPP投递回执失败, msgId: {}", msgId, e);
                }
            } else {
                missedMsgIds.add(msgId);
            }
        }

        if (!statusList.isEmpty()) {
            log.info("SMPP渠道从Redis中获取到 {} 条投递回执（共查询 {} 个msgId）", statusList.size(), msgIds.size());
        }

        // Redis未命中的msgId，通过SMPP query_sm主动查询SMSC
        if (!missedMsgIds.isEmpty()) {
            log.info("Redis未命中 {} 个msgId，开始通过query_sm主动查询", missedMsgIds.size());
            List<SmsReportResult.SmsStatus> queriedStatusList = querySmppMessageStatus(missedMsgIds, priceDetail);
            statusList.addAll(queriedStatusList);
        }

        return new SmsReportResult(true, "查询成功", statusList);
    }

    /**
     * 通过 SMPP query_sm 主动查询消息状态
     *
     * @param msgIds      需要查询的消息ID列表
     * @param priceDetail 费用详情
     * @return 查询到的状态列表
     */
    private List<SmsReportResult.SmsStatus> querySmppMessageStatus(List<String> msgIds, SmsReportResult.PriceDetail priceDetail) {
        List<SmsReportResult.SmsStatus> statusList = new ArrayList<>();
        SMPPSession smppSession;
        try {
            smppSession = getOrCreateSession();
        } catch (IOException e) {
            log.error("query_sm查询时获取SMPP会话失败", e);
            return statusList;
        }

        for (String msgId : msgIds) {
            try {
                QuerySmResult queryResult = smppSession.queryShortMessage(
                    msgId,
                    TypeOfNumber.INTERNATIONAL,
                    NumberingPlanIndicator.ISDN,
                    ""
                );

                MessageState messageState = queryResult.getMessageState();
                // 跳过中间态（ENROUTE=发送中），只返回终态结果
                if (messageState == MessageState.ENROUTE) {
                    continue;
                }

                Integer statusCode = convertMessageState(messageState);
                String statusDesc = convertMessageStateToChinese(messageState);
                String receiveTime = parseDlrDate(queryResult.getFinalDate());

                SmsReportResult.SmsStatus status = new SmsReportResult.SmsStatus(
                    msgId,
                    null,
                    statusCode,
                    statusDesc,
                    receiveTime,
                    priceDetail
                );
                statusList.add(status);
            } catch (Exception e) {
                log.debug("query_sm查询消息状态失败, msgId: {}, 原因: {}", msgId, e.getMessage());
            }
        }

        if (!statusList.isEmpty()) {
            log.info("通过query_sm主动查询到 {} 条消息状态", statusList.size());
        }
        return statusList;
    }

    /**
     * 将 SMPP MessageState 转换为系统状态码
     * <p>
     * 与 Onbuka 平台对齐：0=送达, -1=发送中, 1=失败
     *
     * @param state SMPP MessageState
     * @return 系统状态码
     */
    private Integer convertMessageState(MessageState state) {
        return switch (state) {
            case DELIVERED -> 0;     // 送达
            case ENROUTE, ACCEPTED -> -1;    // 发送中
            case EXPIRED, DELETED, UNDELIVERABLE, REJECTED, UNKNOWN -> 1; // 失败
            default -> -1;
        };
    }

    /**
     * 将 SMPP MessageState 转换为中文描述
     *
     * @param state SMPP MessageState
     * @return 中文状态描述
     */
    private String convertMessageStateToChinese(MessageState state) {
        return switch (state) {
            case DELIVERED -> "已送达";
            case ENROUTE -> "发送中";
            case ACCEPTED -> "已接受";
            case EXPIRED -> "已过期";
            case DELETED -> "已删除";
            case UNDELIVERABLE -> "无法送达";
            case REJECTED -> "已拒绝";
            case UNKNOWN -> "未知状态";
            default -> state.name();
        };
    }

    @Override
    public BalanceResult queryBalance() {
        // SMPP 协议本身不支持余额查询
        return new BalanceResult(false, "SMPP渠道不支持余额查询", null, null, null, null);
    }

    /**
     * 获取或创建 SMPP 会话（线程安全）
     *
     * @return SMPP 会话
     * @throws IOException 连接异常
     */
    private SMPPSession getOrCreateSession() throws IOException {
        // 第一次检查：如果会话存在且已绑定，直接复用
        if (session != null && session.getSessionState().isBound()) {
            return session;
        }

        connectionLock.lock();
        try {
            // 双重检查：获取锁后再次验证
            if (session != null && session.getSessionState().isBound()) {
                return session;
            }

            // 仅在连接不可用时才关闭旧会话并创建新连接
            if (session != null) {
                log.warn("SMPP连接已断开或失效 - sessionState: {}, 将重新建立连接", session.getSessionState());
                closeSession();
            } else {
                log.info("首次建立SMPP连接 - {}:{}", SMPP_HOST, SMPP_PORT);
            }

            log.info("正在建立SMPP连接 - {}:{}", SMPP_HOST, SMPP_PORT);

            SMPPSession newSession = new SMPPSession();
            newSession.setTransactionTimer(30000L); // 30秒超时
            newSession.setEnquireLinkTimer(30000);  // 30秒心跳

            // 注册消息接收监听器,接收 deliver_sm 投递回执
            SmppMessageReceiverListener listener = new SmppMessageReceiverListener();
            newSession.setMessageReceiverListener(listener);
            log.info("已注册SMPP消息接收监听器");

            newSession.connectAndBind(
                SMPP_HOST,
                SMPP_PORT,
                new BindParameter(
                    BindType.BIND_TRX,           // 收发模式
                    SYSTEM_ID,                    // system_id
                    PASSWORD,                     // password
                    "CMT",                        // system_type
                    TypeOfNumber.UNKNOWN,         // addr_ton
                    NumberingPlanIndicator.UNKNOWN, // addr_npi
                    null                          // address_range
                )
            );

            log.info("SMPP连接建立成功 - {}:{}, systemId: {}, sessionState: {}", SMPP_HOST, SMPP_PORT, SYSTEM_ID, newSession.getSessionState());
            session = newSession;
            return session;
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * 发送长短信（分片发送）
     *
     * @param smppSession SMPP 会话
     * @param senderId    发送者ID
     * @param destAddress 目标地址
     * @param content     短信内容
     * @return 第一条分片的消息ID
     */
    private String sendLongMessage(SMPPSession smppSession, String senderId, String destAddress, String content, boolean useGsm7bit)
        throws PDUException, ResponseTimeoutException, InvalidResponseException,
        NegativeResponseException, IOException {

        byte[] contentBytes;
        Alphabet alphabet;
        int maxSegmentSize;

        if (useGsm7bit) {
            // GSM 7-bit：UDH占6字节，每片有效载荷最大 160-6=154 字节（即153个GSM字符+1字节填充）
            contentBytes = content.getBytes(StandardCharsets.US_ASCII);
            alphabet = Alphabet.ALPHA_DEFAULT;
            maxSegmentSize = 153;
        } else {
            // UCS2：UDH占6字节，每片有效载荷最大 140-6=134 字节（即67个字符）
            contentBytes = content.getBytes(StandardCharsets.UTF_16BE);
            alphabet = Alphabet.ALPHA_UCS2;
            maxSegmentSize = 134;
        }

        int totalSegments = (int) Math.ceil((double) contentBytes.length / maxSegmentSize);
        byte refNum = (byte) (Math.random() * 255);

        String firstMsgId = null;

        for (int i = 0; i < totalSegments; i++) {
            int offset = i * maxSegmentSize;
            int length = Math.min(maxSegmentSize, contentBytes.length - offset);

            // 构造 UDH (User Data Header)
            byte[] udh = new byte[]{
                0x05,           // UDH Length
                0x00,           // IEI: Concatenated short messages
                0x03,           // IEDL: Information element data length
                refNum,         // Reference number
                (byte) totalSegments, // Total segments
                (byte) (i + 1)       // Segment sequence number
            };

            // 拼接 UDH + 消息体
            byte[] messageWithUdh = new byte[udh.length + length];
            System.arraycopy(udh, 0, messageWithUdh, 0, udh.length);
            System.arraycopy(contentBytes, offset, messageWithUdh, udh.length, length);

            // ESMClass with UDHI bit set (0x40)
            ESMClass esmClass = new ESMClass((byte) 0x40);

            SubmitSmResult submitSmResult = smppSession.submitShortMessage(
                "CMT",
                TypeOfNumber.ALPHANUMERIC,
                NumberingPlanIndicator.UNKNOWN,
                senderId != null ? senderId : SYSTEM_ID,
                TypeOfNumber.INTERNATIONAL,
                NumberingPlanIndicator.ISDN,
                destAddress,
                esmClass,
                (byte) 0,
                (byte) 1,
                null,
                null,
                new RegisteredDelivery(SMSCDeliveryReceipt.SUCCESS_FAILURE),
                (byte) 0,
                new GeneralDataCoding(alphabet),
                (byte) 0,
                messageWithUdh
            );
            if (i == 0) {
                firstMsgId = submitSmResult.getMessageId();
            }
        }

        return firstMsgId;
    }

    /**
     * SMPP 消息接收监听器
     * <p>
     * 接收 SMSC 推送的 deliver_sm（投递回执），解析后缓存到 deliveryReceiptCache
     */
    private class SmppMessageReceiverListener implements MessageReceiverListener {

        @Override
        public void onAcceptDeliverSm(DeliverSm deliverSm) throws ProcessRequestException {
            if (MessageType.SMSC_DEL_RECEIPT.containedIn(deliverSm.getEsmClass())) {
                // 这是投递回执
                try {
                    // 尝试不同编码解析回执内容
                    String content = null;
                    if (deliverSm.getShortMessage() != null) {
                        // 首先尝试 ASCII/UTF-8
                        content = new String(deliverSm.getShortMessage(), StandardCharsets.UTF_8);
                        // 如果看起来不是有效的文本，尝试其他编码
                        if (content.contains("\uFFFD") || content.trim().isEmpty()) {
                            content = new String(deliverSm.getShortMessage(), StandardCharsets.US_ASCII);
                        }
                    }
                    log.info("解析的deliver_sm内容: {}", content);
                    if (content != null) {
                        parseAndCacheDeliveryReceipt(content, deliverSm);
                    }
                } catch (Exception e) {
                    log.error("解析SMPP投递回执失败", e);
                }
            }
        }

        @Override
        public void onAcceptAlertNotification(AlertNotification alertNotification) {
        }

        @Override
        public DataSmResult onAcceptDataSm(DataSm dataSm, Session source) throws ProcessRequestException {
            return null;
        }
    }

    /**
     * 解析投递回执内容并缓存到 Redis
     *
     * @param content   回执文本内容
     * @param deliverSm deliver_sm PDU
     */
    private void parseAndCacheDeliveryReceipt(String content, DeliverSm deliverSm) {
        Matcher matcher = DLR_PATTERN.matcher(content);
        if (!matcher.find()) {
            log.warn("无法解析SMPP投递回执格式: {}", content);
            return;
        }

        String msgId = matcher.group(1);
        String doneDate = matcher.group(3);
        String stat = matcher.group(4);

        // 将 SMPP DLR 状态转换为系统状态码（与 Onbuka 对齐：0=送达, -1=发送中, 1=失败）
        Integer statusCode = convertDlrStatus(stat);
        String statusDesc = convertDlrStatusToChinese(stat);

        // 解析 done date 为可读格式
        String receiveTime = parseDlrDate(doneDate);

        SmsReportResult.SmsStatus smsStatus = new SmsReportResult.SmsStatus(
            msgId,
            deliverSm.getSourceAddr(),
            statusCode,
            statusDesc,
            receiveTime,
            null  // SMPP 协议不返回费用详情
        );

        // 存入 Redis Hash，定时任务消费后删除
        try {
            redisTemplate.opsForHash().put(
                RedisConstants.Sms.SMPP_DLR_HASH,
                msgId,
                JSONUtil.toJsonStr(smsStatus)
            );
            // 设置2天过期时间，避免数据积累过大
            redisTemplate.expire(RedisConstants.Sms.SMPP_DLR_HASH, 2, TimeUnit.DAYS);
            log.info("SMPP投递回执已存入Redis - msgId: {}, stat: {}, statusCode: {}, phone: {}",
                msgId, stat, statusCode, deliverSm.getSourceAddr());
        } catch (Exception e) {
            log.error("SMPP投递回执存入Redis失败, msgId: {}", msgId, e);
        }
    }

    /**
     * 将 SMPP DLR 状态转换为系统状态码
     * <p>
     * 与 Onbuka 平台对齐：0=送达, -1=发送中, 1=失败
     * 这样 convertToSendStatus 可以统一处理
     *
     * @param dlrStat SMPP DLR 状态字符串
     * @return 系统状态码
     */
    private Integer convertDlrStatus(String dlrStat) {
        return switch (dlrStat) {
            case "DELIVRD" -> 0;    // 送达
            case "ACCEPTD" -> -1;   // 已接受，发送中
            case "UNDELIV", "EXPIRED", "DELETED", "REJECTD" -> 1;  // 失败
            default -> {
                log.warn("未知的SMPP DLR状态: {}", dlrStat);
                yield -1;  // 未知状态当作发送中
            }
        };
    }

    /**
     * 将 SMPP DLR 状态转换为中文描述
     *
     * @param dlrStat SMPP DLR 状态字符串
     * @return 中文状态描述
     */
    private String convertDlrStatusToChinese(String dlrStat) {
        return switch (dlrStat) {
            case "DELIVRD" -> "已送达";
            case "ACCEPTD" -> "已接受";
            case "UNDELIV" -> "无法送达";
            case "EXPIRED" -> "已过期";
            case "DELETED" -> "已删除";
            case "REJECTD" -> "已拒绝";
            default -> dlrStat;  // 未知状态保留原文
        };
    }

    /**
     * 检测内容是否可以用 GSM 7-bit 编码
     * <p>
     * GSM 7-bit 基本字符集包含 ASCII 可打印字符（部分除外）和少量特殊字符。
     * 这里简化判断：纯 ASCII 可打印字符（0x20-0x7E）和换行/回车即认为兼容。
     *
     * @param content 短信内容
     * @return true 表示可以用 GSM 7-bit 编码
     */
    private boolean isGsm7BitCompatible(String content) {
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            // ASCII 可打印字符 + 换行(0x0A) + 回车(0x0D)
            if ((c >= 0x20 && c <= 0x7E) || c == '\n' || c == '\r') {
                continue;
            }
            return false;
        }
        return true;
    }

    /**
     * 解析 DLR 日期格式 (YYMMDDhhmm 或 YYMMDDhhmmss) 为标准时间格式
     *
     * @param dlrDate DLR 日期字符串
     * @return 标准时间格式字符串 (yyyy-MM-dd HH:mm:ss)，解析失败返回 null
     */
    private String parseDlrDate(String dlrDate) {
        if (dlrDate == null || dlrDate.length() < 10) {
            return null;
        }
        try {
            String pattern = dlrDate.length() >= 12 ? "yyMMddHHmmss" : "yyMMddHHmm";
            LocalDateTime dateTime = LocalDateTime.parse(dlrDate.substring(0, pattern.length()),
                DateTimeFormatter.ofPattern(pattern));
            return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            log.warn("解析DLR日期失败: {}", dlrDate);
            return null;
        }
    }

    /**
     * 重置会话：关闭当前连接并置空，下次调用 getOrCreateSession() 时会重新建立连接
     */
    private void resetSession() {
        connectionLock.lock();
        try {
            log.warn("SMPP会话重置: 关闭旧连接");
            closeSession();
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * 关闭 SMPP 会话
     */
    private void closeSession() {
        if (session != null) {
            try {
                session.unbindAndClose();
            } catch (Exception e) {
                log.warn("关闭SMPP会话异常", e);
            }
            session = null;
        }
    }

    /**
     * 应用关闭时释放连接
     */
    @PreDestroy
    public void destroy() {
        log.info("正在关闭SMPP连接...");
        closeSession();
        log.info("SMPP连接已关闭");
    }
}
