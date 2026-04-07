package com.youlai.boot.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.youlai.boot.system.mapper.CountryMapper;
import com.youlai.boot.system.model.entity.Country;
import com.youlai.boot.system.model.vo.CountryVO;
import com.youlai.boot.system.service.CountryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 国家业务实现类
 *
 * @author Ray.Hao
 * @since 2026/04/07
 */
@Service
@RequiredArgsConstructor
public class CountryServiceImpl implements CountryService {

    private final CountryMapper countryMapper;

    @Override
    public List<CountryVO> getCountryList() {
        LambdaQueryWrapper<Country> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Country::getIsEnabled, 1)
                .orderByAsc(Country::getName);
        
        List<Country> countries = countryMapper.selectList(wrapper);
        
        return countries.stream()
                .map(country -> {
                    CountryVO vo = new CountryVO();
                    vo.setId(country.getId());
                    vo.setName(country.getName());
                    return vo;
                })
                .toList();
    }

}
