package com.harriol.baiyishop.common.data.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

/**
 * created_at / updated_at 自动填充（docs/database.md 1.3）。
 * <p>DDL 里也给了 DEFAULT CURRENT_TIMESTAMP(3) 兜底，双保险。
 */
public class AuditMetaObjectHandler implements MetaObjectHandler {

    private static final String CREATED_AT = "createdAt";
    private static final String UPDATED_AT = "updatedAt";

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, CREATED_AT, LocalDateTime.class, now);
        strictInsertFill(metaObject, UPDATED_AT, LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, UPDATED_AT, LocalDateTime.class, LocalDateTime.now());
    }
}
