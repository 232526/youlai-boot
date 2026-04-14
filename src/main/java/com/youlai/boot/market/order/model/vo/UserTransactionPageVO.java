package com.youlai.boot.market.order.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户交易流水分页视图对象
 *
 * @author Ray.Hao
 * @since 2026/04/10
 */
@Schema(description = "用户交易流水分页视图")
@Getter
@Setter
public class UserTransactionPageVO {

    @Schema(description = "流水ID")
    private Long id;

    @Schema(description = "流水号")
    private String transNo;

    @Schema(description = "交易类型")
    private Integer transType;

    @Schema(description = "交易类型描述")
    private String transTypeDesc;

    @Schema(description = "业务类型")
    private String bizType;

    @Schema(description = "交易金额")
    private BigDecimal amount;

    @Schema(description = "交易前账户余额")
    private BigDecimal preBalance;

    @Schema(description = "交易后账户余额")
    private BigDecimal balance;

    @Schema(description = "关联订单号")
    private String relatedOrderNo;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "状态描述")
    private String statusDesc;

    @Schema(description = "备注说明")
    private String remark;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

}
