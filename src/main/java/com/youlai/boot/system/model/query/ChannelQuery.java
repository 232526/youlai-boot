package com.youlai.boot.system.model.query;

import com.youlai.boot.common.base.BaseQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 渠道查询对象
 *
 * @author Theo
 * @since 2026-04-13
 */
@Getter
@Setter
@Schema(description = "渠道查询")
public class ChannelQuery extends BaseQuery {

    @Schema(description = "关键字(名称/昵称)")
    private String keywords;

    @Schema(description = "渠道类型 sms / ws")
    private String type;
}
