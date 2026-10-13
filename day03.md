# Day 03 · V3：用户管理、借阅归还与真实数据

> 目标用时 6 小时。今天把系统**补完**：管理员能管理用户了，读者能借书还书了，图书数据换成真实的豆瓣 Top 250，最后打包成能发出去的 jar。
>
> 今天有一个**真实的设计问题**等着你——不是练习，是当初做这个项目时真的撞上的坑。

---

## 0. 昨天的 V2 检查 + 今天的目标

```bash
./mvnw -q compile && ls data/users.json data/books.json
```

确认两个数据文件都在。

> 📌 **Day 2 打卡**：今天日期 ______，现在你理解"认证和授权是两件事"了吗？用一句话说说。

### 今天的两个缺口

**缺口一：没法加人。**
`admin` 和 `reader` 是硬编码在 `DataInitializer` 里的。想加第三个用户，你得手改 `data/users.json`——**而且密码得是 BCrypt 哈希，你根本算不出来**。所以必须有个界面来创建用户，让系统替你把密码加密。

**缺口二："读者"这个角色名不副实。**
读者的权限是"只能看，不能改"。但这不叫读者，这叫**游客**。真正的读者应该能**借书**、**还书**。

### 今天的时间表

| 时段 | 内容 | 产出 |
|---|---|---|
| 00:00–00:15 | 回顾 + 明确目标 | |
| 00:15–01:15 | 用户管理后端（Form / Service / Controller） | 能创建用户了 |
| 01:15–02:15 | 用户管理页面 + 权限收紧 + 自测 | ✅ 用户 CRUD 可用 |
| 02:15–02:30 | ☕ 休息 | |
| 02:30–03:30 | **借阅 / 归还** | ✅ 读者名副其实 |
| 03:30–04:30 | **豆瓣 Top 250 种子数据 + ISBN 改造** | 真实数据 |
| 04:30–04:45 | ☕ 休息 | |
| 04:45–05:15 | 打包 + README | 可发布的 jar |
| 05:15–06:00 | 三天总验收 + 总复盘 | V3 完成 ✅ |

---

## 1. 用户管理：后端（60 分钟）

### 1.1 为什么需要一个 `UserForm` 而不是直接用 `User`

**因为界面需要的字段和存储需要的字段不一样。**

| | `User`（存 JSON） | `UserForm`（接表单） |
|---|---|---|
| password | BCrypt **哈希** | 用户输入的**明文** |
| 编辑时 | 永远是哈希 | **可以留空**，表示"不修改密码" |

如果直接用 `User` 接表单，就会出现两个尴尬：
1. 编辑页面得把**密码哈希**渲染到输入框里——既没意义（用户不可能编辑哈希），又**泄露**（哈希落到浏览器里了）。
2. 密码留空时无法区分"用户想清空密码"和"用户不想改密码"。

所以：**表单用一个单独的 DTO（数据传输对象）**，控制器负责在 `UserForm` 和 `User` 之间转换。

> 🔎 这个模式叫 **DTO（Data Transfer Object）**。Day 1 我们图省事，`Book` 既当模型又当表单对象——因为两者字段完全一样。**一旦两者开始分叉（就像用户和密码），就该拆开。** 这是个很实用的判断标准。

### 1.2 新建 `UserForm`

新建 `src/main/java/com/library/web/dto/UserForm.java`：

```java
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
```

**重点看密码字段那个正则：**

```java
@Pattern(regexp = "^$|^.{6,100}$", message = "密码长度需在 6~100 之间")
```

**为什么不直接写 `@Size(min = 6, max = 100)`？**

因为 `@Size` 对空串 `""` 会判定为"长度为 0，不满足 min=6"，**报错**。但我们**需要空串合法**（编辑时不改密码）。

用正则 `^$|^.{6,100}$` 的含义是：「**要么是空串，要么是 6~100 个字符**」——正好表达了这个"允许留空，但填了就得够长"的规则。

> 🔎 这是 JSR-303 校验里一个经典的小绕路。记住这个技巧：**"可以留空，但非空时要满足条件"**——用 `@Pattern` 写 `^$|...`，或者干脆在控制器里手动判断（下面 `UserController.create` 就是这么处理"新增时密码必填"的）。

`isNew()` 这个便捷方法后面要在控制器和模板里用。

### 1.3 `UserRepository` 补两个方法

打开 `src/main/java/com/library/repository/UserRepository.java`：

**先在 import 区加上：**

```java
import com.library.model.Role;
```

**然后在 `deleteById` 方法后面，`nextId` 之前插入这两个方法：**

```java
    /** 用户名是否已被占用；{@code excludeId} 用于编辑时排除自身。 */
    public boolean existsByUsername(String username, Long excludeId) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return readAll().stream().anyMatch(user ->
                username.equalsIgnoreCase(user.getUsername())
                        && !(excludeId != null && excludeId.equals(user.getId())));
    }

    /** 当前管理员账号数量，用于「不能删掉最后一个管理员」的校验。 */
    public long countAdmins() {
        return readAll().stream().filter(user -> user.getRole() == Role.ADMIN).count();
    }
```

**`existsByUsername(username, excludeId)` 的 `excludeId` 是干嘛的？**

编辑用户时，"用户名不能重复"这条规则**不该把用户自己算进去**——你打开张三的编辑页，什么都不改直接保存，`existsByUsername("zhangsan")` 会查出张三自己，然后报"用户名已被占用"，**荒谬**。

所以编辑时传自己的 id 进来排除掉。这个 `excludeId` 参数在 Day 1 的 `BookRepository.existsByIsbn` 里也出现过——**同一个模式**。

**`countAdmins()`** 是给"不能删掉最后一个管理员"用的（下面 `UserService.delete` 会用到）。没有这个保护，管理员可能把所有管理员都删光，**然后谁也进不来了**——系统直接锁死。

### 1.4 新建 `UserService`

新建 `src/main/java/com/library/service/UserService.java`：

```java
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
```

**四个关键点：**

**① `update` 里那个 `if`：**

```java
if (form.getPassword() != null && !form.getPassword().isBlank()) {
    existing.setPassword(passwordEncoder.encode(form.getPassword()));
}
```

**留空 = 不动密码。这就是 Day 2 说的"为什么要单独的 UserForm"的答案。** 如果直接用 `User` 接表单，这里就得写"如果密码是哈希格式就不改"这种荒谬判断。

注意这里用的是**同一个 `existing` 对象**（先 `get(id)` 拿到），只改变化的字段。密码没填时，`existing` 里原来的哈希**原封不动**。

**② `create` 里 `passwordEncoder.encode(...)`** —— 明文在这里被加密。**这是整个系统唯一把明文密码变成哈希的地方**（另一处是 Day 2 的 `DataInitializer`）。

