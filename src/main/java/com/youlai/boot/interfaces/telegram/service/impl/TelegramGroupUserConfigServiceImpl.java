package com.youlai.boot.interfaces.telegram.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.youlai.boot.interfaces.telegram.mapper.TelegramGroupUserConfigMapper;
import com.youlai.boot.interfaces.telegram.model.entity.TelegramGroupUserConfig;
import com.youlai.boot.interfaces.telegram.service.TelegramGroupUserConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Telegram 群组用户配置服务实现
 *
 * @author Lingma
 * @since 2026-04-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramGroupUserConfigServiceImpl extends ServiceImpl<TelegramGroupUserConfigMapper, TelegramGroupUserConfig> implements TelegramGroupUserConfigService {

    private final TelegramGroupUserConfigMapper userConfigMapper;

    @Override
    public TelegramGroupUserConfig getByGroupId(String groupId) {
        return userConfigMapper.selectOne(new LambdaQueryWrapper<TelegramGroupUserConfig>()
                .eq(TelegramGroupUserConfig::getGroupId, groupId));
    }

    @Override
    public boolean setQueryUsername(String groupId, String username, Long setByUserId, String setByUsername) {
        TelegramGroupUserConfig existing = getByGroupId(groupId);
        
        if (existing != null) {
            // 更新已有配置
            existing.setQueryUsername(username);
            existing.setSetByUserId(setByUserId);
            existing.setSetByUsername(setByUsername);
            existing.setUpdateTime(LocalDateTime.now());
            return updateById(existing);
        } else {
            // 新增配置
            TelegramGroupUserConfig config = new TelegramGroupUserConfig();
            config.setGroupId(groupId);
            config.setQueryUsername(username);
            config.setSetByUserId(setByUserId);
            config.setSetByUsername(setByUsername);
            config.setCreateTime(LocalDateTime.now());
            config.setUpdateTime(LocalDateTime.now());
            return save(config);
        }
    }

    @Override
    public boolean removeByGroupId(String groupId) {
        return remove(new LambdaQueryWrapper<TelegramGroupUserConfig>()
                .eq(TelegramGroupUserConfig::getGroupId, groupId));
    }
}
