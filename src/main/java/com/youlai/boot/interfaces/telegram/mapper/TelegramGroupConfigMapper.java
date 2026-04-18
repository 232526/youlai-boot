package com.youlai.boot.interfaces.telegram.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.youlai.boot.interfaces.telegram.model.entity.TelegramGroupConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * Telegram 群组配置 Mapper
 *
 * @author Lingma
 * @since 2026-04-18
 */
@Mapper
public interface TelegramGroupConfigMapper extends BaseMapper<TelegramGroupConfig> {
}
