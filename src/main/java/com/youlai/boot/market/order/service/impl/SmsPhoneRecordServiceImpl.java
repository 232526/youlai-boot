package com.youlai.boot.market.order.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.youlai.boot.framework.security.util.SecurityUtils;
import com.youlai.boot.market.order.mapper.SmsPhoneRecordMapper;
import com.youlai.boot.market.order.model.query.SmsPhoneRecordQuery;
import com.youlai.boot.market.order.model.vo.SmsPhoneRecordPageVO;
import com.youlai.boot.market.order.service.SmsPhoneRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 短信发送记录业务实现类
 *
 * @author Ray.Hao
 * @since 2026/04/07
 */
@Service
@RequiredArgsConstructor
public class SmsPhoneRecordServiceImpl implements SmsPhoneRecordService {

    private final SmsPhoneRecordMapper smsPhoneRecordMapper;

    @Override
    public Page<SmsPhoneRecordPageVO> getSmsPhoneRecordPage(SmsPhoneRecordQuery queryParams) {
        // 数据权限：非管理员只能查看自己的记录
        Long currentUserId = SecurityUtils.getUserId();
        boolean isRoot = SecurityUtils.isRoot();
        
        Page<SmsPhoneRecordPageVO> page = new Page<>(queryParams.getPageNum(), queryParams.getPageSize());
        return smsPhoneRecordMapper.getSmsPhoneRecordPage(page, queryParams, currentUserId, isRoot);
    }

}
