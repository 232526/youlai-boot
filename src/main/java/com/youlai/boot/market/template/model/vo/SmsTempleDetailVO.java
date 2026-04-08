package com.youlai.boot.market.template.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 短信模板详情视图对象
 *
 * @author Ray.Hao
 * @since 2026/04/08
 */
@Schema(description = "短信模板详情视图")
@Getter
@Setter
public class SmsTempleDetailVO {

    @Schema(description = "模板ID")
    private Long templateId;

    @Schema(description = "国家ID")
    private Long countriesId;

    @Schema(description = "短信内容文本")
    private String content;

    @Schema(description = "排序号")
    private Integer contentSort;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "创建人ID")
    private Long createBy;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @Schema(description = "修改人ID")
    private Long updateBy;

}
