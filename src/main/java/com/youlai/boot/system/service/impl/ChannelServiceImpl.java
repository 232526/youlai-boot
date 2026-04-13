package com.youlai.boot.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.youlai.boot.system.converter.ChannelConverter;
import com.youlai.boot.system.mapper.ChannelMapper;
import com.youlai.boot.system.model.entity.Channel;
import com.youlai.boot.system.model.query.ChannelQuery;
import com.youlai.boot.system.model.vo.ChannelAdminVO;
import com.youlai.boot.system.model.vo.ChannelVO;
import com.youlai.boot.system.service.ChannelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 渠道Service接口实现
 *
 * @author Theo
 * @since 2026-04-13
 */
@Slf4j
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

    /**
     * 根据渠道代码更新余额
     *
     * @param channelCode 渠道代码（如 ONBUKA）
     * @param balance 余额
     * @param gift 赠送余额
     * @param credit 信用额度
     * @param currency 币种
     * @return 是否更新成功
     */
    @Override
    public boolean updateBalanceByCode(String channelCode, Double balance, Double gift, Double credit, String currency) {
        if (StringUtils.isBlank(channelCode)) {
            log.warn("渠道代码为空，无法更新余额");
            return false;
        }

        // 根据渠道代码查询渠道记录（假设 nickname 字段存储渠道代码）
        LambdaQueryWrapper<Channel> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Channel::getNickname, channelCode)
            .or()
            .eq(Channel::getName, channelCode);

        Channel channel = this.getOne(queryWrapper);
        if (channel == null) {
            log.warn("未找到渠道代码为 {} 的渠道记录", channelCode);
            return false;
        }

        // 更新余额、赠送余额、信用额度和币种
        LambdaUpdateWrapper<Channel> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Channel::getId, channel.getId())
            .set(balance != null, Channel::getBalance, balance)
            .set(gift != null, Channel::getGift, gift)
            .set(credit != null, Channel::getCredit, credit)
            .set(StringUtils.isNotBlank(currency), Channel::getCoin, currency)
            .set(Channel::getUpdateTime, LocalDateTime.now());

        boolean result = this.update(updateWrapper);
        if (result) {
            log.info("渠道 {} 余额更新成功 - 余额: {}, 赠送: {}, 信用: {}, 币种: {}",
                channelCode, balance, gift, credit, currency);
        } else {
            log.error("渠道 {} 余额更新失败", channelCode);
        }

        return result;
    }

    /**
     * 分页查询渠道管理列表
     *
     * @param queryParams 查询参数
     * @return 渠道管理分页列表
     */
    @Override
    public IPage<ChannelAdminVO> pageAdmin(ChannelQuery queryParams) {
        Page<Channel> page = new Page<>(queryParams.getPageNum(), queryParams.getPageSize());
        
        LambdaQueryWrapper<Channel> query = new LambdaQueryWrapper<Channel>()
                .and(StringUtils.isNotBlank(queryParams.getKeywords()),
                    q -> q.like(Channel::getName, queryParams.getKeywords())
                        .or()
                        .like(Channel::getNickname, queryParams.getKeywords())
                )
                .orderByDesc(Channel::getId);
        
        // 如果指定了类型，则添加类型过滤
        if (StringUtils.isNotBlank(queryParams.getType())) {
            query.eq(Channel::getType, queryParams.getType());
        }
        
        Page<Channel> pageList = this.page(page, query);
        return channelConverter.toPageAdminVo(pageList);
    }
}
