package com.youlai.boot.market.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.youlai.boot.common.annotation.Log;
import com.youlai.boot.common.enums.ActionTypeEnum;
import com.youlai.boot.common.enums.LogModuleEnum;
import com.youlai.boot.common.result.PageResult;
import com.youlai.boot.common.result.Result;
import com.youlai.boot.market.order.model.form.SmsOrderForm;
import com.youlai.boot.market.order.model.query.SmsOrderQuery;
import com.youlai.boot.market.order.model.query.SmsPhoneRecordQuery;
import com.youlai.boot.market.order.model.vo.SmsOrderDetailVO;
import com.youlai.boot.market.order.model.vo.SmsOrderPageVO;
import com.youlai.boot.market.order.model.vo.SmsPhoneRecordPageVO;
import com.youlai.boot.market.order.service.SmsOrderService;
import com.youlai.boot.market.order.service.SmsPhoneRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 短信订单控制层
 *
 * @author Ray.Hao
 * @since 2026/04/07
 */
@Tag(name = "12.短信订单接口")
@RestController
@RequestMapping("/api/v1/sms-orders")
@RequiredArgsConstructor
public class SmsOrderController {

    private final SmsOrderService smsOrderService;
    private final SmsPhoneRecordService smsPhoneRecordService;

    @Operation(summary = "短信订单分页列表")
    @GetMapping
    @Log(module = LogModuleEnum.ORDER, value = ActionTypeEnum.LIST)
    public PageResult<SmsOrderPageVO> getSmsOrderPage(SmsOrderQuery queryParams) {
        Page<SmsOrderPageVO> result = smsOrderService.getSmsOrderPage(queryParams);
        return PageResult.success(result);
    }

    @Operation(summary = "获取订单详情")
    @GetMapping("/{id}")
    public Result<SmsOrderDetailVO> getOrderDetail(
            @Parameter(description = "订单ID") @PathVariable Long id
    ) {
        SmsOrderDetailVO detail = smsOrderService.getOrderDetail(id);
        return Result.success(detail);
    }

    @Operation(summary = "创建短信订单")
    @PostMapping
    @Log(module = LogModuleEnum.ORDER, value = ActionTypeEnum.INSERT)
    public Result<Long> createOrder(@Valid @RequestBody SmsOrderForm formData) {
        Long orderId = smsOrderService.createOrder(formData);
        return Result.success(orderId);
    }

    @Operation(summary = "取消订单")
    @PutMapping("/{id}/cancel")
    @Log(module = LogModuleEnum.ORDER, value = ActionTypeEnum.UPDATE)
    public Result<Void> cancelOrder(
            @Parameter(description = "订单ID") @PathVariable Long id
    ) {
        boolean result = smsOrderService.cancelOrder(id);
        return Result.judge(result);
    }

    @Operation(summary = "短信发送记录分页列表")
    @GetMapping("/phone-records")
    @Log(module = LogModuleEnum.ORDER, value = ActionTypeEnum.LIST)
    public PageResult<SmsPhoneRecordPageVO> getSmsPhoneRecordPage(SmsPhoneRecordQuery queryParams) {
        Page<SmsPhoneRecordPageVO> result = smsPhoneRecordService.getSmsPhoneRecordPage(queryParams);
        return PageResult.success(result);
    }

}
