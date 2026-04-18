package com.youlai.boot.interfaces.telegram.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.youlai.boot.interfaces.telegram.mapper.TelegramQueryRuleMapper;
import com.youlai.boot.interfaces.telegram.model.entity.TelegramQueryRule;
import com.youlai.boot.interfaces.telegram.service.TelegramQueryRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Telegram 查询规则服务实现
 *
 * @author Lingma
 * @since 2026-04-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramQueryRuleServiceImpl extends ServiceImpl<TelegramQueryRuleMapper, TelegramQueryRule> implements TelegramQueryRuleService {

    private final TelegramQueryRuleMapper queryRuleMapper;

    @Override
    public List<TelegramQueryRule> getRulesByGroupAndTrigger(String groupId, String triggerWord) {
        if (StrUtil.isBlank(triggerWord)) {
            return List.of();
        }

        return queryRuleMapper.selectList(new LambdaQueryWrapper<TelegramQueryRule>()
                .eq(TelegramQueryRule::getGroupId, groupId)
                .eq(TelegramQueryRule::getEnabled, 1)
                .like(TelegramQueryRule::getTriggerWord, triggerWord)
                .orderByAsc(TelegramQueryRule::getSortOrder));
    }

    @Override
    public List<TelegramQueryRule> getEnabledRulesByGroup(String groupId) {
        return queryRuleMapper.selectList(new LambdaQueryWrapper<TelegramQueryRule>()
                .eq(TelegramQueryRule::getGroupId, groupId)
                .eq(TelegramQueryRule::getEnabled, 1)
                .orderByAsc(TelegramQueryRule::getSortOrder));
    }
}
