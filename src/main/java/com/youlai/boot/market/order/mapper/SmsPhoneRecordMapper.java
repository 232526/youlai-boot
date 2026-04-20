package com.youlai.boot.market.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.youlai.boot.market.order.model.entity.SmsPhoneRecord;
import com.youlai.boot.market.order.model.query.SmsPhoneRecordQuery;
import com.youlai.boot.market.order.model.vo.SmsPhoneRecordPageVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

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

    /**
     * 按订单编号统计不重复手机号数量
     *
     * @param orderNos 订单编号列表
     * @return 每个订单编号对应的不重复手机号数量 (order_no -> count)
     */
    @Select("<script>" +
            "SELECT order_no, COUNT(DISTINCT phone_number) AS cnt " +
            "FROM sms_phone_record " +
            "WHERE order_no IN " +
            "<foreach collection='orderNos' item='no' open='(' separator=',' close=')'>" +
            "#{no}" +
            "</foreach> " +
            "GROUP BY order_no" +
            "</script>")
    List<Map<String, Object>> countDistinctPhoneByOrderNos(@Param("orderNos") List<String> orderNos);

}
