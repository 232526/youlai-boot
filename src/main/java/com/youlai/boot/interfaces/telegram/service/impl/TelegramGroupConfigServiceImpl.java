package com.youlai.boot.interfaces.telegram.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.youlai.boot.interfaces.telegram.mapper.TelegramGroupConfigMapper;
import com.youlai.boot.interfaces.telegram.model.entity.TelegramGroupConfig;
import com.youlai.boot.interfaces.telegram.service.TelegramGroupConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Telegram 群组配置服务实现
 *
 * @author Lingma
 * @since 2026-04-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramGroupConfigServiceImpl extends ServiceImpl<TelegramGroupConfigMapper, TelegramGroupConfig> implements TelegramGroupConfigService {

    private final TelegramGroupConfigMapper groupConfigMapper;

    @Override
    public TelegramGroupConfig getByGroupId(String groupId) {
        return groupConfigMapper.selectOne(new LambdaQueryWrapper<TelegramGroupConfig>()
                .eq(TelegramGroupConfig::getGroupId, groupId));
    }

    @Override
    public boolean registerOrUpdateGroup(String groupId, String groupName, String groupType) {
        TelegramGroupConfig existing = getByGroupId(groupId);
        
        if (existing != null) {
            // 更新已有配置
            existing.setGroupName(groupName);
            existing.setGroupType(groupType);
            existing.setUpdateTime(LocalDateTime.now());
            return updateById(existing);
        } else {
            // 新增配置
            TelegramGroupConfig config = new TelegramGroupConfig();
            config.setGroupId(groupId);
            config.setGroupName(groupName);
            config.setGroupType(groupType);
            config.setEnabled(1);
            config.setQueryMode("all");
            config.setCreateTime(LocalDateTime.now());
            config.setUpdateTime(LocalDateTime.now());
            return save(config);
        }
    }
}
