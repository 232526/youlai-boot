package com.youlai.boot.interfaces.telegram.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.youlai.boot.interfaces.telegram.model.entity.TelegramQueryRule;

import java.util.List;

/**
 * Telegram 查询规则服务接口
 *
 * @author Lingma
 * @since 2026-04-18
 */
public interface TelegramQueryRuleService extends IService<TelegramQueryRule> {

    /**
     * 根据群组ID和触发词获取匹配的规则
     *
     * @param groupId     群组ID
     * @param triggerWord 触发词
     * @return 查询规则列表
     */
    List<TelegramQueryRule> getRulesByGroupAndTrigger(String groupId, String triggerWord);

    /**
     * 根据群组ID获取所有启用的规则
     *
     * @param groupId 群组ID
     * @return 规则列表
     */
    List<TelegramQueryRule> getEnabledRulesByGroup(String groupId);
}
