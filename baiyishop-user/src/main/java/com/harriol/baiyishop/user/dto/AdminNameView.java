package com.harriol.baiyishop.user.dto;

/** 管理员姓名（内部接口，供 order-service 写备注时留存姓名快照）。 */
public record AdminNameView(Long id, String name) {
}
