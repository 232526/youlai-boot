package com.youlai.boot.market.order.model.query;

import com.youlai.boot.common.base.BaseQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 短信发送记录查询对象
 *
 * @author Ray.Hao
 * @since 2026/04/07
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "短信发送记录查询对象")
public class SmsPhoneRecordQuery extends BaseQuery {

    @Schema(description = "订单编号")
    private String orderNo;

    @Schema(description = "国家ID")
    private Long countryId;

    @Schema(description = "手机号")
    private String phoneNumber;

    @Schema(description = "发送状态：0=待发送，1=发送成功，2=发送失败")
    private Integer sendStatus;

    @Schema(description = "开始日期")
    private String startDate;

    @Schema(description = "结束日期")
    private String endDate;

    @Schema(description = "创建时间")
    private String createTime;

    @Schema(description = "短信内容")
    private String content;

}
