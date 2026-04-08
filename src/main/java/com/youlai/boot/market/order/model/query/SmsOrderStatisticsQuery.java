package com.youlai.boot.market.order.model.query;

import com.youlai.boot.common.base.BaseQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 短信订单统计查询对象
 *
 * @author Ray.Hao
 * @since 2026/04/08
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "短信订单统计查询对象")
public class SmsOrderStatisticsQuery extends BaseQuery {

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "国家ID")
    private Integer countryId;

    @Schema(description = "开始时间")
    private String startTime;

    @Schema(description = "结束时间")
    private String endTime;

}
