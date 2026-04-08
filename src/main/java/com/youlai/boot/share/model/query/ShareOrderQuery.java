package com.youlai.boot.share.model.query;

import com.youlai.boot.common.base.BaseQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 社群订单查询对象
 *
 * @author Ray.Hao
 * @since 2026/04/08
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "社群订单查询对象")
public class ShareOrderQuery extends BaseQuery {

    @Schema(description = "订单编号")
    private String orderNo;

    @Schema(description = "国家ID")
    private Integer countryId;

    @Schema(description = "分享ID")
    private Integer shareId;

    @Schema(description = "订单状态：0-待发送，1-发送中，2-已完成，3-发送失败，4-已取消")
    private Integer status;

}
