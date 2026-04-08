package com.youlai.boot.share.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.youlai.boot.share.model.entity.ShareType;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分享类型 访问层
 *
 * @author Ray.Hao
 * @since 2026/04/08
 */
@Mapper
public interface ShareTypeMapper extends BaseMapper<ShareType> {

}
