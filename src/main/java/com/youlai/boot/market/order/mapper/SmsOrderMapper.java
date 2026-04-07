package com.youlai.boot.market.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.youlai.boot.market.order.model.entity.SmsOrder;
import com.youlai.boot.market.order.model.query.SmsOrderQuery;
import com.youlai.boot.market.order.model.vo.SmsOrderPageVO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 短信订单 访问层
 *
 * @author Ray.Hao
 * @since 2026/04/07
 */
@Mapper
public interface SmsOrderMapper extends BaseMapper<SmsOrder> {

    /**
     * 短信订单分页列表
     *
     * @param page 分页参数
     * @param queryParams 查询参数
     * @param currentUserId 当前用户ID
     * @param isRoot 是否管理员
     * @return 短信订单分页列表
     */
    Page<SmsOrderPageVO> getSmsOrderPage(Page<SmsOrderPageVO> page, SmsOrderQuery queryParams, Long currentUserId, boolean isRoot);

}
