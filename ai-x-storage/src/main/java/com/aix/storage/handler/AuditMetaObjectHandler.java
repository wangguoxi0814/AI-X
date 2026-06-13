package com.aix.storage.handler;

import com.aix.storage.constant.AuditConstants;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "createUserId", Long.class, currentUserId());
        strictInsertFill(metaObject, "updateUserId", Long.class, currentUserId());
        strictInsertFill(metaObject, "deleted", Integer.class, 0);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        strictUpdateFill(metaObject, "updateUserId", Long.class, currentUserId());
    }

    private Long currentUserId() {
        // TODO: 多用户阶段从 SecurityContext / ThreadLocal 获取
        return AuditConstants.SYSTEM_USER_ID;
    }
}
