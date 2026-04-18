package com.youlai.boot.interfaces.telegram.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Telegram 查询规则实体
 *
 * @author Lingma
 * @since 2026-04-18
 */
@Data
@Schema(description = "Telegram查询规则")
@TableName("tg_query_rule")
public class TelegramQueryRule {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 群组ID (关联 tg_group_config.group_id)
     */
    private String groupId;

    /**
     * 规则类型: keyword-关键词, command-命令
     */
    private String ruleType;

    /**
     * 触发关键词或命令 (如: /order, 订单)
     */
    private String triggerWord;

    /**
     * 查询内容类型: sms_order-短信订单, share_order-分享订单, custom-自定义
     */
    private String contentType;

    /**
     * 查询条件 (JSON格式, 存储具体的查询参数)
     */
    private String queryCondition;

    /**
     * 返回模板 (可选, 自定义返回消息格式)
     */
    private String responseTemplate;

    /**
     * 排序号
     */
    private Integer sortOrder;

    /**
     * 是否启用: 0-禁用, 1-启用
     */
    private Integer enabled;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
