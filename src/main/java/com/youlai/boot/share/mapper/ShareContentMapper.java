package com.youlai.boot.share.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.youlai.boot.share.model.entity.ShareContent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 社群分享短信内容明细 访问层
 *
 * @author Ray.Hao
 * @since 2026/04/08
 */
@Mapper
public interface ShareContentMapper extends BaseMapper<ShareContent> {

}
