package com.library.web;

import com.library.model.Role;
import com.library.model.User;
import com.library.service.BusinessException;
import com.library.service.NotFoundException;
import com.library.service.UserService;
import com.library.web.dto.UserForm;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 用户管理：增删改查。整块仅管理员可访问（见 SecurityConfig 与类上的 @PreAuthorize）。
 */
@Controller
@RequestMapping("/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** 模板中下拉框用的角色选项。 */
    @ModelAttribute("allRoles")
    public Role[] allRoles() {
        return Role.values();
    }

    @GetMapping
    public String list(@RequestParam(required = false) String keyword, Model model) {
        model.addAttribute("users", userService.search(keyword));
        model.addAttribute("keyword", keyword);
        return "users/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        UserForm form = new UserForm();
        form.setRole(Role.READER);
        model.addAttribute("userForm", form);
        model.addAttribute("editing", false);
        return "users/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("userForm") UserForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {
        if (form.isNew() && (form.getPassword() == null || form.getPassword().isBlank())) {
            binding.rejectValue("password", "required", "新增用户时必须设置初始密码");
        }
        if (binding.hasErrors()) {
            model.addAttribute("editing", false);
            return "users/form";
        }
        try {
            userService.create(form);
        } catch (BusinessException e) {
            reject(binding, e);
            model.addAttribute("editing", false);
            return "users/form";
        }
        redirect.addFlashAttribute("message", "已新增用户「" + form.getUsername() + "」");
        return "redirect:/users";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        User user = userService.get(id);
        UserForm form = new UserForm();
        form.setId(user.getId());
        form.setUsername(user.getUsername());
        form.setFullName(user.getFullName());
        form.setRole(user.getRole());
        model.addAttribute("userForm", form);
        model.addAttribute("editing", true);
        return "users/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("userForm") UserForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("editing", true);
            return "users/form";
        }
        try {
            userService.update(id, form);
        } catch (BusinessException e) {
            reject(binding, e);
            model.addAttribute("editing", true);
            return "users/form";
        }
        redirect.addFlashAttribute("message", "已更新用户「" + form.getUsername() + "」");
        return "redirect:/users";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         Authentication authentication,
                         RedirectAttributes redirect) {
        try {
            userService.delete(id, authentication.getName());
            redirect.addFlashAttribute("message", "已删除该用户");
        } catch (NotFoundException | BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/users";
    }

    private void reject(BindingResult binding, BusinessException e) {
        if (e.getField() != null && binding.getFieldError(e.getField()) == null) {
            binding.rejectValue(e.getField(), "business", e.getMessage());
        } else {
            binding.reject("business", e.getMessage());
        }
    }
}
