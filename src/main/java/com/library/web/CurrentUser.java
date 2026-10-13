package com.library.web;

import com.library.security.LibraryUserPrincipal;

/**
 * 供模板使用的当前登录用户视图对象。
 */
public record CurrentUser(Long id, String username, String fullName, String roleLabel, boolean admin) {

    public static CurrentUser of(LibraryUserPrincipal principal) {
        return new CurrentUser(
                principal.getId(),
                principal.getUsername(),
                principal.getFullName(),
                principal.getRole().getLabel(),
                principal.isAdmin());
    }
}
