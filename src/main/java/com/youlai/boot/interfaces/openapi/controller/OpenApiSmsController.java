package com.youlai.boot.interfaces.openapi.controller;

import com.youlai.boot.common.result.Result;
import com.youlai.boot.interfaces.openapi.model.SmsOrderFormApiModel;
import com.youlai.boot.market.order.model.form.SmsOrderForm;
import com.youlai.boot.market.order.model.vo.SmsOrderDetailVO;
import com.youlai.boot.market.order.service.SmsOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

/**
 * 开放API - 短信订单接口
 * <p>
 * 面向外部系统的短信下单和查询接口，使用API签名鉴权。
 * 鉴权方式：请求头传递 X-Api-Key、X-Timestamp（秒级时间戳）、X-Sign（签名）
 * 签名生成：MD5(apiKey + apiSecret + timestamp)，32位字符串，不区分大小写
 * </p>
 */
@Tag(name = "开放API-短信订单")
@RestController
@RequestMapping("/api/v1/open/sms-orders")
@RequiredArgsConstructor
public class OpenApiSmsController {

    private final SmsOrderService smsOrderService;

    @Operation(summary = "创建短信订单", description = "外部系统通过API签名鉴权后下单短信", parameters = {@Parameter(name = "X-Api-Key", description = "API密钥", required = true, in = ParameterIn.HEADER), @Parameter(name = "X-Timestamp", description = "当前系统时间戳（秒）", required = true, in = ParameterIn.HEADER), @Parameter(name = "X-Sign", description = "签名：MD5(apiKey+apiSecret+timestamp)", required = true, in = ParameterIn.HEADER)})
    @PostMapping
    public Result<String> createOrder(@Valid @RequestBody SmsOrderFormApiModel formData) {
        //电话列表不能超过2k
        if (formData.getPhoneNumberList().size() > 2000) {
            return Result.failed("电话列表不能超过2k");
        }

        //短信内容不能超过1024个字符
        if (formData.getContent().length() > 1024) {
            return Result.failed("短信内容不能超过1024个字符");
        }

        //发送号码，最大长度是32个字符
        if (formData.getPhoneNumberList().stream().anyMatch(phone -> phone.length() > 32)) {
            return Result.failed("发送号码，最大长度是32个字符");
        }

        SmsOrderForm smsOrderForm = new SmsOrderForm();
        smsOrderForm.setCountryId(1);
        smsOrderForm.setHasAreaCode(0);
        // 预约时间设为当前时间两分钟后
        smsOrderForm.setScheduledTime(System.currentTimeMillis() + 2 * 60 * 1000);
        smsOrderForm.setMessageContentList(Collections.singletonList(formData.getContent()));
        smsOrderForm.setPhoneNumberList(formData.getPhoneNumberList());
        String orderId = smsOrderService.createOrder(smsOrderForm);
        return Result.success(orderId);
    }

    @Operation(summary = "查询短信订单详情", description = "外部系统通过API签名鉴权后查询短信订单状态", parameters = {@Parameter(name = "X-Api-Key", description = "API密钥", required = true, in = ParameterIn.HEADER), @Parameter(name = "X-Timestamp", description = "当前系统时间戳（秒）", required = true, in = ParameterIn.HEADER), @Parameter(name = "X-Sign", description = "签名：MD5(apiKey+apiSecret+timestamp)", required = true, in = ParameterIn.HEADER)})
    @GetMapping("/{id}")
    public Result<SmsOrderDetailVO> getOrderDetail(@Parameter(description = "订单ID") @PathVariable Long id) {
        SmsOrderDetailVO detail = smsOrderService.getOrderDetail(id);
        return Result.success(detail);
    }
}
