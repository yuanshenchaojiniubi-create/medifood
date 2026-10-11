package com.medifood.common.interceptor;

import com.medifood.common.annotation.CurrentUser;
import com.medifood.common.exception.BusinessException;
import com.medifood.common.result.ResultCode;
import com.medifood.common.util.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Parameter;

/**
 * 认证拦截器
 * 用于验证 JWT Token 并解析用户信息
 *
 * @author MediFood Team
 * */


@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {
    @Autowired
    private JwtUtils jwtUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 如果不是方法处理器，直接放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;

        // 检查方法参数中是否有 @CurrentUser 注解
        boolean needAuth = false;
        boolean required = true;
        Parameter[] parameters = handlerMethod.getMethod().getParameters();
        for (Parameter parameter : parameters) {
            CurrentUser currentUser = parameter.getAnnotation(CurrentUser.class);
            if (currentUser != null) {
                needAuth = true;
                required = currentUser.required();
                break;
            }
        }

        // 如果不需要认证，直接放行
        if (!needAuth) {
            return true;
        }

        // 如果 required = false，允许未登录用户访问
        if (!required) {
            String token = request.getHeader("Authorization");
            if (token != null && !token.isEmpty()) {
                if (token.startsWith("Bearer ")) {
                    token = token.substring(7);
                }
                if (jwtUtils.isTokenValid(token)) {

                    Long userId = Long.valueOf(jwtUtils.getUserIdFromToken(token));
                    System.out.println("在拦截器设置userId: " + userId);
                    request.setAttribute("currentUserId", userId);
                }
            }
            return true;
        }

        // 从请求头中获取 Token
        String token = request.getHeader("Authorization");
        if (token == null || token.isEmpty()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        // 移除 "Bearer " 前缀（如果存在）
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        // 验证 Token
  if (!jwtUtils.isTokenValid(token)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }


        // 将用户ID存储到 request 属性中，供后续使用
        Long userId = Long.valueOf(jwtUtils.getUserIdFromToken(token));
        System.out.println("在拦截器设置userId: " + userId);

        request.setAttribute("currentUserId", userId);

        return true;
    }
}

