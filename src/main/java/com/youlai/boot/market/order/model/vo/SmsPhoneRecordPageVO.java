package com.youlai.boot.market.order.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 短信发送记录分页视图对象
 *
 * @author Ray.Hao
 * @since 2026/04/07
 */
@Schema(description = "短信发送记录分页视图")
@Getter
@Setter
public class SmsPhoneRecordPageVO {

    @Schema(description = "记录ID")
    private Long recordId;

    @Schema(description = "所属订单")
    private String orderNo;

    @Schema(description = "手机号")
    private String phoneNumber;

    @Schema(description = "短信内容")
    private String content;

    @Schema(description = "发送状态")
    private Integer sendStatus;

    @Schema(description = "发送状态描述")
    private String sendStatusDesc;

    @Schema(description = "发送时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime sendTime;

    @Schema(description = "失败原因")
    private String failReason;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "总费用")
    private BigDecimal payAmount;

    @Schema(description = "报价币种")
    private String currency;

    @Schema(description = "计费条数")
    private Integer chargeCount;

    @Schema(description = "报价单价")
    private BigDecimal unitPrice;

    @Schema(description = "我方支付总费用")
    private BigDecimal mePayAmount;

}
