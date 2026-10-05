package com.harriol.baiyishop.order.controller;

import com.harriol.baiyishop.common.core.exception.BizException;
import com.harriol.baiyishop.common.core.result.ErrorCode;
import com.harriol.baiyishop.common.core.result.PageResult;
import com.harriol.baiyishop.common.core.result.Result;
import com.harriol.baiyishop.common.security.annotation.RequiresRole;
import com.harriol.baiyishop.common.security.context.UserContext;
import com.harriol.baiyishop.order.dto.AdminOrderDetailView;
import com.harriol.baiyishop.order.dto.AdminOrderItem;
import com.harriol.baiyishop.order.dto.OrderNoteRequest;
import com.harriol.baiyishop.order.dto.OrderNoteView;
import com.harriol.baiyishop.order.dto.ShipRequest;
import com.harriol.baiyishop.order.service.AdminOrderService;
import com.harriol.baiyishop.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台订单管理（docs/api.md 5.4、REQ-708）。
 * <p>权限按角色分档（R5-Q3）：客服只有查询与备注，发货需要超管 / 运营 ——
 * 方法上的 @RequiresRole 覆盖类级别，未标的按类级别生效。
 * <p>时间参数兼容两种写法：{@code 2026-10-01 00:00:00} 与 ISO 的 {@code 2026-10-01T00:00:00}。
 */
@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;
    private final OrderService orderService;

    public AdminOrderController(AdminOrderService adminOrderService, OrderService orderService) {
        this.adminOrderService = adminOrderService;
        this.orderService = orderService;
    }

    /** 订单列表（订单号 / 状态 / 用户 / 下单时间区间 / 分页） */
    @GetMapping
    @RequiresRole({"SUPER_ADMIN", "OPERATOR", "SERVICE"})
    public Result<PageResult<AdminOrderItem>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String orderNo,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return Result.ok(adminOrderService.page(page, size, orderNo, status, userId,
                parseTime(from, "from"), parseTime(to, "to")));
    }

    /** 订单详情（明细、地址快照、状态日志、备注） */
    @GetMapping("/{orderNo}")
    @RequiresRole({"SUPER_ADMIN", "OPERATOR", "SERVICE"})
    public Result<AdminOrderDetailView> detail(@PathVariable String orderNo) {
        return Result.ok(new AdminOrderDetailView(orderService.detailByOrderNo(orderNo),
                adminOrderService.require(orderNo).getUserId(), adminOrderService.notes(orderNo)));
    }

    /** 发货（录入运单号）：仅待发货可发货，成功即安排 7 天自动确认收货 */
    @PostMapping("/{orderNo}/ship")
    @RequiresRole({"SUPER_ADMIN", "OPERATOR"})
    public Result<Void> ship(@PathVariable String orderNo, @Valid @RequestBody ShipRequest request) {
        adminOrderService.ship(orderNo, request.trackingNo(), UserContext.requireAdminId());
        return Result.ok();
    }

    /** 备注列表 */
    @GetMapping("/{orderNo}/notes")
    @RequiresRole({"SUPER_ADMIN", "OPERATOR", "SERVICE"})
    public Result<List<OrderNoteView>> notes(@PathVariable String orderNo) {
        return Result.ok(adminOrderService.notes(orderNo));
    }

    /** 添加备注（客服唯一的写权限） */
    @PostMapping("/{orderNo}/notes")
    @RequiresRole({"SUPER_ADMIN", "OPERATOR", "SERVICE"})
    public Result<OrderNoteView> addNote(@PathVariable String orderNo,
                                         @Valid @RequestBody OrderNoteRequest request) {
        return Result.ok(adminOrderService.addNote(orderNo, request.content(), UserContext.requireAdminId()));
    }

    private LocalDateTime parseTime(String value, String field) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim().replace(' ', 'T'));
        } catch (RuntimeException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 时间格式应为 yyyy-MM-dd HH:mm:ss");
        }
    }
}
