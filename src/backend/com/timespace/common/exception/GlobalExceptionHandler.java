package com.timespace.common.exception;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidationException(MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(err ->
                errors.put(err.getField(), err.getDefaultMessage()));
        log.warn("参数校验失败: {}", errors);
        return Result.fail(400, "参数校验失败", errors);
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail(500, "系统繁忙，请稍后重试");
    }

    @Data
    public static class Result<T> {
        private int code;
        private String message;
        private T data;

        public static <T> Result<T> ok() {
            return ok(null);
        }

        public static <T> Result<T> ok(T data) {
            Result<T> r = new Result<>();
            r.setCode(200);
            r.setMessage("success");
            r.setData(data);
            return r;
        }

        public static <T> Result<T> fail(int code, String message) {
            Result<T> r = new Result<>();
            r.setCode(code);
            r.setMessage(message);
            return r;
        }

        public static <T> Result<T> fail(int code, String message, T data) {
            Result<T> r = new Result<>();
            r.setCode(code);
            r.setMessage(message);
            r.setData(data);
            return r;
        }
    }
}
