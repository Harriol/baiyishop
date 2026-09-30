package com.harriol.baiyishop.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 微信绑定（docs/database.md 3.2，REQ-102）。 */
@Getter
@Setter
@TableName("user_wechat")
public class UserWechat {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 小程序 openid，同一 openid 只绑定一个账号 */
    private String openid;

    private String unionid;

    private String nickname;

    private String avatar;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
