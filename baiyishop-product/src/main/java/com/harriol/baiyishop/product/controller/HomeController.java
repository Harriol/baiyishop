package com.harriol.baiyishop.product.controller;

import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.product.dto.home.HomeResponse;
import com.harriol.baiyishop.product.service.HomeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台首页聚合（docs/api.md 4.3、REQ-403）。
 * <p>公开接口：一次拿回轮播、公告、金刚区与楼层（楼层已带好商品）。
 * <p>任一模块未配置时返回空数组，双端做空态兜底。
 */
@RestController
@RequestMapping("/api/v1/home")
public class HomeController {

    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    /** 首页聚合数据 */
    @GetMapping
    public Result<HomeResponse> home() {
        return Result.ok(homeService.home());
    }
}