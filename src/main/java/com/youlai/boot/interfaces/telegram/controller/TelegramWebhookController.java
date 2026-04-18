package com.youlai.boot.interfaces.telegram.controller;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.youlai.boot.common.result.Result;
import com.youlai.boot.config.property.TelegramBotProperties;
import com.youlai.boot.interfaces.telegram.service.TelegramBotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Telegram Webhook 控制器
 * 接收 Telegram 服务器推送的消息
 *
 * @author Lingma
 * @since 2026-04-18
 */
@Slf4j
@Tag(name = "Telegram Bot接口")
@RestController
@RequestMapping("/api/telegram")
@RequiredArgsConstructor
public class TelegramWebhookController {

    private final TelegramBotService botService;
    private final TelegramBotProperties botProperties;

    /**
     * Telegram Webhook 端点
     * Telegram 服务器会 POST 消息到这个地址
     *
     * @param update Telegram 更新对象
     * @return 处理结果
     */
    @Operation(summary = "Telegram Webhook接收端点")
    @PostMapping("/webhook")
    public Result<Void> handleWebhook(@RequestBody Update update) {
        try {
            log.debug("收到 Telegram Webhook 请求");
            
            // 处理消息并获取回复
            SendMessage replyMessage = botService.handleMessage(update);
            
            // 如果有回复消息,发送给 Telegram API
            if (replyMessage != null) {
                sendReply(replyMessage);
            }
            
            return Result.success();
        } catch (Exception e) {
            log.error("处理 Telegram Webhook 失败", e);
            return Result.failed("处理失败: " + e.getMessage());
        }
    }

    /**
     * 发送回复消息到 Telegram
     */
    private void sendReply(SendMessage sendMessage) {
        try {
            String url = String.format("https://api.telegram.org/bot%s/sendMessage",
                botProperties.getToken());
            
            String response = HttpUtil.post(url, JSONUtil.toJsonStr(sendMessage));
            log.debug("发送回复消息结果: {}", response);
        } catch (Exception e) {
            log.error("发送回复消息失败", e);
        }
    }

    /**
     * 健康检查端点
     */
    @Operation(summary = "Telegram Bot 健康检查")
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("Telegram Bot is running");
    }
}
