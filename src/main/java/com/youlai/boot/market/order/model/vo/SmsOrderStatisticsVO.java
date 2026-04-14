package com.youlai.boot.market.order.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 短信订单统计视图对象
 *
 * @author Ray.Hao
 * @since 2026/04/08
 */
@Schema(description = "短信订单统计视图")
@Getter
@Setter
public class SmsOrderStatisticsVO {

    @Schema(description = "订单ID")
    private Long id;

    @Schema(description = "订单编号")
    private String orderNo;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户名称")
    private String userName;

    @Schema(description = "短信条数")
    private Integer totalCount=0;

    @Schema(description = "成功发送条数")
    private Integer successCount=0;

    @Schema(description = "已上报短信条数")
    private Integer reportCount=0;

    @Schema(description = "已付费短信条数")
    private Integer paidCount=0;

    @Schema(description = "失败条数")
    private Integer failCount=0;

    @Schema(description = "取消条数")
    private Integer cancelCount=0;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "状态描述")
    private String statusDesc;

}
