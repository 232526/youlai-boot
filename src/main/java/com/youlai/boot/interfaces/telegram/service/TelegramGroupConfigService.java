package com.youlai.boot.interfaces.telegram.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.youlai.boot.interfaces.telegram.model.entity.TelegramGroupConfig;

/**
 * Telegram 群组配置服务接口
 *
 * @author Lingma
 * @since 2026-04-18
 */
public interface TelegramGroupConfigService extends IService<TelegramGroupConfig> {

    /**
     * 根据群组ID获取配置
     *
     * @param groupId 群组ID
     * @return 群组配置
     */
    TelegramGroupConfig getByGroupId(String groupId);

    /**
     * 注册或更新群组配置
     *
     * @param groupId   群组ID
     * @param groupName 群组名称
     * @param groupType 群组类型
     * @return 是否成功
     */
    boolean registerOrUpdateGroup(String groupId, String groupName, String groupType);
}
