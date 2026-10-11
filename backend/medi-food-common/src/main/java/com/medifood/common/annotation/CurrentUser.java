package com.medifood.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 当前用户注解
 * 用于在 Controller 方法参数中自动注入当前登录用户ID
 * 
 * @author MediFood Team
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
    /**
     * 是否必须，默认 true
     */
    boolean required() default true;
}

