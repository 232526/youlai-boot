package com.youlai.boot.interfaces.smpp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * SMPP 服务端配置属性
 * <p>
 * 用于配置对外提供的 SMPP 服务端参数，下游客户端通过 SMPP 协议连接本服务发送短信
 *
 * @author Ray.Hao
 * @since 2026/05/06
 */
@Data
@Component
@ConfigurationProperties(prefix = "smpp.server")
public class SmppServerProperties {

    /**
     * 是否启用 SMPP 服务端
     */
    private boolean enabled = true;

    /**
     * SMPP 服务端监听端口
     */
    private int port = 12775;

    /**
     * 会话超时时间（毫秒），客户端在此时间内无活动将断开
     */
    private long sessionTimeout = 60000;

    /**
     * 最大并发会话数
     */
    private int maxSessions = 50;

    /**
     * 每个会话每秒最大提交速率（TPS）
     */
    private int maxTps = 100;

    /**
     * Enquire link 超时时间（毫秒）
     */
    private long enquireLinkTimeout = 30000;
}
