package com.youlai.boot.market.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.youlai.boot.common.annotation.Log;
import com.youlai.boot.common.enums.ActionTypeEnum;
import com.youlai.boot.common.enums.LogModuleEnum;
import com.youlai.boot.common.result.PageResult;
import com.youlai.boot.market.order.model.query.UserTransactionQuery;
import com.youlai.boot.market.order.model.vo.UserTransactionPageVO;
import com.youlai.boot.market.order.service.UserTransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户交易流水控制层
 *
 * @author Ray.Hao
 * @since 2026/04/10
 */
@Tag(name = "15.用户交易流水接口")
@RestController
@RequestMapping("/api/v1/user-transactions")
@RequiredArgsConstructor
public class UserTransactionController {

    private final UserTransactionService userTransactionService;

    @Operation(summary = "用户交易流水分页列表")
    @GetMapping
    @Log(module = LogModuleEnum.ORDER, value = ActionTypeEnum.LIST)
    public PageResult<UserTransactionPageVO> getUserTransactionPage(UserTransactionQuery queryParams) {
        Page<UserTransactionPageVO> result = userTransactionService.getUserTransactionPage(queryParams);
        return PageResult.success(result);
    }

}
