package com.youlai.boot.interfaces.telegram.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Telegram Bot 自动配置
 * 注意: Webhook 模式下,消息通过 Controller 接收和处理
 *
 * @author Lingma
 * @since 2026-04-18
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "telegram.bot", name = "token")
public class TelegramBotAutoConfiguration {

    // Webhook 模式下,不需要在这里注册 Bot
    // 消息通过 TelegramWebhookController 接收
    // 回复通过 HTTP API 发送
}
