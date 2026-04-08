package com.youlai.boot.market.template.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.youlai.boot.market.template.model.entity.SmsTemple;
import com.youlai.boot.market.template.model.query.SmsTempleQuery;
import com.youlai.boot.market.template.model.vo.SmsTemplePageVO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 短信模板 访问层
 *
 * @author Ray.Hao
 * @since 2026/04/08
 */
@Mapper
public interface SmsTempleMapper extends BaseMapper<SmsTemple> {

    /**
     * 短信模板分页列表
     *
     * @param page 分页参数
     * @param queryParams 查询参数
     * @param currentUserId 当前用户ID
     * @param isRoot 是否管理员
     * @return 短信模板分页列表
     */
    Page<SmsTemplePageVO> getSmsTemplePage(Page<SmsTemplePageVO> page, SmsTempleQuery queryParams, Long currentUserId, boolean isRoot);

}