**③ `delete` 的两道防线：**

```java
if (target.getUsername().equalsIgnoreCase(currentUsername)) {
    throw new BusinessException("不能删除当前登录的账号");
}
if (target.getRole() == Role.ADMIN && userRepository.countAdmins() <= 1) {
    throw new BusinessException("系统至少需要保留一名管理员");
}
```

**第一道**：不能自杀——否则你刚删完自己，会话就失效了。
**第二道**：不能删掉最后一个管理员——否则系统再也没人能管理了。注意条件是 `countAdmins() <= 1`：如果**当前就只剩一个管理员**，而这个管理员正是要被删的那个，就拒绝。

> 🔎 这类叫**不变量（invariant）保护**——"系统里必须永远至少有一个管理员"。业务规则写在 Service 里，无论从哪个入口删除都会生效。

**④ `delete` 为什么要传 `currentUsername`？** 因为 Service 层**不该知道 HTTP 会话**（那样就耦合到 Web 层了）。所以由 Controller 从 `Authentication` 里取出当前用户名，**作为参数传进来**。这是个重要的分层意识。

### 1.5 新建 `UserController`

新建 `src/main/java/com/library/web/UserController.java`：

```java
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
 * 用户管理：增删改查。整块仅管理员可访问。
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
```

**五个新东西：**

**① 类级别的 `@PreAuthorize("hasRole('ADMIN')")`：**

```java
@Controller
@RequestMapping("/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {
```

注解打在**类**上，表示**这个类里所有方法**都要求管理员。比每个方法贴一遍干净得多。

**② `@ModelAttribute("allRoles")` 方法：**

```java
@ModelAttribute("allRoles")
public Role[] allRoles() {
    return Role.values();
}
```

这个方法会在**本控制器每次请求前**自动执行，往 Model 里放一个 `allRoles`。模板里的角色下拉框直接遍历它——**这样加新角色时，下拉框自动就有了，不用改模板**。

**③ `create` 里的手动校验：**

```java
if (form.isNew() && (form.getPassword() == null || form.getPassword().isBlank())) {
    binding.rejectValue("password", "required", "新增用户时必须设置初始密码");
}
```

`UserForm.password` 的校验规则是"可以留空"。但**新增**时留空是不行的——因为新用户没密码就登不进去。这条规则"依赖上下文"（新增 vs 编辑），注解表达不了，所以在控制器里手动判断。

`binding.rejectValue(...)` 把错误挂到 `password` 字段上，模板里就能显示出来。**注意这是"手动往 BindingResult 里塞错误"的标准做法。**

**④ `editForm` 里的对象转换：**

```java
User user = userService.get(id);
UserForm form = new UserForm();
form.setId(user.getId());
form.setUsername(user.getUsername());
form.setFullName(user.getFullName());
form.setRole(user.getRole());
// 注意：没有 setPassword！
```

**故意不设密码。** 表单里密码框留空，用户看到的是空白 + "（留空表示不修改）"。**哈希永远不会离开服务器。**

**⑤ `delete` 提取当前用户：**

```java
public String delete(@PathVariable Long id, Authentication authentication, ...) {
    userService.delete(id, authentication.getName());
```

`Authentication` 由 Spring Security 注入，`getName()` 就是当前登录的用户名。

**⑥ 注意 `delete` 的异常处理方式和别处不一样：**

```java
try {
    userService.delete(id, authentication.getName());
    redirect.addFlashAttribute("message", "已删除该用户");
} catch (NotFoundException | BusinessException e) {
    redirect.addFlashAttribute("error", e.getMessage());
}
return "redirect:/users";
```

删除失败（比如"不能删除当前登录的账号"）时**不返回表单页**——因为删除请求没有对应的表单。所以改成把错误放进 flash 属性，重定向回列表页，用顶部的**红色提示条**显示。

这是 `reject()` 之外的第二套错误处理方式，**按场景选**：
- **有表单的场景** → 回填表单 + 字段级错误（`reject`）
- **没有表单的场景** → 重定向 + 顶部提示条（flash 属性）

### ✅ 验证

```bash
./mvnw -q compile
```

---

## 2. 用户管理：页面与权限（60 分钟）

### 2.1 用户列表页

新建 `src/main/resources/templates/users/list.html`：

```html
<!DOCTYPE html>
<html lang="zh-CN" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragments/layout :: head('用户管理')}"></head>
<body>
<nav th:replace="~{fragments/layout :: navbar}"></nav>

<main class="container">
    <div class="page-header">
        <h1>用户管理</h1>
        <a class="btn btn-primary" th:href="@{/users/new}">+ 新增用户</a>
    </div>

    <div class="alert alert-success" th:if="${message}" th:text="${message}"></div>
    <div class="alert alert-error" th:if="${error}" th:text="${error}"></div>

    <form class="search-bar" method="get" th:action="@{/users}">
        <input type="text" name="keyword" th:value="${keyword}" placeholder="搜索用户名 / 姓名">
        <button class="btn" type="submit">搜索</button>
        <a class="btn btn-ghost" th:if="${keyword != null and !keyword.isBlank()}" th:href="@{/users}">清除</a>
    </form>

    <div class="table-wrap">
        <table class="table">
            <thead>
            <tr>
                <th>ID</th>
                <th>用户名</th>
                <th>姓名</th>
                <th>角色</th>
                <th class="actions-col">操作</th>
            </tr>
            </thead>
            <tbody>
            <tr th:if="${users.isEmpty()}">
                <td colspan="5" class="empty">没有匹配的用户</td>
            </tr>
            <tr th:each="user : ${users}">
                <td th:text="${user.id}">1</td>
                <td th:text="${user.username}">用户名</td>
                <td th:text="${user.fullName}">姓名</td>
                <td>
                    <span class="tag" th:classappend="${user.role.name() == 'ADMIN'} ? 'tag-admin' : 'tag-reader'"
                          th:text="${user.role.label}">角色</span>
                </td>
                <td class="actions">
                    <a class="btn btn-small btn-ghost" th:href="@{/users/{id}/edit(id=${user.id})}">编辑</a>
                    <form th:action="@{/users/{id}/delete(id=${user.id})}" method="post"
                          onsubmit="return confirm('确定删除该用户吗？此操作不可撤销。');">
                        <button class="btn btn-small btn-danger" type="submit">删除</button>
                    </form>
                </td>
            </tr>
            </tbody>
        </table>
    </div>
</main>

</body>
</html>
```

结构和 `books/list.html` 几乎一样——**这就是把布局抽成片段的好处**，新页面照抄改改就行。

**唯一的新东西是角色标签的着色：**

```html
<span class="tag" th:classappend="${user.role.name() == 'ADMIN'} ? 'tag-admin' : 'tag-reader'"
      th:text="${user.role.label}">角色</span>
```

