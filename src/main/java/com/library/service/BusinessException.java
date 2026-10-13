package com.library.service;

/**
 * 业务规则校验失败（如 ISBN 重复、可借册数越界）。
 *
 * <p>{@code field} 指出应把错误挂在表单的哪个字段上，方便控制器回显到对应输入框；
 * 为 {@code null} 时作为整表单的全局错误显示。
 */
public class BusinessException extends RuntimeException {

    private final String field;

    public BusinessException(String message) {
        this(null, message);
    }

    public BusinessException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
