package com.youlai.boot.market.template.model.query;

import com.youlai.boot.common.base.BaseQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 短信模板查询对象
 *
 * @author Ray.Hao
 * @since 2026/04/08
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "短信模板查询对象")
public class SmsTempleQuery extends BaseQuery {

    @Schema(description = "国家ID")
    private Long countriesId;

    @Schema(description = "短信内容（模糊查询）")
    private String content;

}
