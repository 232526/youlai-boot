package com.youlai.boot.market.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.youlai.boot.market.order.model.entity.UserTransaction;
import com.youlai.boot.market.order.model.query.UserTransactionQuery;
import com.youlai.boot.market.order.model.vo.UserTransactionPageVO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户交易流水 访问层
 *
 * @author Ray.Hao
 * @since 2026/04/10
 */
@Mapper
public interface UserTransactionMapper extends BaseMapper<UserTransaction> {

    /**
     * 用户交易流水分页列表
     *
     * @param page 分页参数
     * @param queryParams 查询参数
     * @param currentUserId 当前用户ID
     * @param isRoot 是否管理员
     * @return 用户交易流水分页列表
     */
    Page<UserTransactionPageVO> getUserTransactionPage(Page<UserTransactionPageVO> page, UserTransactionQuery queryParams, Long currentUserId, boolean isRoot);

}
