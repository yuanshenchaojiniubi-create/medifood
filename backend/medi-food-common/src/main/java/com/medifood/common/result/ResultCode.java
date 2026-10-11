package com.medifood.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 响应码枚举
 * 
 * @author MediFood Team
 */
@Getter
@AllArgsConstructor
public enum ResultCode {
    
    /**
     * 成功
     */
    SUCCESS(200, "操作成功"),
    
    /**
     * 失败
     */
    FAIL(500, "操作失败"),
    
    /**
     * 参数错误
     */
    PARAM_ERROR(400, "参数错误"),
    
    /**
     * 未授权
     */
    UNAUTHORIZED(401, "未授权，请先登录"),
    
    /**
     * 无权限
     */
    FORBIDDEN(403, "无权限访问"),
    
    /**
     * 资源不存在
     */
    NOT_FOUND(404, "资源不存在"),
    
    /**
     * 服务器错误
     */
    INTERNAL_ERROR(500, "服务器内部错误"),
    
    /**
     * 用户相关错误码 1000-1999
     */
    USER_NOT_EXIST(1001, "用户不存在"),
    USER_ALREADY_EXIST(1002, "用户已存在"),
    USER_PASSWORD_ERROR(1003, "密码错误"),
    USER_ACCOUNT_DISABLED(1004, "账号已被禁用"),
    USER_NOT_LOGIN(1005, "用户未登录"),
    PASSWORD_ERROR(1006, "密码错误"),
    
    /**
     * 知识相关错误码 2000-2999
     */
    KNOWLEDGE_NOT_EXIST(2001, "知识不存在"),
    KNOWLEDGE_PUBLISH_FAILED(2002, "知识发布失败"),
    
    /**
     * 食谱相关错误码 3000-3999
     */
    RECIPE_NOT_EXIST(3001, "食谱不存在"),
    RECIPE_PUBLISH_FAILED(3002, "食谱发布失败"),
    
    /**
     * 社区相关错误码 4000-4999
     */
    POST_NOT_EXIST(4001, "动态不存在"),
    POST_PUBLISH_FAILED(4002, "动态发布失败"),
    
    /**
     * 问答相关错误码 5000-5999
     */
    QUESTION_NOT_EXIST(5001, "问题不存在"),
    ANSWER_NOT_EXIST(5002, "回答不存在"),
    QUESTION_PUBLISH_FAILED(5003, "问题发布失败"),
    ANSWER_PUBLISH_FAILED(5004, "回答发布失败");
    
    /**
     * 响应码
     */
    private final Integer code;
    
    /**
     * 响应消息
     */
    private final String message;
}

