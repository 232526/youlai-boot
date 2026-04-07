package com.youlai.boot.shared.constant;

/**
 * 系统常量
 *
 * @author Ray.Hao
 * @since 1.0.0
 */
public interface SystemConstants {

    /**
     * 根节点ID
     */
    Long ROOT_NODE_ID = 0L;

    /**
     * 系统默认密码
     */
    String DEFAULT_PASSWORD = "123456";

    /**
     * 超级管理员角色编码
     */
    String ROOT_ROLE_CODE = "ROOT";

    /**
     * 管理员
     */
    String ADMIN_ROLE_CODE = "ADMIN";


    /**
     * 系统配置 IP的QPS限流的KEY
     */
    String SYSTEM_CONFIG_IP_QPS_LIMIT_KEY = "IP_QPS_THRESHOLD_LIMIT";

}
