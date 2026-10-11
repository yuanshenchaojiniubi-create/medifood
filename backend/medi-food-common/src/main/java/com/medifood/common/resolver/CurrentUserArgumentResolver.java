package com.medifood.common.resolver;

import com.medifood.common.annotation.CurrentUser;
import com.medifood.common.exception.BusinessException;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 当前用户参数解析器
 * 用于自动注入当前登录用户ID到 Controller 方法参数中
 * 
 * @author MediFood Team
 */
// 文件路径: D:\MediFood_Circle\backend\medi-food-common\src\main\java\com\medifood\common\resolver\CurrentUserArgumentResolver.java
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && parameter.getParameterType().equals(Long.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        // 从request属性中获取currentUserId（不是请求头！）
        Object userIdObj = webRequest.getAttribute("currentUserId", RequestAttributes.SCOPE_REQUEST);

        if (userIdObj == null) {
            CurrentUser currentUser = parameter.getParameterAnnotation(CurrentUser.class);
            if (currentUser != null && !currentUser.required()) {
                return null;
            }
            throw new BusinessException(401, "用户未登录");
        }

        // 确保类型正确
        if (userIdObj instanceof Long) {
            return userIdObj;
        } else if (userIdObj instanceof String) {
            try {
                return Long.parseLong((String) userIdObj);
            } catch (NumberFormatException e) {
                throw new BusinessException(400, "无效的用户ID");
            }
        }

        throw new BusinessException(400, "用户ID类型错误");
    }


}



