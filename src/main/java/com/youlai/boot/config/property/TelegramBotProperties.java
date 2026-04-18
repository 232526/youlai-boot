package com.youlai.boot.config.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Telegram Bot 配置属性
 *
 * @author Lingma
 * @since 2026-04-18
 */
@Data
@Component
@ConfigurationProperties(prefix = "telegram.bot")
public class TelegramBotProperties {

    /**
     * Bot Token (从 @BotFather 获取)
     */
    private String token;

    /**
     * Webhook URL (机器人接收消息的地址)
     * 例如: https://yourdomain.com/api/telegram/webhook
     */
    private String webhookUrl;

    /**
     * 是否启用 Webhook 模式
     * true: Webhook 模式, false: Long Polling 模式
     */
    private boolean webhookEnabled = true;

    /**
     * Bot 用户名
     */
    private String username;
}
