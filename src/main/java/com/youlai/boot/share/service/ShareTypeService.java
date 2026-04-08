package com.youlai.boot.share.service;

import com.youlai.boot.share.model.vo.ShareTypeVO;

import java.util.List;

/**
 * 分享类型业务接口
 *
 * @author Ray.Hao
 * @since 2026/04/08
 */
public interface ShareTypeService {

    /**
     * 获取分享类型列表
     *
     * @return 分享类型列表
     */
    List<ShareTypeVO> getShareTypeList();

}
