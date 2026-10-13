package com.library.web.dto;

import com.library.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增 / 编辑用户的表单对象。
 *
 * <p>密码为明文输入：新增时必填，编辑时留空表示「不修改原密码」。
 */
public class UserForm {

    private Long id;

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度需在 3~50 之间")
    private String username;

    /**
     * 允许为空（编辑时表示不修改），非空时长度须为 6~100。
     * 这里用正则而不是 @Size，因为 @Size 会把空串判为长度不足而报错。
     */
    @Pattern(regexp = "^$|^.{6,100}$", message = "密码长度需在 6~100 之间")
    private String password;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名不能超过 50 个字符")
    private String fullName;

    @NotNull(message = "请选择角色")
    private Role role;

    /** id 为空说明是新增。 */
    public boolean isNew() {
        return id == null;
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