- `user.role.name()` → 枚举的英文名 `"ADMIN"`
- `user.role.label` → 中文名 `"管理员"`（调的是 `Role.getLabel()`）
- `th:classappend` → 管理员用蓝色标签，读者用灰色

**⚠️ 这里刻意没有显示密码！** 列表页连哈希都不显示。养成习惯：**密码字段永远不出现在任何界面上**。

### 2.2 用户表单页

新建 `src/main/resources/templates/users/form.html`：

```html
<!DOCTYPE html>
<html lang="zh-CN" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragments/layout :: head('用户编辑')}"></head>
<body>
<nav th:replace="~{fragments/layout :: navbar}"></nav>

<main class="container narrow">
    <div class="page-header">
        <h1 th:text="${editing} ? '编辑用户' : '新增用户'">用户</h1>
        <a class="btn btn-ghost" th:href="@{/users}">返回列表</a>
    </div>

    <form class="card form" method="post" th:object="${userForm}"
          th:action="${editing} ? @{/users/{id}(id=${userForm.id})} : @{/users}">

        <div class="alert alert-error" th:if="${#fields.hasGlobalErrors()}">
            <div th:each="err : ${#fields.globalErrors()}" th:text="${err}">错误</div>
        </div>

        <label>
            <span>用户名 *</span>
            <input type="text" th:field="*{username}" autofocus autocomplete="off">
            <small class="field-error" th:if="${#fields.hasErrors('username')}" th:errors="*{username}"></small>
        </label>

        <label>
            <span>密码 <span th:text="${editing} ? '（留空表示不修改）' : '*'">*</span></span>
            <input type="password" th:field="*{password}" autocomplete="new-password">
            <small class="field-error" th:if="${#fields.hasErrors('password')}" th:errors="*{password}"></small>
        </label>

        <label>
            <span>姓名 *</span>
            <input type="text" th:field="*{fullName}">
            <small class="field-error" th:if="${#fields.hasErrors('fullName')}" th:errors="*{fullName}"></small>
        </label>

        <label>
            <span>角色 *</span>
            <select th:field="*{role}">
                <option th:each="r : ${allRoles}" th:value="${r}" th:text="${r.label}">角色</option>
            </select>
            <small class="field-error" th:if="${#fields.hasErrors('role')}" th:errors="*{role}"></small>
        </label>

        <div class="form-actions">
            <button class="btn btn-primary" type="submit">保存</button>
            <a class="btn btn-ghost" th:href="@{/users}">取消</a>
        </div>
    </form>
</main>

</body>
</html>
```

**三个细节：**

1. **密码标签是动态的**：
   ```html
   <span>密码 <span th:text="${editing} ? '（留空表示不修改）' : '*'">*</span></span>
   ```
   编辑时提示"留空表示不修改"，新增时显示必填星号。**同一行代码，两种语义。**

2. **`autocomplete="new-password"`** —— 告诉浏览器这是**新密码**，不要自动填充。如果写成 `current-password`，浏览器会把你登录用的密码填进去，**然后保存后就把那个用户的密码改成你的了**。这是个真实的坑。

3. **角色下拉框**：
   ```html
   <select th:field="*{role}">
       <option th:each="r : ${allRoles}" th:value="${r}" th:text="${r.label}">角色</option>
   </select>
   ```
   数据来自控制器里的 `@ModelAttribute("allRoles")`。`th:field="*{role}"` 会自动选中当前值（编辑时选中的是用户原来的角色）。

### 2.3 加一层 URL 防护

打开 `src/main/java/com/library/security/SecurityConfig.java`，在 `authorizeHttpRequests` 里**插入一行**：

```java
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/favicon.ico").permitAll()
                        .requestMatchers("/login").permitAll()
                        .requestMatchers("/users/**").hasRole("ADMIN")      // ← 新增
                        .anyRequest().authenticated())
```

**为什么既要在 Controller 上写 `@PreAuthorize`，又要在这里配一遍？**

因为两者防的是不同的东西：

| | `SecurityConfig` 的 URL 规则 | `@PreAuthorize` |
|---|---|---|
| 拦截时机 | **进入 Controller 之前**（过滤器层） | 进入方法时（AOP 层） |
| 效果 | 未授权直接 403，**Controller 代码根本不执行** | 同理，但晚一步 |
| 覆盖范围 | 按路径，不关心具体是哪个方法 | 精确到方法 |

**双重保险**：URL 规则挡住整片区域（想加个 `/users/export` 导出接口，自动就在保护范围内，不用记得加注解）；方法注解挡住单个接口。

> 🔎 反过来看 Day 2 的 `BookController`：它的路径是 `/books`，但**读**要开放、**写**要管理员。URL 级没法表达这种"同一个路径下区分方法"的规则，所以那里只用 `@PreAuthorize`。**两个工具，按需选择。**

### 2.4 导航栏加"用户管理"入口

打开 `src/main/resources/templates/fragments/layout.html`，在导航链接里**加一行**：

```html
        <div class="nav-links" th:if="${currentUser != null}">
            <a th:href="@{/}">首页</a>
            <a th:href="@{/books}">图书</a>
            <a th:if="${currentUser.admin}" th:href="@{/users}">用户管理</a>
        </div>
```

`th:if="${currentUser.admin}"` 让读者**看不到**这个入口。

### 2.5 首页加"系统用户"卡片

打开 `src/main/java/com/library/web/HomeController.java`：

**加 import：**
```java
import com.library.service.UserService;
```

**改构造器：**
```java
    private final BookService bookService;
    private final UserService userService;

    public HomeController(BookService bookService, UserService userService) {
        this.bookService = bookService;
        this.userService = userService;
    }
```

**在 `dashboard` 方法末尾、`return "dashboard";` 之前加入：**
```java
        // 用户总数只对管理员可见
        if (principal != null && principal.isAdmin()) {
            model.addAttribute("userCount", userService.count());
        }
```

打开 `src/main/resources/templates/dashboard.html`，在统计卡片区**末尾加一张卡片**：

```html
        <div class="stat-card" th:if="${userCount != null}">
            <span class="stat-value" th:text="${userCount}">0</span>
            <span class="stat-label">系统用户</span>
        </div>
```

**注意这是"条件性属性"**：管理员才往 Model 里放 `userCount`，读者根本拿不到；模板里 `th:if="${userCount != null}"` 决定渲不渲染。**读者连"系统有几个用户"这个信息都不该知道。**

### ✅ 验证

**重启**（改了 Java 代码），用 `admin` 登录：

