package com.youlai.boot.share.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 分享类型视图对象
 *
 * @author Ray.Hao
 * @since 2026/04/08
 */
@Schema(description = "分享类型信息")
@Getter
@Setter
public class ShareTypeVO {

    @Schema(description = "主键ID")
    private Integer id;

    @Schema(description = "第三方名稱")
    private String name;

    @Schema(description = "可否使用：1=可用，0=不可用")
    private Integer isEnabled;

}
