package com.youlai.boot.interfaces.openapi.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 短信订单表单
 *
 * @author Ray.Hao
 * @since 2026/04/07
 */
@Data
@Schema(description = "短信订单表单")
public class SmsOrderFormApiModel {


    @Schema(description = "短信内容")
    @NotEmpty(message = "短信内容不能为空")
    private String content;

    @Schema(description = "手机号列表")
    @NotEmpty(message = "手机号不能为空")
    private List<String> phoneNumberList;


}
