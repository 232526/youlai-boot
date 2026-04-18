package com.youlai.boot.interfaces.telegram.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.youlai.boot.interfaces.telegram.model.entity.TelegramGroupConfig;
import com.youlai.boot.interfaces.telegram.model.entity.TelegramGroupUserConfig;
import com.youlai.boot.interfaces.telegram.model.entity.TelegramQueryRule;
import com.youlai.boot.market.order.model.entity.SmsOrder;
import com.youlai.boot.market.order.service.SmsOrderService;
import com.youlai.boot.system.model.entity.SysUser;
import com.youlai.boot.system.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

/**
 * Telegram Bot 核心服务 - 处理消息和查询逻辑
 *
 * @author Lingma
 * @since 2026-04-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramBotService {

    private final TelegramGroupConfigService groupConfigService;
    private final TelegramQueryRuleService queryRuleService;
    private final TelegramGroupUserConfigService groupUserConfigService;
    private final SmsOrderService smsOrderService;
    private final UserService userService;

    /**
     * 处理收到的消息
     *
     * @param update Telegram 更新对象
     * @return 回复消息
     */
    public SendMessage handleMessage(Update update) {
        try {
            // 获取聊天ID和消息文本
            Long chatId = update.getMessage().getChatId();
            String messageText = update.getMessage().getText();
            String chatType = update.getMessage().getChat().getType();
            String chatTitle = update.getMessage().getChat().getTitle();

            log.info("收到消息 - ChatId: {}, Type: {}, Text: {}", chatId, chatType, messageText);

            // 注册或更新群组配置
            if (StrUtil.isNotBlank(chatTitle)) {
                groupConfigService.registerOrUpdateGroup(String.valueOf(chatId), chatTitle, chatType);
            }

            // 检查群组是否启用
            TelegramGroupConfig groupConfig = groupConfigService.getByGroupId(String.valueOf(chatId));

            // 如果是命令消息,允许执行(特别是/setuser用于初始化配置)
            boolean isCommand = messageText.startsWith("/");

            if (!isCommand && (groupConfig == null || groupConfig.getEnabled() != 1)) {
                return createReplyMessage(chatId,
                    "❌ 该群组未启用机器人服务\n\n" +
                        "👉 请先执行 /setuser <用户名> 进行配置\n" +
                        "📖 发送 /help 查看详细帮助");
            }

            // 处理命令
            if (messageText.startsWith("/")) {
                return handleCommand(chatId, messageText, update);
            }

            // 处理关键词查询
            return handleKeywordQuery(chatId, messageText);

        } catch (Exception e) {
            log.error("处理消息失败", e);
            return createReplyMessage(update.getMessage().getChatId(), "⚠️ 处理消息时出错: " + e.getMessage());
        }
    }

    /**
     * 处理命令
     */
    private SendMessage handleCommand(Long chatId, String command, Update update) {
        String[] parts = command.split(" ", 2);
        String cmd = parts[0].toLowerCase();

        return switch (cmd) {
            case "/start" -> createReplyMessage(chatId,
                "👋 欢迎使用查询机器人!\n\n" +
                    "可用命令:\n" +
                    "/start - 开始使用\n" +
                    "/help - 显示帮助\n" +
                    "/setuser <用户名> - 设置要查询的用户名\n" +
                    "/unbind - 解除绑定\n" +
                    "/balance - 查询用户余额\n" +
                    "/orders - 查询短信订单\n" +
                    "/status <订单号> - 查询订单状态\n\n" +
                    "也可以直接发送关键词进行查询");

            case "/help" -> createReplyMessage(chatId,
                "📖 使用帮助:\n\n" +
                    "1. 设置查询用户:\n" +
                    "   /setuser <用户名> - 设置要查询的用户\n\n" +
                    "2. 解除绑定:\n" +
                    "   /unbind - 解除当前群组的用户绑定\n\n" +
                    "3. 查询余额:\n" +
                    "   /balance - 查询已设置用户的余额\n\n" +
                    "4. 订单查询:\n" +
                    "   /orders - 查询最近的订单\n" +
                    "   /status <订单号> - 查询指定订单\n\n" +
                    "5. 关键词模式:\n" +
                    "   直接发送: 订单、短信等关键词\n\n"
            );

            case "/setuser" -> {
                if (parts.length > 1) {
                    yield handleSetUser(chatId, parts[1], update.getMessage().getFrom());
                } else {
                    yield createReplyMessage(chatId, "请提供用户名: /setuser <用户名>");
                }
            }

            case "/balance" -> handleBalanceQuery(chatId);

            case "/orders" -> handleOrderQuery(chatId, null);

            case "/status" -> {
                if (parts.length > 1) {
                    yield handleOrderQuery(chatId, parts[1]);
                } else {
                    yield createReplyMessage(chatId, "请提供订单号: /status <订单号>");
                }
            }

            case "/unbind" -> handleUnbind(chatId);

            default -> createReplyMessage(chatId, "❌ 未知命令,发送 /help 查看帮助");
        };
    }

    /**
     * 处理关键词查询
     */
    private SendMessage handleKeywordQuery(Long chatId, String message) {
        // 获取该群组的查询规则
        List<TelegramQueryRule> rules = queryRuleService.getRulesByGroupAndTrigger(
            String.valueOf(chatId), message);

        if (rules.isEmpty()) {
            return createReplyMessage(chatId, "❓ 未识别的查询,发送 /help 查看帮助");
        }

        // 执行第一个匹配的规则
        TelegramQueryRule rule = rules.get(0);
        return executeQueryRule(chatId, rule, message);
    }

    /**
     * 执行查询规则
     */
    private SendMessage executeQueryRule(Long chatId, TelegramQueryRule rule, String userMessage) {
        String contentType = rule.getContentType();

        return switch (contentType) {
            case "sms_order" -> handleSmsOrderQuery(chatId, userMessage, rule.getQueryCondition());
            case "custom" -> createReplyMessage(chatId, "自定义查询功能开发中...");
            default -> createReplyMessage(chatId, "⚠️ 不支持的查询类型: " + contentType);
        };
    }

    /**
     * 处理短信订单查询
     */
    private SendMessage handleSmsOrderQuery(Long chatId, String keyword, String queryCondition) {
        try {
            // 这里可以根据 queryCondition 构建查询条件
            // 简化示例:查询最近的订单
            LambdaQueryWrapper<SmsOrder> wrapper = new LambdaQueryWrapper<>();

            if (StrUtil.isNotBlank(keyword) && !keyword.equals("订单") && !keyword.equals("短信")) {
                // 尝试按订单号查询
                wrapper.eq(SmsOrder::getOrderNo, keyword);
            } else {
                // 查询最近的10条订单
                wrapper.orderByDesc(SmsOrder::getCreateTime);
            }

            wrapper.last("LIMIT 10");
            List<SmsOrder> orders = smsOrderService.list(wrapper);

            if (orders.isEmpty()) {
                return createReplyMessage(chatId, "📭 未找到相关订单");
            }

            StringBuilder response = new StringBuilder("📊 查询结果:\n\n");
            for (int i = 0; i < orders.size(); i++) {
                SmsOrder order = orders.get(i);
                response.append(String.format("%d. 订单号: %s\n", i + 1, order.getOrderNo()));
                response.append(String.format("   状态: %s\n", getOrderStatusText(order.getStatus())));
                response.append(String.format("   数量: %d\n", order.getTotalCount()));
                response.append(String.format("   时间: %s\n\n", order.getCreateTime()));
            }

            return createReplyMessage(chatId, response.toString());

        } catch (Exception e) {
            log.error("查询订单失败", e);
            return createReplyMessage(chatId, "⚠️ 查询失败: " + e.getMessage());
        }
    }

    /**
     * 处理订单查询命令
     */
    private SendMessage handleOrderQuery(Long chatId, String orderNo) {
        return handleSmsOrderQuery(chatId, orderNo, null);
    }

    /**
     * 处理设置用户命令
     */
    private SendMessage handleSetUser(Long chatId, String username, org.telegram.telegrambots.meta.api.objects.User telegramUser) {
        try {
            // 验证用户名是否为空
            username = username.replace(" ", "");
            if (StrUtil.isBlank(username)) {
                return createReplyMessage(chatId, "❌ 用户名不能为空");
            }

            // 检查系统中是否存在该用户
            SysUser sysUser = userService.getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));

            if (sysUser == null) {
                return createReplyMessage(chatId,
                    "❌ 未找到用户: " + username + "\n" +
                        "请确认用户名是否正确");
            }

            // 保存群组用户配置
            Long setByUserId = telegramUser != null ? telegramUser.getId() : null;
            String setByUsername = telegramUser != null ? telegramUser.getUserName() : null;

            boolean success = groupUserConfigService.setQueryUsername(
                String.valueOf(chatId),
                username,
                setByUserId,
                setByUsername
            );

            if (success) {
                return createReplyMessage(chatId,
                    "✅ 设置成功!\n\n" +
                        "👤 查询用户: " + username + "\n" +
                        "💼 用户昵称: " + sysUser.getNickname() + "\n" +
                        "📧 余额: " + (sysUser.getPrice() != null ? sysUser.getPrice() : "0.0") + "\n\n" +
                        "现在可以使用 /balance 命令查询余额");
            } else {
                return createReplyMessage(chatId, "❌ 设置失败,请稍后重试");
            }

        } catch (Exception e) {
            log.error("设置用户失败", e);
            return createReplyMessage(chatId, "⚠️ 设置失败: " + e.getMessage());
        }
    }

    /**
     * 处理解除绑定命令
     */
    private SendMessage handleUnbind(Long chatId) {
        try {
            // 检查是否存在绑定配置
            TelegramGroupUserConfig userConfig = groupUserConfigService.getByGroupId(String.valueOf(chatId));

            if (userConfig == null || StrUtil.isBlank(userConfig.getQueryUsername())) {
                return createReplyMessage(chatId,
                    "⚠️ 当前群组尚未绑定任何用户\n\n" +
                        "请使用 /setuser <用户名> 命令绑定用户");
            }

            String boundUsername = userConfig.getQueryUsername();

            // 删除绑定配置
            boolean success = groupUserConfigService.removeByGroupId(String.valueOf(chatId));

            if (success) {
                return createReplyMessage(chatId,
                    "✅ 解除绑定成功!\n\n" +
                        "👤 已解绑用户: " + boundUsername + "\n\n" +
                        "如需重新绑定,请使用 /setuser <用户名> 命令");
            } else {
                return createReplyMessage(chatId, "❌ 解除绑定失败,请稍后重试");
            }

        } catch (Exception e) {
            log.error("解除绑定失败", e);
            return createReplyMessage(chatId, "⚠️ 解除绑定失败: " + e.getMessage());
        }
    }

    /**
     * 处理余额查询命令
     */
    private SendMessage handleBalanceQuery(Long chatId) {
        try {
            // 获取群组配置的用户名
            TelegramGroupUserConfig userConfig = groupUserConfigService.getByGroupId(String.valueOf(chatId));

            if (userConfig == null || StrUtil.isBlank(userConfig.getQueryUsername())) {
                return createReplyMessage(chatId,
                    "⚠️ 尚未设置查询用户\n\n" +
                        "请使用 /setuser <用户名> 命令设置要查询的用户");
            }

            String username = userConfig.getQueryUsername();

            // 查询系统用户
            SysUser sysUser = userService.getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));

            if (sysUser == null) {
                return createReplyMessage(chatId, "❌ 未找到用户: " + username);
            }

            // 构建余额信息
            StringBuilder response = new StringBuilder();
            response.append("💰 用户余额信息\n\n");
            response.append("👤 用户名: ").append(sysUser.getUsername()).append("\n");
            response.append("🆔 用户昵称: ").append(sysUser.getNickname()).append("\n");
            // 例如: 查询用户的账户余额、积分等
            response.append("\n💵 账户余额:").append(sysUser.getPrice()).append("\n");
            if (sysUser.getPrice() >= 0) {
                response.append("⭐ SMS剩余发送条数为:").append((int) (sysUser.getPrice() / sysUser.getSmsUnitPrice())).append("\n");
            } else {
                response.append("⭐ SMS剩余发送条数为: 0").append("\n");
            }

            response.append("\n⏰ 查询时间: ").append(java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            return createReplyMessage(chatId, response.toString());

        } catch (Exception e) {
            log.error("查询余额失败", e);
            return createReplyMessage(chatId, "⚠️ 查询失败: " + e.getMessage());
        }
    }

    /**
     * 获取订单状态文本
     */
    private String getOrderStatusText(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "待处理";
            case 1 -> "处理中";
            case 2 -> "已完成";
            case 3 -> "已取消";
            case 4 -> "失败";
            default -> "未知状态";
        };
    }

    /**
     * 创建回复消息
     */
    private SendMessage createReplyMessage(Long chatId, String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        // 转义 HTML 特殊字符，防止解析错误
        String escapedText = escapeHtml(text);
        sendMessage.setText(escapedText);
        sendMessage.setParseMode("HTML");
        return sendMessage;
    }

    /**
     * 转义 HTML 特殊字符
     */
    private String escapeHtml(String text) {
        if (text == null) {
            return null;
        }
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
