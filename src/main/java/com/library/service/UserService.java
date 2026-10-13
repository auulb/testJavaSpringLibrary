package com.library.service;

import com.library.model.Role;
import com.library.model.User;
import com.library.repository.UserRepository;
import com.library.web.dto.UserForm;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户业务逻辑：增删改查。密码统一用 BCrypt 哈希后存储。
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public User get(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("用户不存在（id=" + id + "）"));
    }

    public User create(UserForm form) {
        if (userRepository.existsByUsername(form.getUsername(), null)) {
            throw new BusinessException("username", "用户名「" + form.getUsername() + "」已被占用");
        }
        User user = new User();
        user.setUsername(form.getUsername().trim());
        user.setFullName(form.getFullName().trim());
        user.setRole(form.getRole());
        user.setPassword(passwordEncoder.encode(form.getPassword()));
        return userRepository.save(user);
    }

    /** 更新用户；{@code form.password} 留空表示保留原密码。 */
    public User update(Long id, UserForm form) {
        User existing = get(id);
        if (userRepository.existsByUsername(form.getUsername(), id)) {
            throw new BusinessException("username", "用户名「" + form.getUsername() + "」已被占用");
        }
        existing.setUsername(form.getUsername().trim());
        existing.setFullName(form.getFullName().trim());
        existing.setRole(form.getRole());
        if (form.getPassword() != null && !form.getPassword().isBlank()) {
            existing.setPassword(passwordEncoder.encode(form.getPassword()));
        }
        return userRepository.save(existing);
    }

    /**
     * 删除用户。禁止删除当前登录账号，也禁止删掉最后一个管理员，
     * 以免把自己锁在系统外。
     */
    public void delete(Long id, String currentUsername) {
        User target = get(id);
        if (target.getUsername().equalsIgnoreCase(currentUsername)) {
            throw new BusinessException("不能删除当前登录的账号");
        }
        if (target.getRole() == Role.ADMIN && userRepository.countAdmins() <= 1) {
            throw new BusinessException("系统至少需要保留一名管理员");
        }
        userRepository.deleteById(id);
    }

    public List<User> search(String keyword) {
        List<User> all = userRepository.findAll();
        if (keyword == null || keyword.isBlank()) {
            return all;
        }
        String needle = keyword.trim().toLowerCase();
        return all.stream()
                .filter(user -> contains(user.getUsername(), needle) || contains(user.getFullName(), needle))
                .toList();
    }

    public long count() {
        return userRepository.findAll().size();
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }
}
