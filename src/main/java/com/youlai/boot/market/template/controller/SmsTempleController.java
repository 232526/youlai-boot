package com.youlai.boot.market.template.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.youlai.boot.common.annotation.Log;
import com.youlai.boot.common.enums.ActionTypeEnum;
import com.youlai.boot.common.enums.LogModuleEnum;
import com.youlai.boot.common.result.PageResult;
import com.youlai.boot.common.result.Result;
import com.youlai.boot.market.template.model.form.SmsTempleForm;
import com.youlai.boot.market.template.model.query.SmsTempleQuery;
import com.youlai.boot.market.template.model.vo.SmsTempleDetailVO;
import com.youlai.boot.market.template.model.vo.SmsTemplePageVO;
import com.youlai.boot.market.template.service.SmsTempleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 短信模板控制层
 *
 * @author Ray.Hao
 * @since 2026/04/08
 */
@Tag(name = "13.短信模板接口")
@RestController
@RequestMapping("/api/v1/sms-temples")
@RequiredArgsConstructor
public class SmsTempleController {

    private final SmsTempleService smsTempleService;

    @Operation(summary = "短信模板分页列表")
    @GetMapping
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.LIST)
    public PageResult<SmsTemplePageVO> getSmsTemplePage(SmsTempleQuery queryParams) {
        Page<SmsTemplePageVO> result = smsTempleService.getSmsTemplePage(queryParams);
        return PageResult.success(result);
    }

    @Operation(summary = "获取模板详情")
    @GetMapping("/{templateId}")
    public Result<SmsTempleDetailVO> getTempleDetail(
            @Parameter(description = "模板ID") @PathVariable Long templateId
    ) {
        SmsTempleDetailVO detail = smsTempleService.getTempleDetail(templateId);
        return Result.success(detail);
    }

    @Operation(summary = "新增短信模板")
    @PostMapping
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.INSERT)
    public Result<Long> saveSmsTemple(@Valid @RequestBody SmsTempleForm formData) {
        Long templeId = smsTempleService.saveSmsTemple(formData);
        return Result.success(templeId);
    }

    @Operation(summary = "更新短信模板")
    @PutMapping("/{templateId}")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.UPDATE)
    public Result<Void> updateSmsTemple(
            @Parameter(description = "模板ID") @PathVariable Long templateId,
            @Valid @RequestBody SmsTempleForm formData
    ) {
        boolean result = smsTempleService.updateSmsTemple(templateId, formData);
        return Result.judge(result);
    }

    @Operation(summary = "删除短信模板")
    @DeleteMapping("/{templateId}")
    @Log(module = LogModuleEnum.OTHER, value = ActionTypeEnum.DELETE)
    public Result<Void> deleteSmsTemple(
            @Parameter(description = "模板ID") @PathVariable Long templateId
    ) {
        boolean result = smsTempleService.deleteSmsTemple(templateId);
        return Result.judge(result);
    }

}
