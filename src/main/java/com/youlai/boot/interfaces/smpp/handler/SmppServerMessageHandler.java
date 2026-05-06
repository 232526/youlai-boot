package com.youlai.boot.interfaces.smpp.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.youlai.boot.framework.security.model.SysUserDetails;
import com.youlai.boot.interfaces.smpp.config.SmppServerProperties;
import com.youlai.boot.market.order.model.form.SmsOrderForm;
import com.youlai.boot.market.order.service.SmsOrderService;
import com.youlai.boot.system.mapper.UserMapper;
import com.youlai.boot.system.model.entity.SysUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsmpp.bean.*;
import org.jsmpp.extra.ProcessRequestException;
import org.jsmpp.session.*;
import org.jsmpp.util.MessageId;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SMPP 服务端消息处理器
 * <p>
 * 处理下游客户端通过 SMPP 协议提交的短信请求：
 * 1. submit_sm → 创建短信订单，返回消息ID
 * 2. submit_multi → 批量创建短信订单
 * <p>
 * 鉴权方式：客户端 bind 时使用 systemId=apiKey, password=apiSecret
 *
 * @author Ray.Hao
 * @since 2026/05/06
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmppServerMessageHandler implements ServerMessageReceiverListener {

    private final UserMapper userMapper;
    private final SmsOrderService smsOrderService;
    private final SmppServerProperties smppServerProperties;

    /**
     * 已认证的会话 -> 用户ID 映射
     */
    private final ConcurrentHashMap<String, Long> authenticatedSessions = new ConcurrentHashMap<>();

    /**
     * 消息ID生成器
     */
    private final AtomicLong messageIdGenerator = new AtomicLong(System.currentTimeMillis());

    /**
     * 处理 submit_sm 请求（客户端发送短信）
     */
    @Override
    public SubmitSmResult onAcceptSubmitSm(SubmitSm submitSm, SMPPServerSession source) throws ProcessRequestException {
        String sessionId = source.getSessionId();
        Long userId = authenticatedSessions.get(sessionId);

        if (userId == null) {
            log.warn("SMPP服务端: 未认证的会话尝试发送消息, sessionId: {}", sessionId);
            throw new ProcessRequestException("Session not authenticated", 0x00000045);
        }

        String destAddress = submitSm.getDestAddress();
        String sourceAddress = submitSm.getSourceAddr();
        byte[] shortMessage = submitSm.getShortMessage();

        // 解析短信内容
        String content;
        byte dataCoding = submitSm.getDataCoding();
        if (dataCoding == (byte) 0x08) {
            // UCS2 编码
            content = new String(shortMessage, StandardCharsets.UTF_16BE);
        } else {
            // GSM 7-bit / ASCII
            content = new String(shortMessage, StandardCharsets.US_ASCII);
        }

        log.info("SMPP服务端: 收到submit_sm - from: {}, to: {}, content长度: {}, userId: {}, sessionId: {}",
                sourceAddress, destAddress, content.length(), userId, sessionId);

        try {
            // 设置安全上下文
            setSecurityContext(userId);

            // 创建短信订单
            SmsOrderForm orderForm = new SmsOrderForm();
            orderForm.setCountryId(1); // 默认印度
            orderForm.setHasAreaCode(0);
            orderForm.setScheduledTime(System.currentTimeMillis() + 2 * 60 * 1000); // 两分钟后发送
            orderForm.setMessageContentList(Collections.singletonList(content));
            orderForm.setPhoneNumberList(Collections.singletonList(destAddress));

            String orderNo = smsOrderService.createOrder(orderForm);
            log.info("SMPP服务端: 创建订单成功 - orderNo: {}, dest: {}, userId: {}", orderNo, destAddress, userId);

            // 生成消息ID返回给客户端
            String msgId = String.valueOf(messageIdGenerator.incrementAndGet());
            return new SubmitSmResult(new MessageId(msgId), new OptionalParameter[0]);

        } catch (Exception e) {
            log.error("SMPP服务端: 创建订单失败 - dest: {}, userId: {}", destAddress, userId, e);
            throw new ProcessRequestException("Failed to create order: " + e.getMessage(), 0x00000045);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 处理 submit_multi 请求（批量短信）
     */
    @Override
    public SubmitMultiResult onAcceptSubmitMulti(SubmitMulti submitMulti, SMPPServerSession source) throws ProcessRequestException {
        String sessionId = source.getSessionId();
        Long userId = authenticatedSessions.get(sessionId);

        if (userId == null) {
            log.warn("SMPP服务端: 未认证的会话尝试发送批量消息, sessionId: {}", sessionId);
            throw new ProcessRequestException("Session not authenticated", 0x00000045);
        }

        // 提取所有目标号码
        DestinationAddress[] destAddresses = submitMulti.getDestAddresses();
        byte[] shortMessage = submitMulti.getShortMessage();

        String content;
        byte dataCoding = submitMulti.getDataCoding();
        if (dataCoding == (byte) 0x08) {
            content = new String(shortMessage, StandardCharsets.UTF_16BE);
        } else {
            content = new String(shortMessage, StandardCharsets.US_ASCII);
        }

        List<String> phoneNumbers = new ArrayList<>();
        for (DestinationAddress addr : destAddresses) {
            if (addr instanceof Address) {
                phoneNumbers.add(((Address) addr).getAddress());
            }
        }

        log.info("SMPP服务端: 收到submit_multi - 目标数量: {}, content长度: {}, userId: {}", phoneNumbers.size(), content.length(), userId);

        try {
            setSecurityContext(userId);

            SmsOrderForm orderForm = new SmsOrderForm();
            orderForm.setCountryId(1);
            orderForm.setHasAreaCode(0);
            orderForm.setScheduledTime(System.currentTimeMillis() + 2 * 60 * 1000);
            orderForm.setMessageContentList(Collections.singletonList(content));
            orderForm.setPhoneNumberList(phoneNumbers);

            String orderNo = smsOrderService.createOrder(orderForm);
            log.info("SMPP服务端: 批量订单创建成功 - orderNo: {}, 号码数: {}, userId: {}", orderNo, phoneNumbers.size(), userId);

            String msgId = String.valueOf(messageIdGenerator.incrementAndGet());
            return new SubmitMultiResult(msgId, new UnsuccessDelivery[0], new OptionalParameter[0]);

        } catch (Exception e) {
            log.error("SMPP服务端: 批量订单创建失败 - userId: {}", userId, e);
            throw new ProcessRequestException("Failed to create order: " + e.getMessage(), 0x00000045);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 处理 query_sm 请求
     */
    @Override
    public QuerySmResult onAcceptQuerySm(QuerySm querySm, SMPPServerSession source) throws ProcessRequestException {
        log.info("SMPP服务端: 收到query_sm请求, messageId: {}", querySm.getMessageId());
        return new QuerySmResult(querySm.getMessageId(), MessageState.DELIVERED, (byte) 0);
    }

    /**
     * 处理 replace_sm（不支持）
     */
    @Override
    public void onAcceptReplaceSm(ReplaceSm replaceSm, SMPPServerSession source) throws ProcessRequestException {
        log.warn("SMPP服务端: 不支持replace_sm操作");
        throw new ProcessRequestException("replace_sm not supported", 0x00000003);
    }

    /**
     * 处理 cancel_sm（不支持）
     */
    @Override
    public void onAcceptCancelSm(CancelSm cancelSm, SMPPServerSession source) throws ProcessRequestException {
        log.warn("SMPP服务端: 不支持cancel_sm操作");
        throw new ProcessRequestException("cancel_sm not supported", 0x00000003);
    }

    /**
     * 处理 broadcast_sm（不支持）
     */
    @Override
    public BroadcastSmResult onAcceptBroadcastSm(BroadcastSm broadcastSm, SMPPServerSession source) throws ProcessRequestException {
        log.warn("SMPP服务端: 不支持broadcast_sm操作");
        throw new ProcessRequestException("broadcast_sm not supported", 0x00000003);
    }

    /**
     * 处理 cancel_broadcast_sm（不支持）
     */
    @Override
    public void onAcceptCancelBroadcastSm(CancelBroadcastSm cancelBroadcastSm, SMPPServerSession source) throws ProcessRequestException {
        log.warn("SMPP服务端: 不支持cancel_broadcast_sm操作");
        throw new ProcessRequestException("cancel_broadcast_sm not supported", 0x00000003);
    }

    /**
     * 处理 query_broadcast_sm（不支持）
     */
    @Override
    public QueryBroadcastSmResult onAcceptQueryBroadcastSm(QueryBroadcastSm queryBroadcastSm, SMPPServerSession source) throws ProcessRequestException {
        log.warn("SMPP服务端: 不支持query_broadcast_sm操作");
        throw new ProcessRequestException("query_broadcast_sm not supported", 0x00000003);
    }

    /**
     * 处理 data_sm 请求
     */
    @Override
    public DataSmResult onAcceptDataSm(DataSm dataSm, Session source) throws ProcessRequestException {
        log.info("SMPP服务端: 收到data_sm请求");
        return null;
    }

    /**
     * 处理 alert_notification
     */
    public void onAcceptAlertNotification(AlertNotification alertNotification) {
        log.info("SMPP服务端: 收到alert_notification");
    }

    /**
     * 验证客户端绑定请求
     * <p>
     * 使用 systemId 作为 apiKey，password 作为 apiSecret 进行鉴权
     *
     * @param systemId  系统ID（对应平台 apiKey）
     * @param password  密码（对应平台 apiSecret）
     * @param sessionId 会话ID
     * @return 是否通过验证
     */
    public boolean authenticate(String systemId, String password, String sessionId) {
        if (systemId == null || password == null) {
            log.warn("SMPP服务端: 绑定请求缺少认证参数, sessionId: {}", sessionId);
            return false;
        }

        // 根据 apiKey 查找用户
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getApiKey, systemId)
                        .eq(SysUser::getIsDeleted, 0)
        );

        if (user == null) {
            log.warn("SMPP服务端: 无效的system_id(apiKey): {}, sessionId: {}", systemId, sessionId);
            return false;
        }

        // 验证密码（apiSecret）
        if (!password.equals(user.getApiSecret())) {
            log.warn("SMPP服务端: 密码验证失败, systemId: {}, sessionId: {}", systemId, sessionId);
            return false;
        }

        // 验证用户状态
        if (user.getStatus() != 1) {
            log.warn("SMPP服务端: 用户已被禁用, systemId: {}, userId: {}", systemId, user.getId());
            return false;
        }

        // 验证余额
        if (user.getPrice() != null && user.getPrice() < 0) {
            log.warn("SMPP服务端: 用户余额不足, systemId: {}, userId: {}", systemId, user.getId());
            return false;
        }

        // 注册已认证会话
        authenticatedSessions.put(sessionId, user.getId());
        log.info("SMPP服务端: 客户端绑定成功 - systemId: {}, userId: {}, sessionId: {}", systemId, user.getId(), sessionId);
        return true;
    }

    /**
     * 移除会话（客户端断开时调用）
     */
    public void removeSession(String sessionId) {
        Long userId = authenticatedSessions.remove(sessionId);
        if (userId != null) {
            log.info("SMPP服务端: 客户端会话已移除 - sessionId: {}, userId: {}", sessionId, userId);
        }
    }

    /**
     * 获取当前活跃会话数
     */
    public int getActiveSessionCount() {
        return authenticatedSessions.size();
    }

    /**
     * 设置安全上下文
     */
    private void setSecurityContext(Long userId) {
        SysUserDetails userDetails = new SysUserDetails();
        userDetails.setUserId(userId);
        userDetails.setEnabled(true);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
