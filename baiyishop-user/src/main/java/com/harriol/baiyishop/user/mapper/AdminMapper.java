package com.harriol.baiyishop.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harriol.baiyishop.user.entity.Admin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 后台管理员数据访问。 */
@Mapper
public interface AdminMapper extends BaseMapper<Admin> {

    /** 管理员拥有的角色码（role 是 MySQL 关键字，需加反引号） */
    @Select("""
            SELECT r.code FROM admin_role ar
            JOIN `role` r ON r.id = ar.role_id
            WHERE ar.admin_id = #{adminId} AND r.deleted = 0
            """)
    List<String> selectRoleCodes(@Param("adminId") Long adminId);

    /** 管理员通过角色间接拥有的权限码（docs/api.md 5.1 的「权限码列表」） */
    @Select("""
            SELECT DISTINCT p.code FROM admin_role ar
            JOIN role_permission rp ON rp.role_id = ar.role_id
            JOIN permission p ON p.id = rp.permission_id
            WHERE ar.admin_id = #{adminId} AND p.deleted = 0
            ORDER BY p.code
            """)
    List<String> selectPermissionCodes(@Param("adminId") Long adminId);
}