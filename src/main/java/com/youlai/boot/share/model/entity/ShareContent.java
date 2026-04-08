package com.youlai.boot.share.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 社群分享短信内容明细实体
 *
 * @author Ray.Hao
 * @since 2026/04/08
 */
@TableName("share_content")
@Data
public class ShareContent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 内容ID（主键）
     */
    @TableId(type = IdType.AUTO)
    private Long contentId;

    /**
     * 关联订单ID
     */
    private Long orderNo;

    /**
     * 短信内容文本
     */
    private String content;

    /**
     * 排序号（对应Excel上传顺序）
     */
    private Integer contentSort;

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
