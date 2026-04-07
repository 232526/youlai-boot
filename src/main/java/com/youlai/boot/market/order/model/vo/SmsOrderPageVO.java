package com.youlai.boot.market.order.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 短信订单分页视图对象
 *
 * @author Ray.Hao
 * @since 2026/04/07
 */
@Schema(description = "短信订单分页视图")
@Getter
@Setter
public class SmsOrderPageVO {

    @Schema(description = "订单ID")
    private Long id;

    @Schema(description = "订单编号")
    private String orderNo;

    @Schema(description = "国家名称")
    private String countryName;

    @Schema(description = "是否携带区号：0-否，1-是")
    private Integer hasAreaCode;

    @Schema(description = "预约启动时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime scheduledTime;

    @Schema(description = "订单状态")
    private Integer status;

    @Schema(description = "订单状态描述")
    private String statusDesc;

    @Schema(description = "发送成功数量")
    private Integer successCount;

    @Schema(description = "发送失败数量")
    private Integer failCount;

    @Schema(description = "总数")
    private Integer totalCount;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

}
