package com.youlai.boot.market.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.youlai.boot.market.order.model.query.SmsPhoneRecordQuery;
import com.youlai.boot.market.order.model.vo.SmsPhoneRecordPageVO;

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

}
