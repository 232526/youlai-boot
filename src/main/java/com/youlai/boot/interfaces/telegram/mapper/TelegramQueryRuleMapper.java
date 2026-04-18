package com.youlai.boot.interfaces.telegram.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.youlai.boot.interfaces.telegram.model.entity.TelegramQueryRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * Telegram 查询规则 Mapper
 *
 * @author Lingma
 * @since 2026-04-18
 */
@Mapper
public interface TelegramQueryRuleMapper extends BaseMapper<TelegramQueryRule> {
}
