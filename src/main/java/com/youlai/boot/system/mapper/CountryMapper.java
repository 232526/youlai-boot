package com.youlai.boot.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.youlai.boot.system.model.entity.Country;
import org.apache.ibatis.annotations.Mapper;

/**
 * 国家 访问层
 *
 * @author Ray.Hao
 * @since 2026/04/07
 */
@Mapper
public interface CountryMapper extends BaseMapper<Country> {

}
