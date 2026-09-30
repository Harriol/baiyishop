package com.harriol.baiyishop.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 会员账号（docs/database.md 3.1）。
 * <p>密码只存 BCrypt 哈希，库中与日志中都不出现明文（REQ-101、NFR-03）。
 */
@Getter
@Setter
@ToString(exclude = "passwordHash")
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号（Web 端） */
    private String username;

    /** BCrypt 哈希；小程序用户可无密码 */
    private String passwordHash;

    private String nickname;

    /** 头像 URL（MinIO） */
    private String avatar;

    /** 手机号；接口返回需脱敏 */
    private String phone;

    /** 1 正常 / 0 禁用 */
    private Integer status;

    private LocalDateTime lastLoginAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除：0 正常 / 1 已删 */
    @TableLogic
    private Integer deleted;
}
