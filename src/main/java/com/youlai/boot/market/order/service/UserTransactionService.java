package com.youlai.boot.market.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.youlai.boot.market.order.model.entity.UserTransaction;
import com.youlai.boot.market.order.model.query.UserTransactionQuery;
import com.youlai.boot.market.order.model.vo.UserTransactionPageVO;

/**
 * 用户交易流水业务接口
 *
 * @author Ray.Hao
 * @since 2026/04/10
 */
public interface UserTransactionService extends IService<UserTransaction> {

    /**
     * 用户交易流水分页列表
     *
     * @param queryParams 查询参数
     * @return 用户交易流水分页列表
     */
    Page<UserTransactionPageVO> getUserTransactionPage(UserTransactionQuery queryParams);

}
