package com.youlai.boot.interfaces.telegram.config;

import com.youlai.boot.config.property.TelegramBotProperties;
import com.youlai.boot.interfaces.telegram.service.TelegramBotService;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.bots.TelegramWebhookBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Telegram Webhook Bot 处理器
 *
 * @author Lingma
 * @since 2026-04-18
 */
@Slf4j
public class TelegramWebhookBotHandler extends TelegramWebhookBot {

    private final TelegramBotProperties botProperties;
    private final TelegramBotService botService;

    public TelegramWebhookBotHandler(TelegramBotProperties botProperties, TelegramBotService botService) {
        this.botProperties = botProperties;
        this.botService = botService;
    }

    @Override
    public String getBotToken() {
        return botProperties.getToken();
    }

    @Override
    public String getBotPath() {
        return null;
    }

    @Override
    public String getBotUsername() {
        return botProperties.getUsername();
    }

    @Override
    public BotApiMethod<?> onWebhookUpdateReceived(Update update) {
        try {
            // 只处理消息类型
            if (update.hasMessage() && update.getMessage().hasText()) {
                log.info("收到 Telegram 消息: chatId={}, text={}", 
                    update.getMessage().getChatId(), 
                    update.getMessage().getText());
                
                // 处理消息并返回回复
                return botService.handleMessage(update);
            }
            
            // 处理回调查询等其他类型
            if (update.hasCallbackQuery()) {
                log.info("收到回调查询: {}", update.getCallbackQuery().getData());
            }
            
        } catch (Exception e) {
            log.error("处理 Telegram 更新失败", e);
            if (update.hasMessage()) {
                SendMessage errorMessage = new SendMessage();
                errorMessage.setChatId(String.valueOf(update.getMessage().getChatId()));
                errorMessage.setText("⚠️ 处理消息时发生错误");
                return errorMessage;
            }
        }
        
        return null;
    }
}
