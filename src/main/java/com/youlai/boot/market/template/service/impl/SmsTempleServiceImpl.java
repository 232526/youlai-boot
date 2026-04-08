package com.youlai.boot.market.template.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.youlai.boot.common.exception.BusinessException;
import com.youlai.boot.framework.security.util.SecurityUtils;
import com.youlai.boot.market.template.mapper.SmsTempleMapper;
import com.youlai.boot.market.template.model.entity.SmsTemple;
import com.youlai.boot.market.template.model.form.SmsTempleForm;
import com.youlai.boot.market.template.model.query.SmsTempleQuery;
import com.youlai.boot.market.template.model.vo.SmsTempleDetailVO;
import com.youlai.boot.market.template.model.vo.SmsTemplePageVO;
import com.youlai.boot.market.template.service.SmsTempleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 短信模板业务实现类
 *
 * @author Ray.Hao
 * @since 2026/04/08
 */
@Service
@RequiredArgsConstructor
public class SmsTempleServiceImpl extends ServiceImpl<SmsTempleMapper, SmsTemple> implements SmsTempleService {

    private final SmsTempleMapper smsTempleMapper;

    @Override
    public Page<SmsTemplePageVO> getSmsTemplePage(SmsTempleQuery queryParams) {
        // 数据权限：非管理员只能查看自己的模板
        Long currentUserId = SecurityUtils.getUserId();
        boolean isRoot = SecurityUtils.isRoot();

        Page<SmsTemplePageVO> page = new Page<>(queryParams.getPageNum(), queryParams.getPageSize());
        return smsTempleMapper.getSmsTemplePage(page, queryParams, currentUserId, isRoot);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveSmsTemple(SmsTempleForm formData) {
        SmsTemple smsTemple = new SmsTemple();
        smsTemple.setCountriesId(formData.getCountriesId());
        smsTemple.setContent(formData.getContent());
        smsTemple.setContentSort(formData.getContentSort() != null ? formData.getContentSort() : 0);

        this.save(smsTemple);
        return smsTemple.getTemplateId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateSmsTemple(Long templateId, SmsTempleForm formData) {
        SmsTemple smsTemple = this.getById(templateId);
        if (smsTemple == null) {
            throw new BusinessException("模板不存在");
        }

        smsTemple.setCountriesId(formData.getCountriesId());
        smsTemple.setContent(formData.getContent());
        smsTemple.setContentSort(formData.getContentSort() != null ? formData.getContentSort() : 0);

        return this.updateById(smsTemple);
    }

    @Override
    public SmsTempleDetailVO getTempleDetail(Long templateId) {
        SmsTemple smsTemple = this.getById(templateId);
        if (smsTemple == null) {
            throw new BusinessException("模板不存在");
        }

        SmsTempleDetailVO detailVO = new SmsTempleDetailVO();
        detailVO.setTemplateId(smsTemple.getTemplateId());
        detailVO.setCountriesId(smsTemple.getCountriesId());
        detailVO.setContent(smsTemple.getContent());
        detailVO.setContentSort(smsTemple.getContentSort());
        detailVO.setCreateTime(smsTemple.getCreateTime());
        detailVO.setCreateBy(smsTemple.getCreateBy());
        detailVO.setUpdateTime(smsTemple.getUpdateTime());
        detailVO.setUpdateBy(smsTemple.getUpdateBy());

        return detailVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteSmsTemple(Long templateId) {
        SmsTemple smsTemple = this.getById(templateId);
        if (smsTemple == null) {
            throw new BusinessException("模板不存在");
        }

        return this.removeById(templateId);
    }

}
