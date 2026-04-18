package com.youlai.boot.interfaces.telegram.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Telegram 群组配置实体
 *
 * @author Lingma
 * @since 2026-04-18
 */
@Data
@Schema(description = "Telegram群组配置")
@TableName("tg_group_config")
public class TelegramGroupConfig {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Telegram 群组ID (chat_id)
     */
    private String groupId;

    /**
     * 群组名称
     */
    private String groupName;

    /**
     * 群组类型: supergroup, group, channel
     */
    private String groupType;

    /**
     * 是否启用: 0-禁用, 1-启用
     */
    private Integer enabled;

    /**
     * 查询模式: keyword-关键词匹配, command-命令模式, all-全部支持
     */
    private String queryMode;

    /**
     * 描述
     */
    private String description;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
