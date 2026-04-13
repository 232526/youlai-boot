package com.youlai.boot.system.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.youlai.boot.system.model.entity.Channel;
import com.youlai.boot.system.model.vo.ChannelVO;
import org.mapstruct.Mapper;

/**
 * 渠道对象转换器
 *
 * @author Theo
 * @since 2026-04-13
 */
@Mapper(componentModel = "spring")
public interface ChannelConverter {

    Page<ChannelVO> toPageVo(Page<Channel> page);

    ChannelVO toVo(Channel entity);
}
