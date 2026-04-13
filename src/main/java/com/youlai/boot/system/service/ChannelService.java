package com.youlai.boot.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.youlai.boot.system.model.entity.Channel;
import com.youlai.boot.system.model.query.ChannelQuery;
import com.youlai.boot.system.model.vo.ChannelVO;

/**
 * 渠道Service接口
 *
 * @author Theo
 * @since 2026-04-13
 */
public interface ChannelService extends IService<Channel> {

    /**
     * 根据类型分页查询渠道
     *
     * @param type 渠道类型 sms/ws
     * @param queryParams 查询参数
     * @return 渠道分页列表
     */
    IPage<ChannelVO> pageByType(String type, ChannelQuery queryParams);
}
