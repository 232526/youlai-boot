package com.youlai.boot.system.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Set;

/**
 * 当前登录用户视图对象
 *
 * @author Ray.Hao
 * @since 2022/1/14
 */
@Schema(description ="当前登录用户视图对象")
@Data
public class CurrentUserVO {

    @Schema(description="用户ID")
    private Long userId;

    @Schema(description="用户名")
    private String username;

    @Schema(description="用户昵称")
    private String nickname;

    @Schema(description="头像地址")
    private String avatar;

    @Schema(description = "性别(1-男 2-女 0-保密)")
    private Integer gender;

    @Schema(description = "部门名称")
    private String deptName;

    @Schema(description="用户角色编码集合")
    private Set<String> roles;

    @Schema(description = "用户角色名称集合")
    private Set<String> roleNames;

    @Schema(description="用户权限标识集合")
    private Set<String> perms;

}