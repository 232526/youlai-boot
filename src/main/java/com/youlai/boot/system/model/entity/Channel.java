package com.youlai.boot.system.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 渠道对象
 *
 * @author Theo
 * @since 2026-04-13
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "渠道")
@TableName("sys_channel")
public class Channel {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 渠道类型 sms / ws
     */
    private String type;

    /**
     * 名称
     */
    private String name;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 余额
     */
    private Double balance;

    /**
     * 赠送余额
     */
    private Double gift;


    /**
     * 信用额度
     */
    private Double credit;


    /**
     * 币总
     */
    private String coin;

    /**
     * 备注
     */
    private String remark;

    /**
     * 更新时间
     */
    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