- [ ] 导航栏出现「用户管理」
- [ ] `/users` 看到 admin 和 reader 两个用户，角色标签颜色不同（管理员蓝、读者灰）
- [ ] 搜索 `zhang` → 匹配到读者的姓名"张三"
- [ ] 新增用户：`lisi / lisi123456 / 李四 / 读者` → 保存成功，列表里多一行
- [ ] **用 `lisi` / `lisi123456` 登录 → 成功** ← **这条最重要，证明密码加密链路是通的**
- [ ] 删除 `lisi` → 成功
- [ ] **尝试删除 `admin`（当前登录账号）→ 提示「不能删除当前登录的账号」**
- [ ] 编辑 `reader`，密码**留空**，只改姓名 → 保存 → **确认 reader 还能用原密码 `reader123` 登录**（证明留空确实没改密码）
- [ ] 用 `reader` 登录 → 导航栏**没有**「用户管理」→ 手动访问 `/users` → **403**
- [ ] 首页：管理员看到 5 张卡片，读者只看到 4 张

---

## 3. 借阅与归还（60 分钟）

### 3.1 先想清楚"借阅"到底改变了什么

我们的 `Book` 有两个数字：

- `totalCopies`：**总册数**，比如图书馆买了 3 本《三体》
- `availableCopies`：**可借册数**，现在书架上还剩几本

**借出一本 = 可借数 −1。还回一本 = 可借数 +1。** `totalCopies` 永远不变。

派生出来的 `borrowedCopies = totalCopies − availableCopies`，Day 1 就用 `@JsonIgnore` 标好了。

**就这么简单。** 这个项目**不记录**"谁借了哪本书"——没有借阅历史表。这是有意的简化，README 里也写明了。想要完整借阅记录，需要再建一个 `BorrowRecord` 模型，那是下一步的事。

### 3.2 `BookRepository` 加一个原子操作

**⚠️ 先想一个问题：为什么不能这样写？**

```java
// ❌ 错误示范
Book book = bookRepository.findById(id).get();
if (book.getAvailableCopies() > 0) {
    book.setAvailableCopies(book.getAvailableCopies() - 1);
    bookRepository.save(book);
}
```

**看起来完全正确。问题在并发。**

想象两个用户**同时**点了《三体》的"借阅"。最后一本了，`availableCopies = 1`。

| 时刻 | 用户 A | 用户 B |
|---|---|---|
| T1 | 读到 availableCopies = 1 | |
| T2 | | 读到 availableCopies = 1 |
| T3 | 判断 1 > 0 ✅ | |
| T4 | | 判断 1 > 0 ✅ |
| T5 | 设为 0，保存 | |
| T6 | | 设为 0，保存 |

**两个人都借成功了，但书只有一本。** 这叫**丢失更新（lost update）**。现实中"借阅按钮被点了两下"就能触发。

**根因**：`读 → 判断 → 写` 这三步**不是一个整体**，中间被别人插了队。

**解法**：把这三步塞进 `JsonFileStore.update()` 的**写锁**里，变成不可分割的**原子操作**。

打开 `src/main/java/com/library/repository/BookRepository.java`，在 `save` 之后、`deleteById` 之前插入：

```java
    /**
     * 原子地把可借册数增减 {@code delta}（借出为负、归还为正）。
     * 若结果越界（小于 0 或超过总册数）或图书不存在则不做改动并返回 false。
     */
    public boolean adjustAvailable(Long id, int delta) {
        if (id == null) {
            return false;
        }
        return update(items -> items.stream()
                .filter(book -> id.equals(book.getId()))
                .findFirst()
                .map(book -> {
                    int total = book.getTotalCopies() == null ? 0 : book.getTotalCopies();
                    int current = book.getAvailableCopies() == null ? 0 : book.getAvailableCopies();
                    int next = current + delta;
                    if (next < 0 || next > total) {
                        return false;
                    }
                    book.setAvailableCopies(next);
                    return true;
                })
                .orElse(false));
    }
```

**为什么这样就安全了？**

因为整段逻辑跑在 `update(...)` 的**写锁**里。`ReentrantReadWriteLock` 的写锁是**互斥**的——用户 B 必须等用户 A 完全做完（包括写回文件）才能进来。**在上面的表格里，B 的 T2 会被阻塞，直到 A 的 T6 完成。等 B 读到数据时，`availableCopies` 已经是 0 了，判断失败，借阅被正确拒绝。**

> 🔎 **这就是 Day 1 那个 `update(Function)` 设计真正的价值。** 当时你可能觉得"为什么要搞个函数传进去"，现在明白了：**它是"原子性地修改数据"的标准入口**。所有 `读-改-写` 都必须走它。

**再看 `next < 0 || next > total` 这个边界检查：**

- `next < 0`：借出时最后一本已经被借走了 → 拒绝
- `next > total`：归还时已经满了 → 拒绝（防止归还操作把可借数刷成 5，超过总册数 3）

**两个越界都在同一个原子操作里拦下**，不会出现"检查通过但写入时状态变了"的问题。

**返回 `boolean` 而不是抛异常**：仓储层不该关心"失败了要怎么通知用户"，那是 Service 的事。

### 3.3 `BookService` 加借还方法

打开 `src/main/java/com/library/service/BookService.java`，在 `delete` 方法之后插入：

```java
    /** 借出一本，可借册数减 1。 */
    public void borrow(Long id) {
        get(id);
        if (!bookRepository.adjustAvailable(id, -1)) {
            throw new BusinessException("该图书已全部借出，暂无可借副本");
        }
    }

    /** 归还一本，可借册数加 1。 */
    public void giveBack(Long id) {
        get(id);
        if (!bookRepository.adjustAvailable(id, 1)) {
            throw new BusinessException("归还失败：可借册数已达总册数");
        }
    }
```

**`get(id)` 是干什么的？** 先确认书存在，不存在就抛 `NotFoundException`（404）。**不然一本不存在的书会得到"已全部借出"这种莫名其妙的提示。**

方法名 `giveBack` 而不是 `return`——因为 `return` 是 Java 关键字，不能当方法名。

### 3.4 `BookController` 加两个接口

打开 `src/main/java/com/library/web/BookController.java`，在 `delete` 之后插入：

```java
    @PostMapping("/{id}/borrow")
    public String borrow(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            bookService.borrow(id);
            redirect.addFlashAttribute("message", "借阅成功");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/books";
    }

    @PostMapping("/{id}/return")
    public String giveBack(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            bookService.giveBack(id);
            redirect.addFlashAttribute("message", "归还成功");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/books";
    }
```

**注意：这两个方法没有 `@PreAuthorize`。** 因为读者也要能借书——**这正是"读者"这个角色的意义**。

成功和失败都**重定向回列表**，只是往不同的 flash 属性里放消息（`message` 绿色 / `error` 红色）。

### 3.5 列表页加按钮

打开 `src/main/resources/templates/books/list.html`，把「操作」列的 `<td class="actions">` 整个替换成：

