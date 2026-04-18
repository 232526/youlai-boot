package com.youlai.boot.interfaces.telegram.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.youlai.boot.interfaces.telegram.model.entity.TelegramGroupUserConfig;

/**
 * Telegram 群组用户配置服务接口
 *
 * @author Lingma
 * @since 2026-04-18
 */
public interface TelegramGroupUserConfigService extends IService<TelegramGroupUserConfig> {

    /**
     * 根据群组ID获取配置
     *
     * @param groupId 群组ID
     * @return 群组用户配置
     */
    TelegramGroupUserConfig getByGroupId(String groupId);

    /**
     * 设置群组的查询用户名
     *
     * @param groupId       群组ID
     * @param username      要查询的用户名
     * @param setByUserId   设置者的Telegram用户ID
     * @param setByUsername 设置者的Telegram用户名
     * @return 是否成功
     */
    boolean setQueryUsername(String groupId, String username, Long setByUserId, String setByUsername);

    /**
     * 删除群组的查询用户名配置
     *
     * @param groupId 群组ID
     * @return 是否成功
     */
    boolean removeByGroupId(String groupId);
}
