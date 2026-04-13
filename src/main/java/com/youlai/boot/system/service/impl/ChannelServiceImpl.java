package com.youlai.boot.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.youlai.boot.system.converter.ChannelConverter;
import com.youlai.boot.system.mapper.ChannelMapper;
import com.youlai.boot.system.model.entity.Channel;
import com.youlai.boot.system.model.query.ChannelQuery;
import com.youlai.boot.system.model.vo.ChannelVO;
import com.youlai.boot.system.service.ChannelService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 渠道Service接口实现
 *
 * @author Theo
 * @since 2026-04-13
 */
@Service
@RequiredArgsConstructor
public class ChannelServiceImpl extends ServiceImpl<ChannelMapper, Channel> implements ChannelService {

    private final ChannelConverter channelConverter;

    /**
     * 根据类型分页查询渠道
     *
     * @param type 渠道类型 sms/ws
     * @param queryParams 查询参数
     * @return 渠道分页列表
     */
    @Override
    public IPage<ChannelVO> pageByType(String type, ChannelQuery queryParams) {
        Page<Channel> page = new Page<>(queryParams.getPageNum(), queryParams.getPageSize());
        
        LambdaQueryWrapper<Channel> query = new LambdaQueryWrapper<Channel>()
                .eq(Channel::getType, type)
                .and(StringUtils.isNotBlank(queryParams.getKeywords()),
                    q -> q.like(Channel::getName, queryParams.getKeywords())
                        .or()
                        .like(Channel::getNickname, queryParams.getKeywords())
                )
                .orderByDesc(Channel::getId);
        
        Page<Channel> pageList = this.page(page, query);
        return channelConverter.toPageVo(pageList);
    }
}
