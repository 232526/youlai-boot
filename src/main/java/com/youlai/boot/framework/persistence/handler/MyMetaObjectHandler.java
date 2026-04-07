package com.youlai.boot.framework.persistence.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.youlai.boot.framework.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * mybatis-plus 字段自动填充
 * <p>
 * 支持自动填充创建时间、更新时间、创建人、更新人
 * </p>
 *
 * @author Ray.Hao
 * @since 2022/10/14
 */
@Component
@RequiredArgsConstructor
public class MyMetaObjectHandler implements MetaObjectHandler {

    /**
     * 新增填充创建时间、更新时间、创建人
     *
     * @param metaObject 元数据
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime::now, LocalDateTime.class);
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime::now, LocalDateTime.class);
        this.strictInsertFill(metaObject, "createBy", SecurityUtils::getUserId, Long.class);
        this.strictUpdateFill(metaObject, "updateBy", SecurityUtils::getUserId, Long.class);
    }

    /**
     * 更新填充更新时间、更新人
     *
     * @param metaObject 元数据
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime::now, LocalDateTime.class);
        this.strictUpdateFill(metaObject, "updateBy", SecurityUtils::getUserId, Long.class);
    }

}
