package com.timespace.common.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * I-06 XSS 注入攻击异常
 * 当请求参数中检测到潜在的 XSS 注入 payload 时抛出
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class XssInjectionException extends RuntimeException {

    private final String fieldName;
    private final String injectedValue;

    public XssInjectionException(String fieldName, String injectedValue) {
        super("检测到潜在的 XSS 注入攻击，字段: " + fieldName);
        this.fieldName = fieldName;
        this.injectedValue = injectedValue;
    }

    public XssInjectionException(String message) {
        super(message);
        this.fieldName = null;
        this.injectedValue = null;
    }
}
