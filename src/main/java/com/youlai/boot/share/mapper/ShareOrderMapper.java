package com.youlai.boot.share.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.youlai.boot.share.model.entity.ShareOrder;
import com.youlai.boot.share.model.query.ShareOrderQuery;
import com.youlai.boot.share.model.vo.ShareOrderPageVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 社群订单 访问层
 *
 * @author Ray.Hao
 * @since 2026/04/08
 */
@Mapper
public interface ShareOrderMapper extends BaseMapper<ShareOrder> {

    /**
     * 获取社群订单分页列表
     *
     * @param page 分页参数
     * @param queryParams 查询参数
     * @param currentUserId 当前用户ID
     * @param isRoot 是否为管理员
     * @return 社群订单分页列表
     */
    Page<ShareOrderPageVO> getShareOrderPage(
        Page<ShareOrderPageVO> page,
        @Param("queryParams") ShareOrderQuery queryParams,
        @Param("currentUserId") Long currentUserId,
        @Param("isRoot") boolean isRoot
    );

}
