package com.youlai.boot.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.youlai.boot.system.model.entity.Channel;
import org.apache.ibatis.annotations.Mapper;

/**
 * 渠道访问层
 *
 * @author Theo
 * @since 2026-04-13
 */
@Mapper
public interface ChannelMapper extends BaseMapper<Channel> {

}
