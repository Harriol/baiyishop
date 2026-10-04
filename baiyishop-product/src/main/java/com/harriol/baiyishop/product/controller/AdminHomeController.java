package com.harriol.baiyishop.product.controller;

import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.common.security.annotation.RequiresRole;
import com.harriol.baiyishop.product.dto.home.BannerItem;
import com.harriol.baiyishop.product.dto.home.FloorItem;
import com.harriol.baiyishop.product.dto.home.NavItem;
import com.harriol.baiyishop.product.dto.home.NoticeItem;
import com.harriol.baiyishop.product.service.HomeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台首页配置（docs/api.md 5.2、REQ-401、REQ-402）。
 * <p>四类配置都是「GET 读全量 / PUT 存全量」的整表替换语义，后台一次提交整块配置。
 * <p>保存后前台刷新即生效，不需要重启服务。
 */
@RestController
@RequestMapping("/api/v1/admin/home")
@RequiresRole({"SUPER_ADMIN", "OPERATOR"})
public class AdminHomeController {

    private final HomeService homeService;

    public AdminHomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    @GetMapping("/banners")
    public Result<List<BannerItem>> banners() {
        return Result.ok(homeService.banners());
    }

    @PutMapping("/banners")
    public Result<List<BannerItem>> saveBanners(@Valid @RequestBody List<BannerItem> items) {
        return Result.ok(homeService.saveBanners(items));
    }

    @GetMapping("/notices")
    public Result<List<NoticeItem>> notices() {
        return Result.ok(homeService.notices());
    }

    @PutMapping("/notices")
    public Result<List<NoticeItem>> saveNotices(@Valid @RequestBody List<NoticeItem> items) {
        return Result.ok(homeService.saveNotices(items));
    }

    @GetMapping("/navs")
    public Result<List<NavItem>> navs() {
        return Result.ok(homeService.navs());
    }

    @PutMapping("/navs")
    public Result<List<NavItem>> saveNavs(@Valid @RequestBody List<NavItem> items) {
        return Result.ok(homeService.saveNavs(items));
    }

    /** 楼层配置：每个楼层可单独设置 sortField 与展示数量，可选人工置顶商品（REQ-402） */
    @GetMapping("/floors")
    public Result<List<FloorItem>> floors() {
        return Result.ok(homeService.floors());
    }

    @PutMapping("/floors")
    public Result<List<FloorItem>> saveFloors(@Valid @RequestBody List<FloorItem> items) {
        return Result.ok(homeService.saveFloors(items));
    }
}