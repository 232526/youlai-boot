package com.youlai.boot.market.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.youlai.boot.market.order.model.entity.SmsMessageContent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 短信内容 访问层
 *
 * @author Ray.Hao
 * @since 2026/04/07
 */
@Mapper
public interface SmsMessageContentMapper extends BaseMapper<SmsMessageContent> {

}
