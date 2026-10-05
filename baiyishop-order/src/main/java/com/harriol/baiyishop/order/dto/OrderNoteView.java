package com.harriol.baiyishop.order.dto;

import com.harriol.baiyishop.order.entity.OrderNote;

import java.time.LocalDateTime;

/** 后台订单备注视图（REQ-708）。 */
public record OrderNoteView(Long id, Long adminId, String adminName, String content, LocalDateTime createdAt) {

    public static OrderNoteView from(OrderNote note) {
        return new OrderNoteView(note.getId(), note.getAdminId(), note.getAdminName(),
                note.getContent(), note.getCreatedAt());
    }
}
