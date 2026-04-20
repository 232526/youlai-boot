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
        try {
            // 判断是否为开发环境
            String activeProfile = environment.getActiveProfiles().length > 0
                ? environment.getActiveProfiles()[0]
                : "dev";

            boolean isDevEnvironment = "dev".equals(activeProfile) || "local".equals(activeProfile);

            if (isDevEnvironment) {
                return;
//                // 配置代理 (Clash SOCKS5 代理) - 仅开发环境
//                String proxyHost = "127.0.0.1";
//                int socksProxyPort = 7891; // Clash SOCKS5 代理端口
//
//                log.info("🔧 开始配置 Telegram Bot 代理...");
//                log.info("   - SOCKS5 代理: {}: {}", proxyHost, socksProxyPort);
//
//                // 配置系统属性 (TelegramBots 6.x 使用 HttpClient，会自动读取系统代理)
//                System.setProperty("socksProxyHost", proxyHost);
//                System.setProperty("socksProxyPort", String.valueOf(socksProxyPort));
//
//                // 设置 HTTP 代理 (备用)
//                System.setProperty("http.proxyHost", proxyHost);
//                System.setProperty("http.proxyPort", "7890");
//                System.setProperty("https.proxyHost", proxyHost);
//                System.setProperty("https.proxyPort", "7890");

//                log.info("✅ Telegram Bot 代理配置完成");
            } else {
                log.info("🌐 生产环境，不使用代理，直接连接 Telegram API");
            }

            // 注册前先删除可能存在的 Webhook，避免 409 冲突
            DeleteWebhook deleteWebhook = new DeleteWebhook();
            deleteWebhook.setDropPendingUpdates(true);
            execute(deleteWebhook);
            log.info("🧹 已清除旧的 Webhook/Polling 连接");

            // 短暂等待，确保旧连接释放
            Thread.sleep(1000);

            // 注册 Bot
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(this);

            log.info("✅ Telegram Long Polling Bot 已启动: @{}", botProperties.getUsername());
            log.info("📱 可以在 Telegram 中搜索 @{} 开始使用", botProperties.getUsername());
        } catch (Exception e) {
            log.error("❌ Telegram Long Polling Bot 启动失败: {}", e.getMessage());
            log.error(" 错误类型: {}", e.getClass().getName());
            if (e.getCause() != null) {
                log.error(" 根本原因: {}", e.getCause().getMessage());
            }
            log.error("\n💡 排查步骤:");
            log.error("1. 确认 Clash 是否正常运行且 SOCKS5 端口为 7891");
            log.error("2. 确认 Clash 代理模式已开启 (规则模式/全局模式)");
            log.error("3. 在 PowerShell 中测试: curl.exe -x socks5://127.0.0.1:7891 https://api.telegram.org");
            log.error("4. 如果 SOCKS5 不行,尝试 HTTP 代理 (端口 7890)");
        }
    }
}