```html
                <td class="actions">
                    <form th:if="${book.availableCopies != null and book.availableCopies > 0}"
                          th:action="@{/books/{id}/borrow(id=${book.id})}" method="post">
                        <button class="btn btn-small" type="submit">借阅</button>
                    </form>
                    <form th:if="${book.borrowedCopies > 0}"
                          th:action="@{/books/{id}/return(id=${book.id})}" method="post">
                        <button class="btn btn-small btn-ghost" type="submit">归还</button>
                    </form>
                    <a class="btn btn-small btn-ghost" th:if="${currentUser.admin}"
                       th:href="@{/books/{id}/edit(id=${book.id})}">编辑</a>
                    <form th:if="${currentUser.admin}"
                          th:action="@{/books/{id}/delete(id=${book.id})}" method="post"
                          onsubmit="return confirm('确定删除这本书吗？此操作不可撤销。');">
                        <button class="btn btn-small btn-danger" type="submit">删除</button>
                    </form>
                </td>
```

**两个按钮是"条件显示"的，这个细节很重要：**

- **「借阅」**：只在 `availableCopies > 0` 时出现。全借出去了就不显示——**用户不会看到一个点了必然失败的按钮**。
- **「归还」**：只在 `borrowedCopies > 0` 时出现。一本没借出去时，归还按钮没意义。

> 🔎 **这是好的界面设计原则：不要让用户点必然失败的按钮。** 但注意——**这只是体验优化**。忘了吗？用户可以直接 POST。所以 Service 层的 `adjustAvailable` 边界检查**才是真正的防线**，两者缺一不可。
>
> 你可以自己在浏览器里验证这点：借光一本书后，按钮消失了；但如果用 curl 直接 POST，依然会被正确拒绝。

**权限对比一目了然：**

| 按钮 | 谁能看到 |
|---|---|
| 借阅 / 归还 | **所有人**（管理员 + 读者） |
| 编辑 / 删除 | **只有管理员**（`th:if="${currentUser.admin}"`） |

### ✅ 验证

**重启**，用 `reader` 登录，找一本书（比如《活着》总册数 5）：

- [ ] 可借数 5，「借阅」和「归还」按钮都在？—— **不对，「归还」不该在！** 因为 `borrowedCopies = 0`。确认页面上**只有「借阅」**
- [ ] 点一次「借阅」→ 绿色提示「借阅成功」，可借数变 **4**，已借出变 **1**
- [ ] 现在**「归还」按钮出现了**
- [ ] 点「归还」→ 可借数回到 5，**「归还」按钮又消失了**
- [ ] 连点借阅，直到可借数为 0 → **「借阅」按钮消失**，可借数显示为红色徽章
- [ ] 这时再用 curl 强行借：
  ```bash
  # 先登录拿 cookie（把 <id> 换成那本书的 id）
  curl -s -c /tmp/c.txt -b /tmp/c.txt http://localhost:8080/login > /tmp/p.html
  TOKEN=$(grep -o 'name="_csrf" value="[^"]*"' /tmp/p.html | head -1 | sed 's/.*value="//;s/"//')
  curl -s -c /tmp/c.txt -b /tmp/c.txt -d "username=reader&password=reader123&_csrf=$TOKEN" http://localhost:8080/login -o /dev/null
  TOKEN=$(curl -s -c /tmp/c.txt -b /tmp/c.txt http://localhost:8080/books | grep -o 'name="_csrf" value="[^"]*"' | head -1 | sed 's/.*value="//;s/"//')
  curl -s -c /tmp/c.txt -b /tmp/c.txt -d "_csrf=$TOKEN" http://localhost:8080/books/1/borrow -o /dev/null -w "%{http_code}\n"
  ```
  **应该被拒绝**（列表页出现红色提示「该图书已全部借出」），可借数**仍然是 0**。**这就是原子操作在起作用。**
- [ ] 首页的「已借出」数字**跟着变了**（它由 `totalCopies - availableCopies` 算出）

---

## 4. 豆瓣 Top 250：一个真实的设计问题（60 分钟）

### 4.1 先看问题

我们要把系统里的书换成**豆瓣图书 Top 250**——一份真实、体面的数据，比手敲几本《三体》有意思得多。

数据我已经准备好了：`hi-backup/src/main/resources/seed/books.json`（Day 1 备份的那个）。先看一眼：

```bash
head -5 ../hi-backup/src/main/resources/seed/books.json
```

```json
[
  { "title": "红楼梦", "author": "[清] 曹雪芹", "category": "文学", "totalCopies": 8 },
  { "title": "活着", "author": "余华", "category": "小说", "totalCopies": 8 },
  ...
```

**现在盯着这个结构，找出问题。**

...看到了吗？**这些条目里没有 `isbn`。**

而我们的 `Book`：

```java
@NotBlank(message = "ISBN 不能为空")     // ← 必填!
@Size(max = 32, message = "ISBN 不能超过 32 个字符")
private String isbn;
```

**冲突了。** 如果我们直接把这份数据塞进去，250 本书会因为"ISBN 不能为空"全部被拒绝——**一本都存不进去**。

### 4.2 三个选项，怎么选？

| 方案 | 做法 | 代价 |
|---|---|---|
| **A. 给 250 本书补 ISBN** | 一本本去豆瓣查 | 250 次查询；豆瓣**没有**批量 ISBN 接口；很多书有多个版本、多个 ISBN；**几小时都做不完，还不准** |
| **B. 放弃这份数据** | 继续用手敲的书 | 失去了真实数据，练习也就没意思了 |
| **C. 把 ISBN 改成选填** | 改一行注解 | 需要想清楚"唯一性校验"怎么办 |

**这就是真实的工程决策。** 不是"哪个技术上最优"，而是"在时间和准确性约束下哪个值得做"。

当初做这个项目时选的是 **C**。理由：

- **ISBN 本质上不是"每本书都必须有的属性"。** 图书馆里还有古籍、内部资料、没有 ISBN 的引进版——**现实中的书目本来就允许没有 ISBN**。
- 原来的"必填"，是我们**假设**错了，不是数据有问题。

**这个认知很重要**：当数据和模型打架时，先问一句"**是不是我模型建错了**"，而不是急着去修改数据迁就模型。

### 4.3 动手改

**① `Book.isbn` 去掉 `@NotBlank`**

打开 `src/main/java/com/library/model/Book.java`，把 ISBN 字段改成：

```java
    /**
     * ISBN 选填：豆瓣图书等来源的书目不一定带 ISBN，留空时不影响保存与搜索。
     * 非空时仍参与唯一性校验。
     */
    @Size(max = 32, message = "ISBN 不能超过 32 个字符")
    private String isbn;
```

