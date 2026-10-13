package com.library.repository;

import tools.jackson.databind.ObjectMapper;
import com.library.config.LibraryProperties;
import com.library.model.Role;
import com.library.model.User;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 用户仓储，数据落在 {@code data/users.json}。
 */
@Repository
public class UserRepository extends JsonFileStore<User> {

    public UserRepository(ObjectMapper mapper, LibraryProperties properties) {
        super(mapper, properties.dataDirPath(), "users.json", User.class);
    }

    public List<User> findAll() {
        return readAll().stream()
                .sorted(Comparator.comparing(User::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public Optional<User> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return readAll().stream().filter(user -> id.equals(user.getId())).findFirst();
    }

    public Optional<User> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return readAll().stream()
                .filter(user -> username.equalsIgnoreCase(user.getUsername()))
                .findFirst();
    }

    /** 用户名是否已被占用；{@code excludeId} 用于编辑时排除自身。 */
    public boolean existsByUsername(String username, Long excludeId) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return readAll().stream().anyMatch(user ->
                username.equalsIgnoreCase(user.getUsername())
                        && !(excludeId != null && excludeId.equals(user.getId())));
    }

    /** 新增（id 为 null）或更新（id 已存在）一个用户，返回保存后的对象。 */
    public User save(User user) {
        return update(items -> {
            if (user.getId() == null) {
                user.setId(nextId(items));
                items.add(user);
                return user;
            }
            for (int i = 0; i < items.size(); i++) {
                if (user.getId().equals(items.get(i).getId())) {
                    items.set(i, user);
                    return user;
                }
            }
            items.add(user);
            return user;
        });
    }

    public boolean deleteById(Long id) {
        return update(items -> items.removeIf(user -> id != null && id.equals(user.getId())));
    }

    /** 当前管理员账号数量，用于「不能删掉最后一个管理员」的校验。 */
    public long countAdmins() {
        return readAll().stream().filter(user -> user.getRole() == Role.ADMIN).count();
    }

    private Long nextId(List<User> items) {
        return items.stream()
                .map(User::getId)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .orElse(0L) + 1;
    }
}
