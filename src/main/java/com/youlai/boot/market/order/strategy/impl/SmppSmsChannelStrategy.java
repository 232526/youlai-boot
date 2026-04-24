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
        try {
            SMPPSession smppSession = getOrCreateSession();
            List<String> msgIds = new ArrayList<>();

            for (String phoneNumber : phoneNumbers) {
                // 添加+91区号前缀
                String fullNumber = phoneNumber.startsWith("+91") ? phoneNumber : "+91" + phoneNumber;
                // SMPP 协议中号码不带+号
                String destAddress = fullNumber.startsWith("+") ? fullNumber.substring(1) : fullNumber;

                try {
                    // 处理长短信：按照 SMPP 协议，如果短信内容超过单条限制，需要进行分片
                    byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

                    String msgId;
                    if (contentBytes.length <= 140) {
                        // 单条短信（JSMPP 3.x 返回 SubmitSmResult 对象）
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
                            new GeneralDataCoding(Alphabet.ALPHA_UCS2), // data coding (UCS2 for unicode)
                            (byte) 0,                                 // sm default msg id
                            content.getBytes(StandardCharsets.UTF_16BE) // short message (UCS2 编码)
                        );
                        msgId = result.getMessageId();
                    } else {
                        // 长短信：使用 UDH 分片发送
                        msgId = sendLongMessage(smppSession, senderId, destAddress, content);
                    }

                    if (msgId != null && !msgId.isEmpty()) {
                        msgIds.add(msgId);
                    }

                } catch (PDUException e) {
                    log.error("SMPP PDU异常, 号码: {}", destAddress, e);
                    return new SmsSendResult(false, "PDU异常: " + e.getMessage(), msgIds, e.getMessage());
                } catch (ResponseTimeoutException e) {
                    log.error("SMPP响应超时, 号码: {}", destAddress, e);
                    return new SmsSendResult(false, "响应超时", msgIds, "响应超时: " + e.getMessage());
                } catch (InvalidResponseException e) {
                    log.error("SMPP无效响应, 号码: {}", destAddress, e);
                    return new SmsSendResult(false, "无效响应: " + e.getMessage(), msgIds, e.getMessage());
                } catch (NegativeResponseException e) {
                    log.error("SMPP否定响应, 号码: {}", destAddress, e);
                    return new SmsSendResult(false, "否定响应: " + e.getMessage(), msgIds, e.getMessage());
                }
            }

            log.info("SMPP短信发送完成, 成功{}条", msgIds.size());
            return new SmsSendResult(true, "发送成功", msgIds);

        } catch (Exception e) {
            log.error("SMPP短信发送失败", e);
            String failReason = "发送失败: " + e.getMessage();
            return new SmsSendResult(false, failReason, null, failReason);
        }
    }

    @Override
    public SmsReportResult queryReport(List<String> msgIds) {
        List<SmsReportResult.SmsStatus> statusList = new ArrayList<>();
        SmsReportResult.PriceDetail priceDetail = new SmsReportResult.PriceDetail(
            BigDecimal.valueOf(0.0031), "USD", 1, BigDecimal.valueOf(0.0031), null, null, null, null
        );
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
            }
        }

        if (!statusList.isEmpty()) {
            log.info("SMPP渠道从Redis中获取到 {} 条投递回执（共查询 {} 个msgId）", statusList.size(), msgIds.size());
        }

        return new SmsReportResult(true, "查询成功", statusList);
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
    private String sendLongMessage(SMPPSession smppSession, String senderId, String destAddress, String content)
        throws PDUException, ResponseTimeoutException, InvalidResponseException,
        NegativeResponseException, IOException {

        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_16BE);
        // UDH 占 6 字节，每片有效载荷最大 134 字节
        int maxSegmentSize = 134;
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

            // JSMPP 2.x 直接返回 messageId
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
                new GeneralDataCoding(Alphabet.ALPHA_UCS2),
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
