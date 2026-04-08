package com.youlai.boot.market.template.model.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 短信模板表单
 *
 * @author Ray.Hao
 * @since 2026/04/08
 */
@Data
@Schema(description = "短信模板表单")
public class SmsTempleForm {

    @Schema(description = "模板ID（更新时必填）")
    private Long templateId;

    @Schema(description = "国家ID")
    @NotNull(message = "国家不能为空")
    private Long countriesId;

    @Schema(description = "短信内容文本")
    @NotBlank(message = "短信内容不能为空")
    private String content;

    @Schema(description = "排序号")
    private Integer contentSort;

}
