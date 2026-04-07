package com.youlai.boot.market.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.youlai.boot.market.order.model.entity.SmsOrder;
import com.youlai.boot.market.order.model.form.SmsOrderForm;
import com.youlai.boot.market.order.model.query.SmsOrderQuery;
import com.youlai.boot.market.order.model.vo.SmsOrderDetailVO;
import com.youlai.boot.market.order.model.vo.SmsOrderPageVO;

/**
 * 短信订单业务接口
 *
 * @author Ray.Hao
 * @since 2026/04/07
 */
public interface SmsOrderService extends IService<SmsOrder> {

    /**
     * 获取短信订单分页列表
     *
     * @param queryParams 查询参数
     * @return 短信订单分页列表
     */
    Page<SmsOrderPageVO> getSmsOrderPage(SmsOrderQuery queryParams);

    /**
     * 新增短信订单
     *
     * @param formData 订单表单
     * @return 订单ID
     */
    Long createOrder(SmsOrderForm formData);

    /**
     * 获取订单详情
     *
     * @param id 订单ID
     * @return 订单详情
     */
    SmsOrderDetailVO getOrderDetail(Long id);

    /**
     * 取消订单
     *
     * @param id 订单ID
     * @return 是否成功
     */
    boolean cancelOrder(Long id);

}
