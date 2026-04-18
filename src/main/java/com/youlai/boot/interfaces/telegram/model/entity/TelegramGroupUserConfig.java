package com.youlai.boot.interfaces.telegram.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Telegram 群组用户配置实体
 * 用于存储每个群组设置的查询用户名
 *
 * @author Lingma
 * @since 2026-04-18
 */
@Data
@Schema(description = "Telegram群组用户配置")
@TableName("tg_group_user_config")
public class TelegramGroupUserConfig {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Telegram群组ID(chat_id)
     */
    private String groupId;

    /**
     * 要查询的用户名
     */
    private String queryUsername;

    /**
     * 设置者的Telegram用户ID
     */
    private Long setByUserId;

    /**
     * 设置者的Telegram用户名
     */
    private String setByUsername;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
