package com.youlai.boot.interfaces.smpp.server;

import com.youlai.boot.interfaces.smpp.config.SmppServerProperties;
import com.youlai.boot.interfaces.smpp.handler.SmppServerMessageHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsmpp.PDUStringException;
import org.jsmpp.SMPPConstant;
import org.jsmpp.bean.InterfaceVersion;
import org.jsmpp.session.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SMPP 服务端
 * <p>
 * 对外提供 SMPP 协议接口，下游客户端可通过 SMPP 协议连接并提交短信发送请求。
 * <p>
 * 鉴权方式：
 * - system_id = 平台分配的 API Key
 * - password = 平台分配的 API Secret
 * <p>
 * 支持的操作：
 * - bind_transmitter / bind_receiver / bind_transceiver
 * - submit_sm（单条短信提交）
 * - submit_multi（批量短信提交）
 * - enquire_link（心跳保活）
 * - unbind（断开连接）
 *
 * @author Ray.Hao
 * @since 2026/05/06
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "smpp.server", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SmppServer {

    private final SmppServerProperties properties;
    private final SmppServerMessageHandler messageHandler;

    private SMPPServerSessionListener sessionListener;
    private ExecutorService acceptorExecutor;
    private ExecutorService sessionExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 系统启动时初始化 SMPP 服务端
     */
    @PostConstruct
    public void start() {
        if (!properties.isEnabled()) {
            log.info("SMPP服务端: 已禁用，跳过启动");
            return;
        }

        acceptorExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "smpp-server-acceptor");
            t.setDaemon(true);
            return t;
        });

        sessionExecutor = Executors.newFixedThreadPool(properties.getMaxSessions(), r -> {
            Thread t = new Thread(r, "smpp-session-handler");
            t.setDaemon(true);
            return t;
        });

        acceptorExecutor.submit(this::acceptConnections);
        log.info("SMPP服务端: 启动中，监听端口 {}", properties.getPort());
    }

    /**
     * 接受客户端连接的主循环
     */
    private void acceptConnections() {
        try {
            sessionListener = new SMPPServerSessionListener(properties.getPort());
            sessionListener.setTimeout((int) properties.getSessionTimeout());
            running.set(true);
            log.info("SMPP服务端: 已启动，等待客户端连接 - port: {}", properties.getPort());

            while (running.get()) {
                try {
                    SMPPServerSession serverSession = sessionListener.accept();
                    log.info("SMPP服务端: 新客户端连接 - sessionId: {}", serverSession.getSessionId());

                    // 检查最大连接数
                    if (messageHandler.getActiveSessionCount() >= properties.getMaxSessions()) {
                        log.warn("SMPP服务端: 已达到最大会话数 {}, 拒绝新连接", properties.getMaxSessions());
                        serverSession.close();
                        continue;
                    }

                    // 在线程池中处理绑定请求
                    sessionExecutor.submit(() -> handleSession(serverSession));

                } catch (IOException e) {
                    if (running.get()) {
                        log.error("SMPP服务端: 接受连接异常", e);
                    }
                }
            }
        } catch (IOException e) {
            log.error("SMPP服务端: 启动监听失败, port: {}", properties.getPort(), e);
        }
    }

    /**
     * 处理单个客户端会话
     */
    private void handleSession(SMPPServerSession serverSession) {
        try {
            // 等待客户端发送 bind 请求
            BindRequest bindRequest = serverSession.waitForBind(properties.getSessionTimeout());

            String systemId = bindRequest.getSystemId();
            String password = bindRequest.getPassword();

            log.info("SMPP服务端: 收到bind请求 - systemId: {}, bindType: {}, sessionId: {}",
                    systemId, bindRequest.getBindType(), serverSession.getSessionId());

            // 验证客户端身份
            boolean authenticated = messageHandler.authenticate(systemId, password, serverSession.getSessionId());

            if (!authenticated) {
                log.warn("SMPP服务端: 认证失败, systemId: {}, sessionId: {}", systemId, serverSession.getSessionId());
                bindRequest.reject(SMPPConstant.STAT_ESME_RINVPASWD);
                return;
            }

            // 认证成功，接受绑定
            bindRequest.accept(systemId, InterfaceVersion.IF_34);

            // 设置消息接收监听器
            serverSession.setMessageReceiverListener(messageHandler);

            // 设置 enquire link 定时器
            serverSession.setEnquireLinkTimer((int) properties.getEnquireLinkTimeout());

            log.info("SMPP服务端: 客户端绑定成功 - systemId: {}, sessionId: {}", systemId, serverSession.getSessionId());

            // 添加会话状态监听，当会话关闭时清理资源
            serverSession.addSessionStateListener((newState, oldState, session) -> {
                log.info("SMPP服务端: 会话状态变更 - sessionId: {}, {} -> {}",
                        serverSession.getSessionId(), oldState, newState);
                if (!newState.isBound()) {
                    messageHandler.removeSession(serverSession.getSessionId());
                }
            });

        } catch (TimeoutException e) {
            log.warn("SMPP服务端: 等待bind超时, sessionId: {}", serverSession.getSessionId());
            serverSession.close();
        } catch (PDUStringException e) {
            log.error("SMPP服务端: PDU字符串异常, sessionId: {}", serverSession.getSessionId(), e);
            serverSession.close();
        } catch (IOException e) {
            log.error("SMPP服务端: IO异常, sessionId: {}", serverSession.getSessionId(), e);
            serverSession.close();
        } catch (Exception e) {
            log.error("SMPP服务端: 处理会话异常, sessionId: {}", serverSession.getSessionId(), e);
            messageHandler.removeSession(serverSession.getSessionId());
            serverSession.close();
        }
    }

    /**
     * 应用关闭时停止 SMPP 服务端
     */
    @PreDestroy
    public void stop() {
        running.set(false);
        log.info("SMPP服务端: 正在关闭...");

        if (sessionListener != null) {
            try {
                sessionListener.close();
            } catch (IOException e) {
                log.warn("SMPP服务端: 关闭监听器异常", e);
            }
        }

        if (acceptorExecutor != null) {
            acceptorExecutor.shutdownNow();
        }

        if (sessionExecutor != null) {
            sessionExecutor.shutdownNow();
        }

        log.info("SMPP服务端: 已关闭");
    }
}
