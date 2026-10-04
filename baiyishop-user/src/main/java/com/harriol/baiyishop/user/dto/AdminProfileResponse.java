package com.harriol.baiyishop.user.dto;

import java.util.List;

/**
 * 后台管理员资料：角色与权限码列表供前端渲染菜单（docs/api.md 5.1）。
 */
public record AdminProfileResponse(Long id, String username, String realName,
                                   List<String> roles, List<String> permissions) {
}