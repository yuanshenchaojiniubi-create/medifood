package com.medifood.common.exception;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.medifood.common.result.Result;
import com.medifood.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;

/**
 * 全局异常处理器
 * 
 * @author MediFood Team
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        log.error("业务异常: {}", e.getMessage(), e);
        return Result.fail(e.getCode(), e.getMessage());
    }
    
    /**
     * 处理参数校验异常（@RequestBody）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.error("参数校验异常: {}", e.getMessage());
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), message);
    }
    
    /**
     * 处理参数校验异常（@ModelAttribute）
     */
    @ExceptionHandler(BindException.class)
    public Result<?> handleBindException(BindException e) {
        log.error("参数绑定异常: {}", e.getMessage());
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "参数绑定失败";
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), message);
    }
    
    /**
     * 处理参数校验异常（@RequestParam）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<?> handleConstraintViolationException(ConstraintViolationException e) {
        log.error("参数约束异常: {}", e.getMessage());
        Set<ConstraintViolation<?>> violations = e.getConstraintViolations();
        String message = violations.iterator().next().getMessage();
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), message);
    }
    
    /**
     * 处理 Sentinel 限流/熔断异常（Flow/ParamFlow/Degrade 等），统一返回友好提示，避免 500
     */
    @ExceptionHandler(BlockException.class)
    public Result<?> handleBlockException(BlockException e) {
        log.warn("Sentinel 限流/熔断拦截: {}", e.getClass().getSimpleName());
        return Result.fail(429, "请求过于频繁，请稍后再试");
    }

    /**
     * 处理其他异常
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("系统异常: ", e);
        return Result.fail(ResultCode.INTERNAL_ERROR);
    }
}

