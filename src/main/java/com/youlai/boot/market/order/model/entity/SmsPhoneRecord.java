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
 * 手机号发送记录实体
 *
 * @author Ray.Hao
 * @since 2026/04/07
 */
@TableName("sms_phone_record")
@Data
public class SmsPhoneRecord implements Serializable {

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
     * 短信消息ID
     */
    private String msgId;

    /**
     * 发送的第三方渠道
     */
    private String channel;

    /**
     * 发送状态：0=未发送， 1=发送中，2=发送成功  -1 发送失败  -2 发送取消
     */
    private Integer sendStatus;

    /**
     * 实际发送时间
     */
    private LocalDateTime sendTime;

    /**
     * 状态报告接收时间
     */
    private LocalDateTime receiveTime;

    /**
     * 失败原因（失败时记录）
     */
    private String failReason;

    /**
     * 总费用
     */
    private BigDecimal payAmount;

    /**
     * 报价币种（如USD、EUR）
     */
    private String currency;

    /**
     * 计费条数
     */
    private Integer chargeCount;

    /**
     * 报价单价
     */
    private BigDecimal unitPrice;

    /**
     * 报价汇率（报价币种非USD时返回）
     */
    private BigDecimal quoteExchange;

    /**
     * 结算币种总费用（报价币种非USD时返回）
     */
    private BigDecimal settlePay;

    /**
     * 结算币种（报价币种非USD时返回）
     */
    private String settleCurrency;

    /**
     * 结算币种单价（报价币种非USD时返回）
     */
    private BigDecimal settleUnitPrice;

    /**
     * 我方支付总费用
     */
    private BigDecimal mePayAmount;

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


    /**
     * 报价单价
     */
    private BigDecimal outUnitPrice;


    /**
     * 总费用
     */
    private BigDecimal outPayAmount;

    /**
     * 反转率
     * 默认为0为不是 ，1 为是
     */

    private Integer isFlip = 0;


    /**
     * 反转时候记录
     * 失败原因（失败时记录）
     */
    private String flipFailReason;


}
