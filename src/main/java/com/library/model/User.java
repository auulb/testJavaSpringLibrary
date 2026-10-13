package com.library.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 系统用户。password 字段存放 BCrypt 哈希，不存明文。
 */
public class User {

    private Long id;

    private String username;

    /** BCrypt 密码哈希。 */
    private String password;

    private String fullName;

    private Role role;

    public User() {
    }

    public User(String username, String password, String fullName, Role role) {
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
    }

    /** 派生值，不写入 JSON。 */
    @JsonIgnore
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
