package com.youlai.boot.share.model.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 社群订单表单
 *
 * @author Ray.Hao
 * @since 2026/04/08
 */
@Data
@Schema(description = "社群订单表单")
public class ShareOrderForm {

    @Schema(description = "国家ID")
    @NotNull(message = "国家不能为空")
    private Integer countryId;

    @Schema(description = "分享ID")
    private String shareId;

    @Schema(description = "是否携带区号：0-否，1-是")
    @NotNull(message = "请选择是否携带区号")
    private Integer hasAreaCode;

    @Schema(description = "预约启动时间（时间戳，毫秒）")
    @NotNull(message = "请选择预约启动时间")
    private Long scheduledTime;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "短信内容列表")
    @NotEmpty(message = "短信内容不能为空")
    private List<String> messageContentList;

    @Schema(description = "手机号列表")
    @NotEmpty(message = "手机号不能为空")
    private List<String> phoneNumberList;

}
