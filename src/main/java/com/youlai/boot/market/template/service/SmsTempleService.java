package com.youlai.boot.market.template.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.youlai.boot.market.template.model.entity.SmsTemple;
import com.youlai.boot.market.template.model.form.SmsTempleForm;
import com.youlai.boot.market.template.model.query.SmsTempleQuery;
import com.youlai.boot.market.template.model.vo.SmsTempleDetailVO;
import com.youlai.boot.market.template.model.vo.SmsTemplePageVO;

/**
 * 短信模板业务接口
 *
 * @author Ray.Hao
 * @since 2026/04/08
 */
public interface SmsTempleService extends IService<SmsTemple> {

    /**
     * 获取短信模板分页列表
     *
     * @param queryParams 查询参数
     * @return 短信模板分页列表
     */
    Page<SmsTemplePageVO> getSmsTemplePage(SmsTempleQuery queryParams);

    /**
     * 新增短信模板
     *
     * @param formData 模板表单
     * @return 模板ID
     */
    Long saveSmsTemple(SmsTempleForm formData);

    /**
     * 更新短信模板
     *
     * @param templateId 模板ID
     * @param formData 模板表单
     * @return 是否成功
     */
    boolean updateSmsTemple(Long templateId, SmsTempleForm formData);

    /**
     * 获取模板详情
     *
     * @param templateId 模板ID
     * @return 模板详情
     */
    SmsTempleDetailVO getTempleDetail(Long templateId);

    /**
     * 删除短信模板
     *
     * @param templateId 模板ID
     * @return 是否成功
     */
    boolean deleteSmsTemple(Long templateId);

}
