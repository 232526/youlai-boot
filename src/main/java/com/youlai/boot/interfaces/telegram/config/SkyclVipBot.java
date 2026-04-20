package com.youlai.boot.interfaces.telegram.config;

import com.youlai.boot.config.property.TelegramBotProperties;
import com.youlai.boot.interfaces.telegram.service.TelegramBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Telegram Long Polling Bot
 * 用于本地开发和测试,不需要 HTTPS
 *
 * @author Lingma
 * @since 2026-04-18
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "telegram.bot", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SkyclVipBot extends TelegramLongPollingBot {

    private final TelegramBotProperties botProperties;
    private final TelegramBotService botService;
    private final Environment environment;

    /** 防止重复注册 */
    private final AtomicBoolean registered = new AtomicBoolean(false);

    @Override
    public String getBotUsername() {
        return botProperties.getUsername();
    }

    @Override
    public String getBotToken() {
        return botProperties.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            // 只处理消息类型
            if (update.hasMessage() && update.getMessage().hasText()) {
                log.info("收到 Telegram 消息: chatId={}, text={}",
                    update.getMessage().getChatId(),
                    update.getMessage().getText());

                // 处理消息并获取回复
                SendMessage replyMessage = botService.handleMessage(update);

                // 发送回复
                if (replyMessage != null) {
                    execute(replyMessage);
                }
            }

            // 处理回调查询等其他类型
            if (update.hasCallbackQuery()) {
                log.info("收到回调查询: {}", update.getCallbackQuery().getData());
            }

        } catch (Exception e) {
            log.error("处理 Telegram 更新失败", e);
            try {
                if (update.hasMessage()) {
                    SendMessage errorMessage = new SendMessage();
                    errorMessage.setChatId(String.valueOf(update.getMessage().getChatId()));
                    errorMessage.setText("⚠️ 处理消息时发生错误");
                    execute(errorMessage);
                }
            } catch (Exception ex) {
                log.error("发送错误消息失败", ex);
            }
        }
    }

    /**
     * 应用启动后自动注册 Bot
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void registerBot() {
        // 防止重复注册
        if (!registered.compareAndSet(false, true)) {
            log.warn("⚠️ Telegram Bot 已注册，跳过重复注册");
            return;
        }

        try {
            // 判断是否为开发环境
            String activeProfile = environment.getActiveProfiles().length > 0
                ? environment.getActiveProfiles()[0]
                : "dev";

            boolean isDevEnvironment = "dev".equals(activeProfile) || "local".equals(activeProfile);

            if (isDevEnvironment) {
                // 配置代理 (Clash SOCKS5 代理) - 仅开发环境
                String proxyHost = "127.0.0.1";
                int socksProxyPort = 7891;

                log.info("🔧 开始配置 Telegram Bot 代理...");
                System.setProperty("socksProxyHost", proxyHost);
                System.setProperty("socksProxyPort", String.valueOf(socksProxyPort));
                System.setProperty("http.proxyHost", proxyHost);
                System.setProperty("http.proxyPort", "7890");
                System.setProperty("https.proxyHost", proxyHost);
                System.setProperty("https.proxyPort", "7890");
                log.info("✅ Telegram Bot 代理配置完成");
            } else {
                log.info("🌐 生产环境，直接连接 Telegram API");
            }

            // 先删除 Webhook 并通知 Telegram 释放旧的 getUpdates 连接
            DeleteWebhook deleteWebhook = new DeleteWebhook();
            deleteWebhook.setDropPendingUpdates(false);
            execute(deleteWebhook);
            log.info("🧹 已通知 Telegram 清除旧连接");

            // 等待旧的 Long Polling 连接超时（Telegram 端超时约25-30秒）
            log.info("⏳ 等待 35 秒让旧的 polling 连接自然超时...");
            Thread.sleep(35_000);

            // 注册 Bot
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(this);

            log.info("✅ Telegram Long Polling Bot 已启动: @{}", botProperties.getUsername());
        } catch (Exception e) {
            registered.set(false); // 注册失败，允许重试
            log.error("❌ Telegram Long Polling Bot 启动失败: {}", e.getMessage(), e);
        }
    }
}
