package com.youlai.boot.system.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 渠道视图对象
 *
 * @author Theo
 * @since 2026-04-13
 */
@Data
@Builder
@Schema(description = "渠道VO")
public class ChannelAdminVO implements Serializable {

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


    /**
     * 余额
     */
    @Schema(description = "余额")
    private double balance;

    /**
     * 赠送余额
     */
    @Schema(description = "赠送余额")
    private double gift;


    /**
     * 信用额度
     */
    @Schema(description = "信用额度")
    private double credit;


    /**
     * 币总
     */
    @Schema(description = "币总")
    private String coin;


    /**
     * 更新时间
     */
    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
