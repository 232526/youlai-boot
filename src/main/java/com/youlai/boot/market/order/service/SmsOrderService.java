package com.youlai.boot.market.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.youlai.boot.market.order.model.entity.SmsMessageContent;
import com.youlai.boot.market.order.model.entity.SmsOrder;
import com.youlai.boot.market.order.model.entity.SmsPhoneRecord;
import com.youlai.boot.market.order.model.form.SmsOrderForm;
import com.youlai.boot.market.order.model.query.SmsOrderQuery;
import com.youlai.boot.market.order.model.query.SmsOrderStatisticsQuery;
import com.youlai.boot.market.order.model.vo.SmsOrderDetailVO;
import com.youlai.boot.market.order.model.vo.SmsOrderPageVO;
import com.youlai.boot.market.order.model.vo.SmsOrderStatisticsVO;

import java.util.List;

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

    /**
     * 获取短信订单统计分页列表
     *
     * @param queryParams 查询参数
     * @return 短信订单统计分页列表
     */
    Page<SmsOrderStatisticsVO> getSmsOrderStatisticsPage(SmsOrderStatisticsQuery queryParams);

    /**
     * 查询当前需要执行的待发送订单
     * <p>
     * 查询条件：状态为待发送且预约时间小于等于当前时间的订单
     *
     * @return 待执行订单列表
     */
    List<SmsOrder> getPendingOrdersToExecute();

    /**
     * 根据订单ID获取手机号记录列表
     *
     * @param orderId 订单ID
     * @return 手机号记录列表
     */
    List<SmsPhoneRecord> getPhoneRecordsByOrderId(Long orderId);

    /**
     * 根据订单ID获取短信内容列表
     *
     * @param orderId 订单ID
     * @return 短信内容列表
     */
    List<SmsMessageContent> getMessageContentsByOrderId(Long orderId);

}