**只删掉了 `@NotBlank` 那一行。** `@Size` 保留——填了还是不能超过 32 字符。

> ⚠️ `@NotBlank` 的 import 还不能删，因为 `title` 和 `author` 还在用它。

**② 唯一性校验要改吗？——不用，先去看看**

这是**关键一步，别急着改代码**。打开 `src/main/java/com/library/repository/BookRepository.java`，看 `existsByIsbn`：

```java
public boolean existsByIsbn(String isbn, Long excludeId) {
    if (isbn == null || isbn.isBlank()) {     // ← 看这里
        return false;
    }
    ...
}
```

**第一行就短路返回 `false` 了！** 意思是"空 ISBN 永远不算重复"。

**所以 `BookService.validate()` 一行都不用改。** 结果就是：

- 250 本没有 ISBN 的书**能共存**（不会互相判重）
- 但如果有人填了 ISBN，**依然**会和别的书查重

**这个 `if (isbn == null || isbn.isBlank()) return false;` 是当初写 `existsByIsbn` 时就顺手加的边界保护。** 当时不知道会有"没有 ISBN 的书"，只是觉得"空值不该参与查重"。**这就是防御性编程的回报**——今天的改动只需要一行。

> 🔎 反过来想：如果当初 `existsByIsbn` 直接 `isbn.equalsIgnoreCase(...)`，今天就 `NullPointerException` 了。**写代码时多想一步边界情况，未来的你会感谢现在的你。**

**③ 界面上标记"选填"**

打开 `src/main/resources/templates/books/form.html`，把 ISBN 那一段改成：

```html
            <label>
                <span>ISBN（选填）</span>
                <input type="text" th:field="*{isbn}" placeholder="可留空">
                <small class="field-error" th:if="${#fields.hasErrors('isbn')}" th:errors="*{isbn}"></small>
            </label>
```

**④ 列表页留空时显示占位符**

打开 `src/main/resources/templates/books/list.html`，把 ISBN 那一格改成：

```html
                <td th:text="${#strings.isEmpty(book.isbn) ? '—' : book.isbn}">ISBN</td>
```

`#strings.isEmpty(...)` 是 Thymeleaf 内置工具：`null` 或空串都算空。空的时候显示一个破折号 `—`，而不是刺眼的空白。

### 4.4 让种子数据自动导入

现在数据文件有了、模型也改好了。**缺的是"怎么把它读进来"。**

回忆 Day 2 的 `DataInitializer`——它只种了用户。现在给它加上种图书。

打开 `src/main/java/com/library/service/DataInitializer.java`，**整个文件替换**成：

```java
package com.library.service;

import com.library.model.Book;
import com.library.model.Role;
import com.library.model.User;
import com.library.repository.BookRepository;
import com.library.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * 首次启动时写入默认账号与图书种子数据。已有数据时不做任何事。
 *
 * <p>图书种子来自 classpath 下的 {@code seed/books.json}（豆瓣图书 Top 250 快照），
 * 每条只需 {@code title/author/category/totalCopies}，ISBN 选填。
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    /** 种子图书资源路径。 */
    private static final String SEED_BOOKS_RESOURCE = "seed/books.json";

    /** 种子条目未指定册数时使用的默认值。 */
    private static final int DEFAULT_COPIES = 5;

    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper mapper;

    public DataInitializer(UserRepository userRepository,
                           BookRepository bookRepository,
                           PasswordEncoder passwordEncoder,
                           ObjectMapper mapper) {
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
        this.passwordEncoder = passwordEncoder;
        this.mapper = mapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedUsers();
        seedBooks();
    }

    private void seedUsers() {
        if (!userRepository.findAll().isEmpty()) {
            return;
        }
        userRepository.save(new User("admin", passwordEncoder.encode("admin123"), "系统管理员", Role.ADMIN));
        userRepository.save(new User("reader", passwordEncoder.encode("reader123"), "张三", Role.READER));
        log.info("已初始化默认账号：admin / admin123（管理员）、reader / reader123（读者）");
    }

    private void seedBooks() {
        if (!bookRepository.findAll().isEmpty()) {
            return;
        }
        List<SeedBook> seeds = loadSeedBooks();
        for (SeedBook seed : seeds) {
            int copies = seed.totalCopies() == null ? DEFAULT_COPIES : seed.totalCopies();
            bookRepository.save(new Book(seed.title(), seed.author(), seed.isbn(), seed.category(), copies));
        }
        log.info("已从 {} 初始化 {} 本图书", SEED_BOOKS_RESOURCE, seeds.size());
    }

    private List<SeedBook> loadSeedBooks() {
        ClassPathResource resource = new ClassPathResource(SEED_BOOKS_RESOURCE);
        if (!resource.exists()) {
            log.warn("未找到种子图书文件 {}，跳过图书初始化", SEED_BOOKS_RESOURCE);
            return List.of();
        }
        try (InputStream in = resource.getInputStream()) {
            List<SeedBook> seeds = mapper.readValue(in, mapper.getTypeFactory()
                    .constructCollectionType(List.class, SeedBook.class));
            return seeds == null ? List.of() : seeds;
        } catch (IOException e) {
            throw new UncheckedIOException("读取种子图书文件失败: " + SEED_BOOKS_RESOURCE, e);
        } catch (JacksonException e) {
            throw new IllegalStateException("解析种子图书文件失败: " + SEED_BOOKS_RESOURCE, e);
        }
    }

    /** 种子文件中的图书条目。ISBN 选填。 */
    private record SeedBook(String title, String author, String isbn, String category, Integer totalCopies) {
    }
}
```

#### 逐块解读

**① 为什么用 `ClassPathResource` 而不是 `new File("...")`？**

```java
ClassPathResource resource = new ClassPathResource(SEED_BOOKS_RESOURCE);
```

`ClassPathResource` 从 **classpath** 读文件。`src/main/resources/` 下的东西在编译后会被复制到 classpath 根目录。

**关键差别**：打成 jar 之后，`src/main/resources/...` 这个路径**根本不存在**了——文件被封在 jar 内部。**用 `new File()` 在 IDE 里能跑，打包后必崩。** `ClassPathResource` 两种情况下都能工作，因为它走的是**类加载器**，认的是 jar 内部的资源。

> 🔎 **这类"本地能跑、打包就崩"的 bug 特别难查**，因为你测试时用的是 IDE。**记住这个规则：凡是读随程序打包的文件，一律用 `ClassPathResource` / `getResourceAsStream`。**

**② 为什么用 `SeedBook` 这个 private record 而不是直接用 `Book`？**

```java
private record SeedBook(String title, String author, String isbn, String category, Integer totalCopies) {
}
```

因为**种子文件的形状和 `Book` 不一样**：

