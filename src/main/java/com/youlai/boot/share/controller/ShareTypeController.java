package com.youlai.boot.share.controller;

import com.youlai.boot.common.result.Result;
import com.youlai.boot.share.model.vo.ShareTypeVO;
import com.youlai.boot.share.service.ShareTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分享类型控制层
 *
 * @author Ray.Hao
 * @since 2026/04/08
 */
@Tag(name = "社群分享接口")
@RestController
@RequestMapping("/api/v1/share-types")
@RequiredArgsConstructor
public class ShareTypeController {

    private final ShareTypeService shareTypeService;

    @Operation(summary = "获取分享类型列表")
    @GetMapping
    public Result<List<ShareTypeVO>> getShareTypeList() {
        List<ShareTypeVO> list = shareTypeService.getShareTypeList();
        return Result.success(list);
    }

}
