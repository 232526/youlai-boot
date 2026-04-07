package com.youlai.boot.market.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.youlai.boot.market.order.model.entity.SmsPhoneRecord;
import com.youlai.boot.market.order.model.query.SmsPhoneRecordQuery;
import com.youlai.boot.market.order.model.vo.SmsPhoneRecordPageVO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 手机号发送记录 访问层
 *
 * @author Ray.Hao
 * @since 2026/04/07
 */
@Mapper
public interface SmsPhoneRecordMapper extends BaseMapper<SmsPhoneRecord> {

    /**
     * 短信发送记录分页列表
     *
     * @param page 分页参数
     * @param queryParams 查询参数
     * @param currentUserId 当前用户ID
     * @param isRoot 是否管理员
     * @return 短信发送记录分页列表
     */
    Page<SmsPhoneRecordPageVO> getSmsPhoneRecordPage(Page<SmsPhoneRecordPageVO> page, SmsPhoneRecordQuery queryParams, Long currentUserId, boolean isRoot);

}
