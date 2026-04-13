package com.youlai.boot.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.youlai.boot.common.enums.ActionTypeEnum;
import com.youlai.boot.common.enums.LogModuleEnum;
import com.youlai.boot.common.result.PageResult;
import com.youlai.boot.common.annotation.Log;
import com.youlai.boot.system.model.query.ChannelQuery;
import com.youlai.boot.system.model.vo.ChannelVO;
import com.youlai.boot.system.service.ChannelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 渠道前端控制层
 *
 * @author Theo
 * @since 2026-04-13
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "渠道管理")
@RequestMapping("/api/v1/channels")
public class ChannelController {

    private final ChannelService channelService;

    @Operation(summary = "短信渠道分页列表")
    @GetMapping("/sms")
    @PreAuthorize("@ss.hasPerm('sys:channel:list')")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.LIST)
    public PageResult<ChannelVO> smsPage(@ParameterObject ChannelQuery queryParams) {
        IPage<ChannelVO> result = channelService.pageByType("sms", queryParams);
        return PageResult.success(result);
    }

    @Operation(summary = "社群渠道分页列表")
    @GetMapping("/ws")
    @PreAuthorize("@ss.hasPerm('sys:channel:list')")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.LIST)
    public PageResult<ChannelVO> wsPage(@ParameterObject ChannelQuery queryParams) {
        IPage<ChannelVO> result = channelService.pageByType("ws", queryParams);
        return PageResult.success(result);
    }
}
