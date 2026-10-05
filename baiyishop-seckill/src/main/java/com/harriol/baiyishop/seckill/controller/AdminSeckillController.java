package com.harriol.baiyishop.seckill.controller;

import com.harriol.baiyishop.common.core.result.PageResult;
import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.common.security.annotation.RequiresRole;
import com.harriol.baiyishop.common.security.context.UserContext;
import com.harriol.baiyishop.seckill.dto.ActivityRequest;
import com.harriol.baiyishop.seckill.dto.ActivityView;
import com.harriol.baiyishop.seckill.service.SeckillActivityService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台秒杀活动管理（docs/api.md 5.5、REQ-901）。
 * <p>超管与运营可维护；客服访问返回 403。
 */
@RestController
@RequestMapping("/api/v1/admin/seckill/activities")
@RequiresRole({"SUPER_ADMIN", "OPERATOR"})
public class AdminSeckillController {

    private final SeckillActivityService activityService;

    public AdminSeckillController(SeckillActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping
    public Result<PageResult<ActivityView>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String status) {
        return Result.ok(activityService.page(page, size, status));
    }

    @GetMapping("/{id}")
    public Result<ActivityView> detail(@PathVariable long id) {
        return Result.ok(activityService.detail(id));
    }

    /** 创建活动：创建即向 inventory 申请划拨（REQ-901、REQ-504） */
    @PostMapping
    public Result<ActivityView> create(@Valid @RequestBody ActivityRequest request) {
        return Result.ok(activityService.create(UserContext.requireAdminId(), request));
    }

    /** 修改活动：仅未开始的活动，且只支持名称与起止时间 */
    @PutMapping("/{id}")
    public Result<ActivityView> update(@PathVariable long id, @Valid @RequestBody ActivityRequest request) {
        return Result.ok(activityService.update(id, request));
    }

    /** 结束活动：未售出自动回补（REQ-504） */
    @PutMapping("/{id}/status")
    public Result<Void> end(@PathVariable long id) {
        activityService.end(id);
        return Result.ok();
    }

    /** 删除活动：未开始 / 已结束可删，删除前回补 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id) {
        activityService.delete(id);
        return Result.ok();
    }
}
