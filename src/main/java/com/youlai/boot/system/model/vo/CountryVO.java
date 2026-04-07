package com.youlai.boot.system.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 国家视图对象
 *
 * @author Ray.Hao
 * @since 2026/04/07
 */
@Schema(description = "国家信息")
@Getter
@Setter
public class CountryVO {

    @Schema(description = "国家ID")
    private Integer id;

    @Schema(description = "国家名称")
    private String name;

}
