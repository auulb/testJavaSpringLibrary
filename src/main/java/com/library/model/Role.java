package com.library.model;

/**
 * 用户角色。Spring Security 中映射为 ROLE_ADMIN / ROLE_READER。
 */
public enum Role {

    ADMIN("管理员"),
    READER("读者");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    /** 中文显示名，供页面展示。 */
    public String getLabel() {
        return label;
    }
}
