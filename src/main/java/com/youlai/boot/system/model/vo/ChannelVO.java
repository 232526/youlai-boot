package com.youlai.boot.system.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 渠道视图对象
 *
 * @author Theo
 * @since 2026-04-13
 */
@Data
@Builder
@Schema(description = "渠道VO")
public class ChannelVO implements Serializable {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "渠道类型 sms / ws")
    private String type;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "备注")
    private String remark;
}
