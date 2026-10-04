package com.harriol.baiyishop.product.controller;

import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.common.security.annotation.RequiresRole;
import com.harriol.baiyishop.product.dto.ParamItemRequest;
import com.harriol.baiyishop.product.dto.ParamItemResponse;
import com.harriol.baiyishop.product.dto.ParamTemplateRequest;
import com.harriol.baiyishop.product.dto.ParamTemplateResponse;
import com.harriol.baiyishop.product.service.ParamService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台参数模板与参数项管理（docs/api.md 5.2、REQ-204）。
 * <p>超管与运营可维护；客服访问返回 403。
 */
@RestController
@RequestMapping("/api/v1/admin/params")
@RequiresRole({"SUPER_ADMIN", "OPERATOR"})
public class AdminParamController {

    private final ParamService paramService;

    public AdminParamController(ParamService paramService) {
        this.paramService = paramService;
    }

    /** 模板列表（含各自参数项） */
    @GetMapping("/templates")
    public Result<List<ParamTemplateResponse>> templates() {
        return Result.ok(paramService.listTemplates());
    }

    /** 新增模板 */
    @PostMapping("/templates")
    public Result<ParamTemplateResponse> createTemplate(@Valid @RequestBody ParamTemplateRequest request) {
        return Result.ok(paramService.createTemplate(request));
    }

    /** 修改模板 */
    @PutMapping("/templates/{id}")
    public Result<ParamTemplateResponse> updateTemplate(@PathVariable Long id,
                                                        @Valid @RequestBody ParamTemplateRequest request) {
        return Result.ok(paramService.updateTemplate(id, request));
    }

    /** 删除模板：下面还有参数项时拒绝 */
    @DeleteMapping("/templates/{id}")
    public Result<Void> deleteTemplate(@PathVariable Long id) {
        paramService.deleteTemplate(id);
        return Result.ok();
    }

    /** 模板下的参数项 */
    @GetMapping("/templates/{id}/items")
    public Result<List<ParamItemResponse>> items(@PathVariable Long id) {
        return Result.ok(paramService.listItems(id));
    }

    /** 新增参数项 */
    @PostMapping("/items")
    public Result<ParamItemResponse> createItem(@Valid @RequestBody ParamItemRequest request) {
        return Result.ok(paramService.createItem(request));
    }

    /** 修改参数项 */
    @PutMapping("/items/{id}")
    public Result<ParamItemResponse> updateItem(@PathVariable Long id, @Valid @RequestBody ParamItemRequest request) {
        return Result.ok(paramService.updateItem(id, request));
    }

    /** 删除参数项：已被商品使用时拒绝 */
    @DeleteMapping("/items/{id}")
    public Result<Void> deleteItem(@PathVariable Long id) {
        paramService.deleteItem(id);
        return Result.ok();
    }
}