| | `Book` | `SeedBook` |
|---|---|---|
| 字段 | id, title, author, isbn, category, totalCopies, availableCopies | 只有 title/author/isbn/category/totalCopies |
| 用途 | 完整实体 | 只描述"要造一本什么样的书" |

种子文件里**没有** `availableCopies`（新书当然全部可借，由 `Book` 的构造器自动设为 = totalCopies）。**用一个专门的 record 描述"输入的形状"，比硬塞进实体、盼着字段对上要清晰得多。**

而且 `record` 在这里**完美**：它就是个纯数据的、不可变的载体，没有行为。Jackson 3 **原生支持 record 反序列化**（通过构造器），不用写任何注解。

**③ 异常处理的两种类型：**

```java
} catch (IOException e) {
    throw new UncheckedIOException("读取种子图书文件失败: " + SEED_BOOKS_RESOURCE, e);
} catch (JacksonException e) {
    throw new IllegalStateException("解析种子图书文件失败: " + SEED_BOOKS_RESOURCE, e);
}
```

- `IOException` 是**受检异常**（读文件可能失败），包成 `UncheckedIOException` 抛出去——这个场景下没法优雅恢复，直接让启动失败并给出清晰信息。
- `JacksonException` 是**非受检的**（Jackson 3 的特性，Day 1 讲过）。**它不是 `IOException` 的子类**，所以要单独 catch——顺序无所谓，因为两者没有继承关系。

**④ 幂等保护照旧：**

```java
if (!bookRepository.findAll().isEmpty()) {
    return;
}
```

已有图书就跳过。**所以你手动加的书不会被 250 本种子覆盖。**

### 4.5 放文件、清数据、启动

**① 把种子文件放进去：**

```bash
mkdir -p src/main/resources/seed
cp ../hi-backup/src/main/resources/seed/books.json src/main/resources/seed/books.json
python3 -c "import json;d=json.load(open('src/main/resources/seed/books.json'));print('条目数:',len(d))"
```

应该输出 **条目数: 250**。

**② 清掉旧的图书数据**（因为 `seedBooks()` 有幂等保护，不清的话不会导入）：

```bash
rm -f data/books.json
```

> ⚠️ **`data/users.json` 千万不要删**——那里面有你今天新建的用户。只删 `books.json`。

**③ 重启：**

```bash
./mvnw spring-boot:run
```

日志里应该出现：

```
已从 seed/books.json 初始化 250 本图书
```

### ✅ 验证

- [ ] `python3 -c "import json;d=json.load(open('data/books.json'));print(len(d))"` → **250**
- [ ] 图书列表一页能看到 250 行，ISBN 列**全部显示 `—`**
- [ ] 搜索 `科幻` → 应该有一批（约 13 本）
- [ ] 搜索 `余华` → 三本左右（作者的其它作品）
- [ ] 搜索 `三体` → 命中《三体全集》
- [ ] 新增一本书，**ISBN 留空** → **能保存成功** ✅（证明 ISBN 真的变成选填了）
- [ ] 新增一本书，ISBN 填 `ABC123`，再加一本也填 `ABC123` → **第二本被拒绝**（唯一性校验依然有效）
- [ ] 首页统计：**250 图书种类**，总册数约 **1200**

---

## 5. 打包与收尾（30 分钟）

### 5.1 打包成可执行 jar

```bash
./mvnw -B -q clean package
ls -lh target/hi-1.0.0.jar
```

`spring-boot-maven-plugin` 打的包是**"胖 jar"（fat jar）**——把 Spring、Tomcat、你的代码**全部打进去**，一个文件就能跑。这是 Spring Boot 最实用的特性之一。

**跑起来：**

```bash
java -jar target/hi-1.0.0.jar
```

不需要 Maven、不需要 Tomcat，**只要有 JDK 就能运行**。这就是交付形态。

> 🔎 **注意 `clean`**：它会删掉 `target/` 重新构建。养成习惯——**打包前先 `clean`**，避免残留的旧 class 文件混进包里。（`target/` 已经被 `.gitignore` 忽略了。）

> ⚠️ **一个必须知道的坑**：`data/` 目录是**相对于"启动目录"**创建的。在项目根目录 `java -jar target/hi-1.0.0.jar`，数据就在 `项目根/data/`。如果你 `cd target && java -jar hi-1.0.0.jar`，数据就会跑到 `target/data/` 去——**看起来像是"数据全没了"**。想换位置就改 `application.yml` 里的 `library.data-dir`，或者用绝对路径。

### 5.2 更新 README

打开 `README.md`，检查这几处是否和现在的代码一致：

- 默认账号（`admin` / `admin123`、`reader` / `reader123`）
- 首次启动会导入 `seed/books.json` 的 250 本书
- 校验规则那条改成：**ISBN 选填**，填了才做唯一性校验；可借册数不得超过总册数
- 重置说明：停掉应用、删掉 `data/` 目录再启动

**这一步的意义**：你三天后回来看，README 是你唯一的"说明书"。**写清楚"怎么跑"和"有哪些坑"，比写漂亮的功能列表重要。**

---

## 6. 三天总验收

**完全重置，走一遍完整流程。** 这一步必须做——它验证的是一个**新人拿到这个项目能不能跑通**。

```bash
# 1. 停掉所有运行中的实例
lsof -ti:8080 | xargs kill -9 2>/dev/null

# 2. 回到零状态
rm -rf data/

# 3. 干净构建
./mvnw -B -q clean package

# 4. 用 jar 启动（不是 mvnw，验证打包产物）
java -jar target/hi-1.0.0.jar
```

日志应该同时出现：
```
已初始化默认账号：admin / admin123（管理员）、reader / reader123（读者）
已从 seed/books.json 初始化 250 本图书
```

**然后逐条走：**

**A. 匿名**
- [ ] 访问 `http://localhost:8080` → 跳转 `/login`
- [ ] 直接访问 `/books` → 跳转 `/login`

**B. 管理员**
- [ ] `admin` / `admin123` 登录
- [ ] 首页：250 图书种类 / ~1200 总册数 / 系统用户卡片显示 **3**（admin、reader、可能还有你昨天建的）
- [ ] 导航栏：首页 / 图书 / **用户管理** / 退出
- [ ] 图书列表 250 行，ISBN 全是 `—`
- [ ] 新增一本无 ISBN 的书 → 成功
- [ ] 编辑它 → 改成有 ISBN → 成功
- [ ] 删除它 → 成功
- [ ] 借阅一本 → 可借数 −1
- [ ] 归还 → 回到原值
- [ ] 用户管理：新增用户 `test01 / test123456 / 测试员 / 读者` → 成功
- [ ] 用 `test01` / `test123456` 登录 → **成功**
- [ ] 删除 `admin` → **拒绝**（当前登录账号）
- [ ] 删除 `test01` → 成功

