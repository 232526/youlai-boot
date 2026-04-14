package com.youlai.boot.share.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.youlai.boot.common.annotation.Log;
import com.youlai.boot.common.enums.ActionTypeEnum;
import com.youlai.boot.common.enums.LogModuleEnum;
import com.youlai.boot.common.result.PageResult;
import com.youlai.boot.common.result.Result;
import com.youlai.boot.share.model.form.ShareOrderForm;
import com.youlai.boot.share.model.query.ShareOrderQuery;
import com.youlai.boot.share.model.vo.ShareOrderDetailVO;
import com.youlai.boot.share.model.vo.ShareOrderPageVO;
import com.youlai.boot.share.service.ShareOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 社群订单控制层
 *
 * @author Ray.Hao
 * @since 2026/04/08
 */
@Tag(name = "13.社群分享接口")
@RestController
@RequestMapping("/api/v1/share-orders")
@RequiredArgsConstructor
public class ShareOrderController {

    private final ShareOrderService shareOrderService;

    @Operation(summary = "社群订单分页列表")
    @GetMapping
    @Log(module = LogModuleEnum.ORDER, value = ActionTypeEnum.LIST)
    public PageResult<ShareOrderPageVO> getShareOrderPage(ShareOrderQuery queryParams) {
        Page<ShareOrderPageVO> result = shareOrderService.getShareOrderPage(queryParams);
        return PageResult.success(result);
    }

    @Operation(summary = "获取订单详情")
    @GetMapping("/{id}")
    public Result<ShareOrderDetailVO> getOrderDetail(
        @Parameter(description = "订单ID") @PathVariable Long id
    ) {
        ShareOrderDetailVO detail = shareOrderService.getOrderDetail(id);
        return Result.success(detail);
    }

    @Operation(summary = "创建社群订单")
    @PostMapping
    @Log(module = LogModuleEnum.ORDER, value = ActionTypeEnum.INSERT)
    public Result<String> createOrder(@Valid @RequestBody ShareOrderForm formData) {
        String orderId = shareOrderService.createOrder(formData);
        return Result.success(orderId);
    }

    @Operation(summary = "取消订单")
    @PutMapping("/{id}/cancel")
    @Log(module = LogModuleEnum.ORDER, value = ActionTypeEnum.UPDATE)
    public Result<Void> cancelOrder(
        @Parameter(description = "订单ID") @PathVariable Long id
    ) {
        boolean result = shareOrderService.cancelOrder(id);
        return Result.judge(result);
    }

}
