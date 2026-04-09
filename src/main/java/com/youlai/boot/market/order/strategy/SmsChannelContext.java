package com.youlai.boot.market.order.strategy;

import com.youlai.boot.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 短信渠道上下文管理器
 * <p>
 * 管理所有短信渠道策略，支持动态切换渠道
 */
@Component
@RequiredArgsConstructor
public class SmsChannelContext {

    private final Map<String, SmsChannelStrategy> strategyMap = new ConcurrentHashMap<>();

    /**
     * 自动注入所有的短信渠道策略
     */
    public SmsChannelContext(List<SmsChannelStrategy> strategies) {
        for (SmsChannelStrategy strategy : strategies) {
            strategyMap.put(strategy.getChannelCode(), strategy);
        }
    }

    /**
     * 根据渠道标识获取对应的策略
     *
     * @param channelCode 渠道标识
     * @return 短信渠道策略
     */
    public SmsChannelStrategy getStrategy(String channelCode) {
        SmsChannelStrategy strategy = strategyMap.get(channelCode);
        if (strategy == null) {
            throw new BusinessException("不支持的短信渠道: " + channelCode);
        }
        return strategy;
    }

    /**
     * 获取所有可用的渠道标识
     *
     * @return 渠道标识列表
     */
    public List<String> getAvailableChannels() {
        return strategyMap.keySet().stream().toList();
    }
}