**C. 读者**
- [ ] `reader` / `reader123` 登录
- [ ] 导航栏**没有**「用户管理」
- [ ] 手敲 `/users` → **403**
- [ ] 手敲 `/books/new` → **403**
- [ ] 图书列表能看，但「操作」列**只有借阅/归还，没有编辑/删除**
- [ ] 借阅 → 成功；归还 → 成功
- [ ] 首页**只有 4 张卡片**（没有"系统用户"）

**D. 持久化**
- [ ] `Ctrl+C` 停掉
- [ ] `java -jar target/hi-1.0.0.jar` 重新启动
- [ ] 用刚才新建的 `test01` 登录（如果没删）→ **成功**
- [ ] 图书数据、借阅状态**全部保留**
- [ ] 这次启动**不再打印**"已初始化"，因为数据已存在（**幂等生效**）

**全部通过 = V3 完成 = 整个项目完成** 🎉

---

## 7. 常见坑速查

| 现象 | 原因 | 解决 |
|---|---|---|
| 删掉 `data/` 后种子没导入 | 文件不存在 / 路径不对 | 确认 `src/main/resources/seed/books.json` 存在，且名字大小写一致 |
| 种子文件在 IDE 里能读、打包后报错 | 用了 `new File()` | 改用 `ClassPathResource` |
| 250 本书导入失败，报"ISBN 不能为空" | `@NotBlank` 没删掉 | 检查 `Book.isbn` 上只剩 `@Size` |
| 加了 250 本书后 ISBN 查重总是失败 | `existsByIsbn` 的空值短路被删了 | 确认开头有 `if (isbn == null \|\| isbn.isBlank()) return false;` |
| 编译报 `找不到符号: record` | Java 版本低于 16 | 确认 `pom.xml` 里 `<java.version>27</java.version>` |
| 借阅按钮点了没反应/数字不变 | `adjustAvailable` 没走 `update()` | 确认整个逻辑包在 `update(items -> ...)` 里 |
| 归还后可借数超过总册数 | 少了 `next > total` 检查 | 补上边界判断 |
| 用户密码改了但登录不上 | 编辑时密码留空被当成了"空密码" | 确认 `UserService.update` 里有 `!isBlank()` 判断 |
| 页面报 500，日志是 `NullPointerException` | 某个 Model 属性没有，模板却直接取值 | 检查控制器有没有 `addAttribute` |
| `java -jar` 启动后数据"消失" | 启动目录不同，`data/` 建在了别处 | 在项目根目录启动，或配置绝对路径 |

---

## 8. 三天总复盘

这是最重要的部分。**合上所有代码**，试着回答：

### 架构

1. **画出这个系统的分层图**：浏览器 → ? → ? → ? → JSON 文件。每一层负责什么？
2. **为什么要有 Service 层？** Controller 直接调 Repository 会怎样？（提示：ISBN 校验、借阅边界检查放哪？如果将来加一个 REST API，这些规则要不要重写？）
3. **`JsonFileStore<T>` 泛型基类带来了什么？** 如果不用它，`BookRepository` 和 `UserRepository` 会有多少重复代码？

### 关键决策

4. **`update(Function)` 为什么要传函数，而不是提供 `read()` + `write()` 两个方法？** 用你 Day 3 学到的"丢失更新"例子回答。
5. **ISBN 从必填改成选填**——这个决定里，哪个部分最值钱？（提示：不是"删一行注解"，而是"**先去看 `existsByIsbn` 会不会炸**"）
6. **`@PreAuthorize` 和 `th:if` 的分工是什么？** 如果只能保留一个，保留哪个？为什么？

### 安全

7. 列出这个系统里**所有**和密码有关的地方，说明每一处明文/哈希的状态。
8. **CSRF 保护挡的是什么攻击？** 为什么 `th:action` 就能解决？
9. 如果让你把这个系统部署到公网，**至少还要做哪 5 件事**？（提示：HTTPS、默认密码、日志、限流、备份……）

### 反思

10. **这三天里，哪一处改动最小但收益最大？**（候选：`@PreAuthorize` 加 5 行、ISBN 删 1 行、`CurrentUser` 一个 record）
11. **哪一处是"当时顺手写的、今天救了命"的？**（提示：`existsByIsbn` 的第一行）
12. **如果重来一次，你会怎么改 Day 1 的设计？**

### 🛠 继续做下去

项目到这儿功能齐了，但离"能上线"还远。按难度排序：

1. **借阅记录**：新建 `BorrowRecord`（谁、哪本书、什么时候借的、还没还），列表页显示"你借了 3 本"。**这是最自然的下一步**，你会第一次遇到"模型之间有关联"。
2. **分页**：250 本书一页显示确实太多了。
3. **改密码功能**：让用户自己改密码，需要"输入旧密码验证"。
4. **写测试**：`src/test/java` 还是空的。从 `BookService` 的借还逻辑开始写单元测试——**尤其是并发那条**，用多线程测试 `adjustAvailable` 绝不越界。
5. **换成真数据库**：把 `JsonFileStore` 换成 Spring Data JPA + SQLite/H2。**你会发现改动被限制在 repository 层**——这就是分层的回报。
6. **导出 / 导入**：管理员导出所有图书为 CSV。

---

## 9. 恭喜

三天前你面对的是一个 `src/Main.java` 的空工程。现在你有的东西：

| | 能力 |
|---|---|
| **框架** | Spring Boot 4 + Spring MVC + Thymeleaf + Spring Security |
| **功能** | 登录认证、角色授权、图书 CRUD、用户 CRUD、借阅归还、种子数据 |
| **数据** | JSON 文件持久化，带读写锁与原子更新 |
| **安全** | BCrypt 密码、CSRF 防护、三层权限控制、越界防护 |
| **工程** | Maven Wrapper、可执行 jar、分层架构、DTO 模式 |
| **数据** | 豆瓣 Top 250 真实书目 |

**比这些更重要的是你建立的几个判断力：**

- 看到"数据和模型打架"，先问**是不是模型错了**
- 看到 `读-判断-写`，先问**并发下会不会错**
- 看到"前端隐藏了"，先问**绕过界面还能不能用**
- 看到要改一处，先问**为什么只需要改这一处**

第四个问题的答案是**分层**。这就是这三天最核心的东西——**不是记住了多少注解，而是知道改动应该落在哪一层。**

**下一步建议**：挑上面"继续做下去"里的第 1 条（借阅记录）动手。它足够小（半天能做完），又足够新（第一次处理模型关联），是巩固这三天的好方式。

---

**V3 打卡：** 当 `java -jar target/hi-1.0.0.jar` 跑起来、250 本书出现在页面上时，写下今天的日期 ______。

三天完成。🎓
