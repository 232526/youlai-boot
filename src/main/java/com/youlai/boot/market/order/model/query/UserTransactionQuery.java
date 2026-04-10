package com.youlai.boot.market.order.model.query;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.youlai.boot.common.base.BaseQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 用户交易流水查询对象
 *
 * @author Ray.Hao
 * @since 2026/04/10
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户交易流水查询对象")
public class UserTransactionQuery extends BaseQuery {

    @Schema(description = "流水号")
    private String transNo;

    @Schema(description = "交易类型 1=收入 2=支出")
    private Integer transType;

    @Schema(description = "业务类型")
    private String bizType;

    @Schema(description = "关联订单号")
    private String relatedOrderNo;

    @Schema(description = "状态 0=作废 1=成功 2=处理中")
    private Integer status;

    @Schema(description = "开始日期")
    private String startDate;

    @Schema(description = "结束日期")
    private String endDate;

    @Schema(description = "用户名（模糊搜索）")
    private String username;

    @JsonIgnore
    @Schema(hidden = true)
    private List<Long> userIds;

}
