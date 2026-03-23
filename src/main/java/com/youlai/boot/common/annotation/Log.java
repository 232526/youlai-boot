package com.youlai.boot.common.annotation;

import com.youlai.boot.common.enums.ActionTypeEnum;
import com.youlai.boot.common.enums.LogModuleEnum;

import java.lang.annotation.*;

/**
 * 日志注解
 *
 * @author Ray
 * @since 2024/6/25
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface Log {

    /**
     * 模块
     *
     * @return 模块
     */
    LogModuleEnum module();

    /**
     * 操作类型
     *
     * @return 操作类型
     */
    ActionTypeEnum value();

    /**
     * 操作标题（可选，默认使用枚举描述）
     *
     * @return 标题
     */
    String title() default "";

    /**
     * 自定义日志内容（可选，用于记录操作细节）
     *
     * @return 日志内容
     */
    String content() default "";

}
