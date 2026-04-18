package com.youlai.boot.interfaces.telegram.util;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.youlai.boot.config.property.TelegramBotProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Telegram Webhook 设置工具
 * 应用启动时自动设置 Webhook
 *
 * @author Lingma
 * @since 2026-04-18
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "telegram.bot", name = {"token", "webhook-enabled"}, havingValue = "true")
public class WebhookSetupRunner implements CommandLineRunner {

    private final TelegramBotProperties botProperties;

    @Override
    public void run(String... args) {
        if (!botProperties.isWebhookEnabled()) {
            log.info("Webhook 模式未启用,跳过 Webhook 设置");
            return;
        }

        String webhookUrl = botProperties.getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.warn("Webhook URL 未配置,跳过 Webhook 设置");
            return;
        }

        try {
            // 设置 Webhook
            String url = String.format("https://api.telegram.org/bot%s/setWebhook", 
                botProperties.getToken());
            
            JSONObject params = new JSONObject();
            params.set("url", webhookUrl);
            
            String response = HttpUtil.post(url, params.toString());
            JSONObject result = JSONUtil.parseObj(response);
            
            if (result.getBool("ok")) {
                log.info("✅ Telegram Webhook 设置成功: {}", webhookUrl);
            } else {
                log.error("❌ Telegram Webhook 设置失败: {}", result.getStr("description"));
            }
            
            // 验证 Webhook
            verifyWebhook();
            
        } catch (Exception e) {
            log.error("设置 Telegram Webhook 时发生错误", e);
        }
    }

    /**
     * 验证 Webhook 是否设置成功
     */
    private void verifyWebhook() {
        try {
            String url = String.format("https://api.telegram.org/bot%s/getWebhookInfo", 
                botProperties.getToken());
            
            String response = HttpUtil.get(url);
            JSONObject result = JSONUtil.parseObj(response);
            
            if (result.getBool("ok")) {
                JSONObject webhookInfo = result.getJSONObject("result");
                log.info("📊 Webhook 信息:");
                log.info("   - URL: {}", webhookInfo.getStr("url"));
                log.info("   - Pending Updates: {}", webhookInfo.getInt("pending_update_count"));
                log.info("   - Last Error: {}", webhookInfo.getStr("last_error_message"));
            }
        } catch (Exception e) {
            log.error("验证 Webhook 失败", e);
        }
    }
}
