package com.youlai.boot.market.order.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户交易流水实体
 *
 * @author Ray.Hao
 * @since 2026/04/10
 */
@TableName("user_transaction")
@Data
public class UserTransaction implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 流水ID（主键）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 全局唯一流水号（业务唯一标识）
     */
    private String transNo;

    /**
     * 交易类型 1=收入 2=支出
     */
    private Integer transType;

    /**
     * 业务类型 营销短信发送
     */
    private String bizType;

    /**
     * 交易金额（正数）
     */
    private BigDecimal amount;

    /**
     * 交易前账户余额
     */
    private BigDecimal preBalance;

    /**
     * 交易后账户余额
     */
    private BigDecimal balance;

    /**
     * 关联订单号/业务单号
     */
    private String relatedOrderNo;

    /**
     * 状态 0=作废 1=成功 2=处理中
     */
    private Integer status;

    /**
     * 备注说明
     */
    private String remark;

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

}
