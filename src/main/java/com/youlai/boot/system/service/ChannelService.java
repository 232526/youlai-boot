package com.youlai.boot.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.youlai.boot.system.model.entity.Channel;
import com.youlai.boot.system.model.query.ChannelQuery;
import com.youlai.boot.system.model.vo.ChannelAdminVO;
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
    boolean updateBalanceByCode(String channelCode, Double balance, Double gift, Double credit, String currency);

    /**
     * 分页查询渠道管理列表
     *
     * @param queryParams 查询参数
     * @return 渠道管理分页列表
     */
    IPage<ChannelAdminVO> pageAdmin(ChannelQuery queryParams);
}
