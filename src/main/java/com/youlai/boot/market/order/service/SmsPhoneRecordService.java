package com.youlai.boot.market.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.youlai.boot.market.order.model.query.SmsPhoneRecordQuery;
import com.youlai.boot.market.order.model.vo.SmsPhoneRecordPageVO;
import com.youlai.boot.market.order.strategy.SmsChannelStrategy;

/**
 * 短信发送记录业务接口
 *
 * @author Ray.Hao
 * @since 2026/04/07
 */
public interface SmsPhoneRecordService {

    /**
     * 短信发送记录分页列表
     *
     * @param queryParams 查询参数
     * @return 短信发送记录分页列表
     */
    Page<SmsPhoneRecordPageVO> getSmsPhoneRecordPage(SmsPhoneRecordQuery queryParams);

    /**
     * 保存短信发送结果
     *
     * @param orderNo 订单编号
     * @param sendResult 发送结果
     * @param channelCode 渠道代码
     */
    void saveSendResult(Long orderNo, SmsChannelStrategy.SmsSendResult sendResult, String channelCode);

    /**
     * 更新短信状态报告
     *
     * @param reportResult 状态报告结果
     */
    void updateReportResult(SmsChannelStrategy.SmsReportResult reportResult);

    /**
     * 查询并更新指定订单的状态报告
     *
     * @param orderNo 订单编号
     * @param channelCode 渠道代码
     */
    void queryAndUpdateReport(Long orderNo, String channelCode);

}
