package com.youlai.boot.share.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.youlai.boot.share.mapper.ShareTypeMapper;
import com.youlai.boot.share.model.entity.ShareType;
import com.youlai.boot.share.model.vo.ShareTypeVO;
import com.youlai.boot.share.service.ShareTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 分享类型业务实现类
 *
 * @author Ray.Hao
 * @since 2026/04/08
 */
@Service
@RequiredArgsConstructor
public class ShareTypeServiceImpl implements ShareTypeService {

    private final ShareTypeMapper shareTypeMapper;

    @Override
    public List<ShareTypeVO> getShareTypeList() {
        LambdaQueryWrapper<ShareType> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(ShareType::getId);
        
        List<ShareType> shareTypes = shareTypeMapper.selectList(wrapper);
        
        return shareTypes.stream()
                .map(shareType -> {
                    ShareTypeVO vo = new ShareTypeVO();
                    vo.setId(shareType.getId());
                    vo.setName(shareType.getName());
                    vo.setIsEnabled(shareType.getIsEnabled());
                    return vo;
                })
                .toList();
    }

}
