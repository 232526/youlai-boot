package com.youlai.boot.share.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 社群分享手机号发送明细实体
 *
 * @author Ray.Hao
 * @since 2026/04/08
 */
@TableName("share_phone_record")
@Data
public class SharePhoneRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 记录ID（主键）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long recordId;

    /**
     * 关联订单ID
     */
    private String orderNo;

    /**
     * 关联短信内容ID（内容-号码多对多场景用，单内容可NULL）
     */
    private Long contentId;

    /**
     * 手机号（带/不带区号，按is_with_area_code规则）
     */
    private String phoneNumber;

    /**
     * 发送状态：0=待发送，1=发送成功，2=发送失败
     */
    private Integer sendStatus;

    /**
     * 实际发送时间
     */
    private LocalDateTime sendTime;

    /**
     * 失败原因（失败时记录）
     */
    private String failReason;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /**
     * 创建人 ID
     */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    /**
     * 更新人 ID
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    /**
     * 逻辑删除标识(0-未删除 1-已删除)
     */
    @TableLogic
    private Integer isDeleted;

}
