package com.youlai.boot.share.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.youlai.boot.share.model.entity.ShareOrder;
import com.youlai.boot.share.model.form.ShareOrderForm;
import com.youlai.boot.share.model.query.ShareOrderQuery;
import com.youlai.boot.share.model.vo.ShareOrderDetailVO;
import com.youlai.boot.share.model.vo.ShareOrderPageVO;

/**
 * 社群订单业务接口
 *
 * @author Ray.Hao
 * @since 2026/04/08
 */
public interface ShareOrderService extends IService<ShareOrder> {

    /**
     * 获取社群订单分页列表
     *
     * @param queryParams 查询参数
     * @return 社群订单分页列表
     */
    Page<ShareOrderPageVO> getShareOrderPage(ShareOrderQuery queryParams);

    /**
     * 新增社群订单
     *
     * @param formData 订单表单
     * @return 订单ID
     */
    String createOrder(ShareOrderForm formData);

    /**
     * 获取订单详情
     *
     * @param id 订单ID
     * @return 订单详情
     */
    ShareOrderDetailVO getOrderDetail(Long id);

    /**
     * 取消订单
     *
     * @param id 订单ID
     * @return 是否成功
     */
    boolean cancelOrder(Long id);

}
