package com.library.web;

import com.library.security.LibraryUserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 把当前登录用户注入所有模板，供导航栏显示用户名/角色与菜单权限判断。
 */
@ControllerAdvice
public class GlobalModelAdvice {

    @ModelAttribute("currentUser")
    public CurrentUser currentUser(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof LibraryUserPrincipal principal) {
            return CurrentUser.of(principal);
        }
        return null;
    }
}
