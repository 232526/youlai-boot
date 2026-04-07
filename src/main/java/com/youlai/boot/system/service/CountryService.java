package com.youlai.boot.system.service;

import com.youlai.boot.system.model.vo.CountryVO;

import java.util.List;

/**
 * 国家业务接口
 *
 * @author Ray.Hao
 * @since 2026/04/07
 */
public interface CountryService {

    /**
     * 获取国家列表
     *
     * @return 国家列表
     */
    List<CountryVO> getCountryList();

}
