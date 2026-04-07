package com.youlai.boot.system.controller;

import com.youlai.boot.common.result.Result;
import com.youlai.boot.system.model.vo.CountryVO;
import com.youlai.boot.system.service.CountryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 国家控制层
 *
 * @author Ray.Hao
 * @since 2026/04/07
 */
@Tag(name = "国家接口")
@RestController
@RequestMapping("/api/v1/countries")
@RequiredArgsConstructor
public class CountryController {

    private final CountryService countryService;

    @Operation(summary = "获取国家列表")
    @GetMapping
    public Result<List<CountryVO>> getCountryList() {
        List<CountryVO> list = countryService.getCountryList();
        return Result.success(list);
    }

}
