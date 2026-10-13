# Day 02 · V2：登录、角色与权限

> 目标用时 6 小时。今天你会给 V1 装上**身份**：登录页、用户模型、BCrypt 密码、管理员 / 读者两种角色，以及「谁才能改书」的权限控制。
>
> 今天结束你会体会到一件事：**因为 Day 1 分了层，加权限只需要改很少的地方。**

---

## 0. 昨天的 V1 检查

先确认 Day 1 是通的：

```bash
./mvnw -q compile && ls data/books.json
```

`data/books.json` 应该有你昨天加的书。**如果昨天删光了书也没关系**，今天不影响。

> 📌 **Day 1 打卡**（给自己留一句）：今天日期 ______，昨天最有收获的一点是 ______________________。

---

## 1. 今天的时间表

| 时段 | 内容 | 产出 |
|---|---|---|
| 00:00–00:30 | 加入 Security 依赖，观察「神奇的默认登录页」 | 理解认证 vs 授权 |
| 00:30–01:10 | `User` / `Role` 模型 + `UserRepository` | 用户能存进 JSON 了 |
| 01:10–02:10 | 密码加密、`UserDetailsService`、`SecurityConfig` | 能用自己的用户登录 |
| 02:10–02:25 | ☕ 休息 | |
| 02:25–03:10 | `DataInitializer` 造两个默认账号 + 跑通登录 | ✅ 登录成功 |
| 03:10–04:00 | 登录页 + 首页概览 | 好看的界面 |
| 04:00–04:15 | ☕ 休息 | |
| 04:15–05:15 | **权限控制**：`@PreAuthorize` + 模板按角色隐藏菜单 | 读者改不了书 |
| 05:15–06:00 | 自测两套流程 + 复盘 | V2 完成 ✅ |

---

## 2. 引入 Spring Security：先看一个神奇现象（30 分钟）

### 2.1 加依赖

打开 `pom.xml`，在 `spring-boot-starter-validation` 那一段**后面**插入：

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
```

然后重启：

```bash
./mvnw spring-boot:run
```

### 2.2 观察

打开 <http://localhost:8080> —— 昨天还好好的应用，**现在跳到了一个登录页**，样式很朴素，标题写着 "Please sign in"。

更神奇的是，**控制台日志里多了这么一行**：

```
Using generated security password: 8e557245-73e2-418c-9682-6db5f78444b0
```

（每次启动密码都不一样）

用用户名 `user` + 上面这个密码，**能登录进去**，然后就能正常用你的图书系统了。

### 2.3 这是怎么回事？

**你只加了一个依赖，一行配置都没写，登录功能就出现了。** 这就是 Spring Boot 的"约定优于配置"——它检测到 classpath 上有 Spring Security，就自动装了一套**默认安全配置**：

- 所有请求都需要登录
- 自动生成一个用户 `user`，密码随机打印在日志里
- 提供一个默认登录页

**方便，但不能用于生产**。因为①用户只能是那个随机的 `user` ②密码重启就变 ③拿不到密码就没法登录。所以我们要**接管**这套配置，用自己的用户体系。

### 2.4 两个必须先分清的概念

| | 认证 (Authentication) | 授权 (Authorization) |
|---|---|---|
| 回答的问题 | **你是谁？** | **你能干什么？** |
| 对应的功能 | 登录、密码校验 | 角色、权限判断 |
| 今天的实现 | `UserDetailsService` + `PasswordEncoder` | `Role` + `@PreAuthorize` + `SecurityFilterChain` |
| 失败的表现 | 跳回登录页 | **403 Forbidden** |

**顺序不能颠倒**：先认证（登录），才能授权（判断你能不能删书）。

> 🛑 先把程序 `Ctrl+C` 停掉。下面开始接管配置。

---

## 3. 用户模型与仓储（40 分钟）

### 3.1 角色枚举 `Role`

新建 `src/main/java/com/library/model/Role.java`：

```java
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
```

**为什么用枚举而不是字符串？** 因为字符串写错了编译器不报错——`"ADMID"` 要等到运行时才发现。枚举写错**编译不通过**。而且这里顺手给每个枚举值挂了个中文名，页面直接 `role.label` 就能取到。

### 3.2 用户模型 `User`

新建 `src/main/java/com/library/model/User.java`：

```java
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
```

和 `Book` 一样，`@JsonIgnore` 保护派生字段 `isAdmin()`（由 `role` 算出来的，不存 JSON）。

### 3.3 `UserRepository`

新建 `src/main/java/com/library/repository/UserRepository.java`：

```java
package com.library.repository;

import tools.jackson.databind.ObjectMapper;
import com.library.config.LibraryProperties;
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

    private Long nextId(List<User> items) {
        return items.stream()
                .map(User::getId)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .orElse(0L) + 1;
    }
}
```

**注意到什么了吗？** 这个类和 `BookRepository` **结构几乎一模一样**——`findAll` / `findById` / `save` / `deleteById` / `nextId` 全都对得上，只是类型从 `Book` 变成 `User`，多了一个 `findByUsername`。

**这就是 Day 1 的 `JsonFileStore<T>` 的价值。** 如果当初把读写逻辑直接写在 `BookRepository` 里，今天就得把那段代码再抄一遍（然后在改 bug 时改两处、漏一处）。**抽象公共部分，是这个项目里最值得学的一个决定。**

> 🔎 `findByUsername` 用 `equalsIgnoreCase` 做**忽略大小写**的匹配——登录时输 `Admin` 也能进。而 `Book` 的 ISBN 查重也用忽略大小写。这是个有意的设计：用户不该因为大小写被挡在外面。

### ✅ 验证

```bash
./mvnw -q compile
```

---

## 4. 接管认证：密码 + 用户加载（60 分钟）

### 4.1 为什么密码绝对不能存明文

想象一下 `data/users.json` 长这样：

```json
[ { "username": "admin", "password": "admin123", ... } ]
```

**这是灾难性的**。数据库泄露、员工误发文件、服务器被入侵——任何一个环节，所有人的密码就直接暴露了。而且很多人多个网站用同一个密码，一个库泄露会连累一堆账号。

**正确做法：哈希（hash）。** 存的是密码经过单向函数计算出的乱码：

```
"admin123"  →  $2a$10$N9qo8uLOickgx2ZMRZoMye...  （存这个）
```

哈希的两个关键性质：
1. **单向**——从 `$2a$10$...` 反推不出 `admin123`。
2. **验证方式不同**——登录时不"解密"，而是把用户刚输入的密码**用同样的算法算一遍**，比较两个哈希是否一致。

**BCrypt 是专门为密码设计的哈希算法**，额外做了两件事：
- **加盐 (salt)**：同样的密码，每次哈希结果都不同（盐是随机插进去的）。所以两个人密码都是 `123456`，存出来的是两串完全不同的乱码。这挡住了"彩虹表"攻击。
- **故意慢**：BCrypt 计算一次要几十毫秒。对正常登录毫无影响，但让暴力破解的成本变得极高。

Spring Security 提供了现成的 `BCryptPasswordEncoder`，**我们只需要声明它是 Bean**。

### 4.2 `LibraryUserPrincipal`

**问题**：Spring Security 需要知道"当前登录的是谁"，它内部的接口是 `UserDetails`。我们的 `User` 类没有实现它——而且**我们也不想让持久化模型去实现框架接口**（那就把模型和框架绑死了）。

**做法**：写一个适配类，把 `User` 包一层。

新建 `src/main/java/com/library/security/LibraryUserPrincipal.java`：

```java
package com.library.security;

import com.library.model.Role;
import com.library.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security 的登录主体，额外携带用户 id、姓名与角色，方便页面直接取用。
 */
public class LibraryUserPrincipal implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final String fullName;
    private final Role role;

    public LibraryUserPrincipal(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.fullName = user.getFullName();
        this.role = user.getRole();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
```

**关键在 `getAuthorities()`：**

```java
new SimpleGrantedAuthority("ROLE_" + role.name())
```

`role.name()` 是枚举的英文名（`ADMIN` / `READER`），前面拼上 `ROLE_`，得到 `ROLE_ADMIN` / `ROLE_READER`。

> ⚠️ **`ROLE_` 前缀是必须的**，这是 Spring Security 的约定。后面写 `hasRole("ADMIN")` 时它会自动补上前缀去比对；你如果在这里写成 `"ADMIN"`，权限判断就会永远失败，而且**报错不明显**——只会表现为 403。这是新手最容易卡住的地方之一。

另外那四个 `isXxx()` 方法都返回 `true`，意思是"账号没过期、没锁定、凭证没过期、已启用"。这个演示项目用不到这些功能，但**接口要求必须实现**。这也是为什么我们要写适配类——把这些样板代码挡在 `User` 之外。

**类里额外加的 `getId()` / `getFullName()` / `getRole()` / `isAdmin()`** 是给页面用的（比如导航栏显示"张三 · 读者"）。`UserDetails` 接口本身不要求这些。

### 4.3 `LibraryUserDetailsService`

新建 `src/main/java/com/library/security/LibraryUserDetailsService.java`：

```java
package com.library.security;

import com.library.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 从 users.json 加载登录用户。
 */
@Service
public class LibraryUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public LibraryUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .map(LibraryUserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("用户名或密码错误"));
    }
}
```

**这就是"告诉 Spring Security 去哪儿找用户"的地方。**

`UserDetailsService` 是 Spring Security 定义的接口，只有**一个方法**：`loadUserByUsername(String)`。登录时它会调用你这边的实现去拿用户。你实现了它，就完成了"接管"。

注意 `.map(LibraryUserPrincipal::new)` —— 找到的 `User` 被包成 `LibraryUserPrincipal` 返回。找不到就抛 `UsernameNotFoundException`。

> 🔎 **为什么要抛"用户名或密码错误"而不是"用户不存在"？**
>
> 这是**安全习惯**。如果提示"用户不存在"，攻击者就能用登录页**枚举出系统里有哪些用户名**（试 `admin` → "用户不存在"，试 `zhangsan` → "密码错误"，就知道 `zhangsan` 是真实用户）。统一提示让攻击者两眼一抹黑。
>
> Spring Security 后来也会把 `UsernameNotFoundException` 转成统一的失败，但**养成这个习惯**没坏处。

### 4.4 `SecurityConfig`

这是今天的核心配置。

新建 `src/main/java/com/library/security/SecurityConfig.java`：

```java
package com.library.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 登录与鉴权配置。
 *
 * <ul>
 *   <li>{@code /login}、静态资源放行；</li>
 *   <li>其余页面登录后即可访问，图书写操作另由 {@code @PreAuthorize} 限制为管理员。</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/favicon.ico").permitAll()
                        .requestMatchers("/login").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll());
        return http.build();
    }
}
```

#### 逐块解读

**类上的三个注解：**

- `@Configuration` —— 这是一个配置类，里面用 `@Bean` 声明的东西会进 Spring 容器。
- `@EnableWebSecurity` —— 开启 Spring Security 的 Web 支持。
- `@EnableMethodSecurity` —— **开启方法级权限控制**。没有它，`@PreAuthorize` **完全不起作用而且没有报错**——你写在 Controller 上的注解会被直接忽略。这是第二个超常见的坑。

**`passwordEncoder()` Bean：**

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

声明完之后，全局任何地方注入 `PasswordEncoder` 拿到的都是这个 BCrypt 实现。

**为什么返回类型写 `PasswordEncoder`（接口）而不是 `BCryptPasswordEncoder`（实现类）？** 因为使用方只关心"能加密、能校验"这个契约，不关心具体算法。**将来要换成 Argon2，只改这一行，其他代码一行不动。** 这就是"面向接口编程"的实际好处。

**`securityFilterChain(...)` Bean —— 一行一行看：**

```java
http
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/css/**", "/js/**", "/favicon.ico").permitAll()   // ①
        .requestMatchers("/login").permitAll()                              // ②
        .anyRequest().authenticated())                                      // ③
```

- ① 静态资源放行。**没有这一条，你的 CSS 也要登录才能加载**，页面会完全没有样式——而且不会报错，只是"变丑了"。经典坑。
- ② 登录页本身当然要能匿名访问。
- ③ **兜底规则，必须放最后**。`anyRequest()` 匹配所有请求，而 Spring Security 的规则是**从上往下第一个匹配的生效**。所以具体规则写前面，笼统的写后面。

```java
    .formLogin(form -> form
        .loginPage("/login")              // 用我们自己的登录页（而不是默认的）
        .defaultSuccessUrl("/", true)     // 登录成功去首页
        .failureUrl("/login?error")       // 失败回登录页并带上 ?error
        .permitAll())
```

> 🔎 `defaultSuccessUrl("/", true)` 的第二个参数 `true` 是 **alwaysUse**：无论用户之前想访问哪个页面，登录成功后**总是**去首页。不传（或传 false）的话，Spring 会记住"用户本来是去 /books 的，被拦下来了"，登录后把他送回 `/books`——对某些场景更好，但这里我们想要行为可预期。

```java
    .logout(logout -> logout
        .logoutUrl("/logout")             // POST /logout 触发退出
        .logoutSuccessUrl("/login?logout")// 退出后回登录页
        .permitAll());
```

注意 `logoutUrl` 是 **POST** 的。Day 1 的导航栏里我们用的是 `<form method="post">` 提交到 `/logout`——正好对上。（用 GET 触发登出是不安全的：一张 `<img src="/logout">` 就能把用户踢下线。）

#### 🚨 CSRF：一个 90% 的人会踩的坑

Spring Security **默认开启 CSRF 保护**。它会给每个会话生成一个随机 token，**任何 POST / PUT / DELETE 请求都必须带上这个 token**，否则返回 **403**。

那 Day 1 写的那些 `<form method="post">` 为什么今天还能用？

**因为 Thymeleaf 在你用了 `th:action` 的时候，会自动往表单里塞一个隐藏的 token 字段。**

```html
<form method="post" th:action="@{/books}">   <!-- ✅ 自动获得 CSRF token -->
<form method="post" action="/books">          <!-- ❌ 没有 token，提交必 403 -->
```

**记住这条规则**：只要项目里有 Spring Security，表单就必须用 `th:action`，**不要**用普通 `action`。Day 1 的代码恰好写对了——这不是巧合，是当时就该有的习惯。

> ❌ **报错长这样**：点保存后跳到 `Whitelabel Error Page`，HTTP 状态 **403 Forbidden**。
> ✅ **修复**：把那个表单的 `action=` 改成 `th:action=`。

### 4.5 登录页

新建 `src/main/resources/templates/login.html`：

```html
<!DOCTYPE html>
<html lang="zh-CN" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>登录 · 图书馆管理系统</title>
    <link rel="stylesheet" th:href="@{/css/style.css}">
</head>
<body class="login-page">

<main class="login-card">
    <h1>📚 图书馆管理系统</h1>
    <p class="muted">请登录后使用</p>

    <div class="alert alert-error" th:if="${param.error}">用户名或密码错误</div>
    <div class="alert alert-success" th:if="${param.logout}">您已安全退出</div>

    <form th:action="@{/login}" method="post" class="form">
        <label>
            <span>用户名</span>
            <input type="text" name="username" autofocus autocomplete="username" required>
        </label>
        <label>
            <span>密码</span>
            <input type="password" name="password" autocomplete="current-password" required>
        </label>
        <button class="btn btn-primary btn-block" type="submit">登录</button>
    </form>

    <div class="login-hint">
        <p>默认账号：</p>
        <ul>
            <li>管理员 — <code>admin / admin123</code></li>
            <li>读者 — <code>reader / reader123</code></li>
        </ul>
    </div>
</main>

</body>
</html>
```

**三个细节，都不是随便写的：**

1. **输入框的 `name` 必须是 `username` 和 `password`**。这是 Spring Security 表单登录的**约定字段名**，改了就得额外配置。所以这里**不能**用 `th:field`（那会用对象属性名）——必须老老实实写 `name="username"`。
2. **`th:if="${param.error}"`** —— `param` 是 Thymeleaf 内置对象，取 URL 查询参数。因为 `SecurityConfig` 里配的是 `failureUrl("/login?error")`，登录失败时 URL 带 `?error`，这里就显示错误提示。`?logout` 同理。
   > 注意这个页面**没有**用 `fragments/layout`，因为它不需要导航栏（用户还没登录呢）。
3. **`autocomplete="current-password"`** —— 告诉浏览器（和密码管理器）这是"已有密码"，可以自动填充。新建密码的表单则应该用 `new-password`。这是个小细节，但用起来体验差别很大。

### ✅ 验证（还差一点）

现在编译能过，但**还登不进去**——因为 `data/users.json` 里一个用户都没有，`userRepository.findByUsername()` 永远找不到人。下一步解决。

---

## 5. 造出第一批用户（45 分钟）

### 5.1 问题

我们需要 `data/users.json` 里有用户，但密码必须是 **BCrypt 哈希**——你我都没法手算。而且现在还没有用户管理界面（那是 Day 3）。

### 5.2 做法：启动时自动写入种子数据

新建 `src/main/java/com/library/service/DataInitializer.java`：

```java
package com.library.service;

import com.library.model.Role;
import com.library.model.User;
import com.library.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 首次启动时写入默认账号。已有数据时不做任何事。
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedUsers();
    }

    private void seedUsers() {
        if (!userRepository.findAll().isEmpty()) {
            return;
        }
        userRepository.save(new User("admin", passwordEncoder.encode("admin123"), "系统管理员", Role.ADMIN));
        userRepository.save(new User("reader", passwordEncoder.encode("reader123"), "张三", Role.READER));
        log.info("已初始化默认账号：admin / admin123（管理员）、reader / reader123（读者）");
    }
}
```

**理解这个类：**

- **`ApplicationRunner`** 是 Spring Boot 的接口。实现它，`run()` 方法会在**应用完全启动之后**自动执行一次。用它做初始化最合适——比 `@PostConstruct` 好，因为那时所有 Bean 都已就绪。
- **`if (!userRepository.findAll().isEmpty()) return;`** —— **幂等保护**。第二次启动时数据已经存在，直接返回，**绝不会覆盖你改过的密码**。这一行是这段代码里最重要的部分。
- **`passwordEncoder.encode("admin123")`** —— 明文在这里被转成 BCrypt 哈希**才**存进文件。**明文只在这行代码里出现，从不落盘。**

### 5.3 跑起来，第一次登录

```bash
./mvnw spring-boot:run
```

日志里应该看到：

```
已初始化默认账号：admin / admin123（管理员）、reader / reader123（读者）
```

检查一下文件：

```bash
cat data/users.json
```

**你应该看到密码是一长串 `$2a$10$...` 的乱码，而不是 `admin123`。** 这是今天最重要的验证——**如果看到明文，说明哪里写错了，立刻停下来检查。**

现在浏览器打开 <http://localhost:8080>，用 `admin` / `admin123` 登录。**应该能进去，看到图书列表。**

再试试：
- 故意输错密码 → 回到登录页，顶部红色提示「用户名或密码错误」
- 用户名输 `ADMIN`（大写）→ **也能登录**（因为 `findByUsername` 用了 `equalsIgnoreCase`）

### ✅ 里程碑：V2 的认证部分完成

到这里，**认证（你是谁）已经打通**。授权（你能干什么）还差一半——现在读者也能删书。下一节解决。

---

## 6. 首页概览（50 分钟）

现在登录后直接跳到图书列表，有点单调。做一个真正的概览页。

### 6.1 改写 `HomeController`

打开 `src/main/java/com/library/web/HomeController.java`，**整个替换**成：

```java
package com.library.web;

import com.library.model.Book;
import com.library.security.LibraryUserPrincipal;
import com.library.service.BookService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * 登录页与首页概览。
 */
@Controller
public class HomeController {

    private final BookService bookService;

    public HomeController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String dashboard(Model model, @AuthenticationPrincipal LibraryUserPrincipal principal) {
        List<Book> books = bookService.findAll();

        int totalCopies = books.stream().mapToInt(Book::getTotalCopies).sum();
        int availableCopies = books.stream().mapToInt(Book::getAvailableCopies).sum();

        model.addAttribute("bookTitles", books.size());
        model.addAttribute("totalCopies", totalCopies);
        model.addAttribute("availableCopies", availableCopies);
        model.addAttribute("borrowedCopies", totalCopies - availableCopies);

        return "dashboard";
    }
}
```

**新东西：`@AuthenticationPrincipal`**

```java
public String dashboard(Model model, @AuthenticationPrincipal LibraryUserPrincipal principal)
```

这个注解**把当前登录用户直接注入方法参数**。Spring Security 会从会话里取出登录主体，转成你指定的类型。

**为什么能转成 `LibraryUserPrincipal`？** 因为 `LibraryUserDetailsService` 返回的就是它——**闭环**：你加载谁，这里就拿到谁。

拿到 `principal` 之后，模板里就能用 `currentUser`（下一步讲怎么传过去）。如果不用这个注解，也可以从 `SecurityContextHolder.getContext().getAuthentication()` 里拿，但**参数注入干净得多**。

`@GetMapping("/login")` 只是渲染登录页。注意 Spring Security 的登录**提交**（POST /login）是由框架处理的，不需要我们写——我们只负责"显示"这个页面。

> 🔎 `books.stream().mapToInt(Book::getTotalCopies).sum()` —— 把每本书的 `totalCopies` 变成 `int` 流再求和。用 `mapToInt` 而不是 `map`，是为了得到 `IntStream`（有现成的 `sum()`）。

### 6.2 首页模板

新建 `src/main/resources/templates/dashboard.html`：

```html
<!DOCTYPE html>
<html lang="zh-CN" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragments/layout :: head('首页')}"></head>
<body>
<nav th:replace="~{fragments/layout :: navbar}"></nav>

<main class="container">
    <div class="page-header">
        <h1>
            你好，<span th:text="${currentUser.fullName}">用户</span> 👋
        </h1>
        <span class="tag" th:text="${currentUser.roleLabel}">角色</span>
    </div>

    <div class="stat-grid">
        <div class="stat-card">
            <span class="stat-value" th:text="${bookTitles}">0</span>
            <span class="stat-label">图书种类</span>
        </div>
        <div class="stat-card">
            <span class="stat-value" th:text="${totalCopies}">0</span>
            <span class="stat-label">总册数</span>
        </div>
        <div class="stat-card">
            <span class="stat-value" th:text="${availableCopies}">0</span>
            <span class="stat-label">可借册数</span>
        </div>
        <div class="stat-card">
            <span class="stat-value" th:text="${borrowedCopies}">0</span>
            <span class="stat-label">已借出</span>
        </div>
    </div>

    <div class="card">
        <h2>可以做什么</h2>
        <ul class="action-list">
            <li><a th:href="@{/books}">浏览图书目录</a></li>
            <li th:if="${currentUser.admin}"><a th:href="@{/books/new}">新增一本图书</a></li>
        </ul>
        <p class="muted" th:unless="${currentUser.admin}">
            你是读者，可以浏览图书；图书的增删改由管理员完成。
        </p>
    </div>
</main>

</body>
</html>
```

注意 `th:if="${currentUser.admin}"` —— 读者看不到"新增一本图书"这条。**但这个模板还不能工作**，因为 `currentUser` 还没人提供。下一步。

### 6.3 `CurrentUser` + `GlobalModelAdvice`

**问题**：几乎每个页面都需要"当前用户"（导航栏要显示姓名和角色）。难道每个 Controller 方法都写一遍 `model.addAttribute("currentUser", ...)`？

**不用。** Spring MVC 有 `@ControllerAdvice` —— 它里面的 `@ModelAttribute` 方法会**对每个 Controller 的每个请求都执行一次**，自动往 Model 里塞数据。

先建一个给模板用的视图对象。新建 `src/main/java/com/library/web/CurrentUser.java`：

```java
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
```

**为什么不让模板直接用 `LibraryUserPrincipal`？** 因为那是**安全框架的类型**，包含 `password` 哈希等敏感字段。把它整个丢进模板，就等于给每个 HTML 页面开了个后门（万一哪个模板写出 `${principal.password}` 就泄露了）。**`CurrentUser` 是一道防火墙：只暴露页面真正需要的东西。**

> 🔎 这里用了 `record`——Java 16+ 的语法糖。一行就得到一个不可变类，自动生成构造器、`getter`（叫 `id()` 不是 `getId()`）、`equals`、`hashCode`、`toString`。**只有数据、没有行为的类，就该用 record。**
>
> Thymeleaf 访问 record 组件用 `${currentUser.fullName}` 就行——它能识别 record 的访问器。

新建 `src/main/java/com/library/web/GlobalModelAdvice.java`：

```java
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
```

**三个点：**

1. `@ControllerAdvice` + `@ModelAttribute("currentUser")` = **所有页面的 Model 里都有一个 `currentUser`**。没有写一行重复代码。
2. 参数 `Authentication` 由 Spring Security 自动注入，代表当前认证状态。**未登录时它是 `null`**（比如访问登录页），所以要判空。
3. `instanceof LibraryUserPrincipal principal` 是 **Java 16+ 的模式匹配**：既判断类型，又顺便完成强转和变量声明，比老写法 `(LibraryUserPrincipal) auth.getPrincipal()` 安全得多。

**为什么返回 `null` 而不是抛异常？** 因为登录页、CSS 等**匿名可访问**的请求也会经过这里，那时确实没有用户。返回 `null`，模板里用 `th:if="${currentUser != null}"` 判断即可。

### 6.4 更新导航栏

打开 `src/main/resources/templates/fragments/layout.html`，把 `<nav>` 换成：

```html
<nav class="navbar" th:fragment="navbar">
    <div class="navbar-inner">
        <a class="brand" th:href="@{/}">📚 图书馆管理系统</a>

        <div class="nav-links" th:if="${currentUser != null}">
            <a th:href="@{/}">首页</a>
            <a th:href="@{/books}">图书</a>
        </div>

        <div class="nav-user" th:if="${currentUser != null}">
            <span class="tag" th:text="${currentUser.roleLabel}">角色</span>
            <span class="nav-name" th:text="${currentUser.fullName}">姓名</span>
            <form th:action="@{/logout}" method="post">
                <button type="submit" class="link-btn">退出</button>
            </form>
        </div>
    </div>
</nav>
```

`<head>` 片段不用动。

**注意 `th:if="${currentUser != null}"`** —— 未登录时（登录页虽然没用这个片段，但其他匿名可访问的页面会），导航栏的用户区域不渲染，不会因为 `currentUser` 是 `null` 而报错。

### ✅ 验证

重启（这次要 `Ctrl+C` 再 `./mvnw spring-boot:run`，因为改了 Java 代码）。

登录 `admin` / `admin123`：
- 首页显示「你好，系统管理员 👋」+ 4 个统计卡片
- 导航栏右侧显示「管理员 系统管理员 [退出]」
- 点「退出」→ 回到登录页，顶部绿色提示「您已安全退出」

再登录 `reader` / `reader123`：
- 首页显示「你好，张三 👋」
- 卡片数量一样（图书数据是共享的）
- **「可以做什么」里没有"新增一本图书"**
- 导航栏右侧显示「读者 张三」

**但注意：现在读者如果直接访问 `http://localhost:8080/books/new`，照样能打开新增表单！** 因为"隐藏按钮"只是**界面上的礼貌**，不是安全。下一步才是真正的权限控制。

> ⚠️ **一定要建立这个认知**：前端隐藏 ≠ 有权限。用户可以直接敲 URL，也可以改 `curl` 请求。**所有权限判断必须在服务端做。** 模板里的 `th:if` 只是"别让用户看到用不了的按钮"，**永远不能当作安全措施**。

---

## 7. 真正的权限控制（60 分钟）

### 7.1 三条线一起收紧

| 层 | 手段 | 作用 |
|---|---|---|
| **URL 级** | `SecurityConfig` 里 `.requestMatchers(...).hasRole(...)` | 挡住整个路径（如 `/users/**`） |
| **方法级** | Controller 方法上 `@PreAuthorize("hasRole('ADMIN')")` | 挡住单个接口 |
| **视图级** | 模板里 `th:if="${currentUser.admin}"` | 隐藏按钮（**仅仅是体验**） |

前两个是**真安全**，第三个是**好体验**。三层配合，缺一不可。

### 7.2 给图书写操作加方法级权限

打开 `src/main/java/com/library/web/BookController.java`，加 import：

```java
import org.springframework.security.access.prepost.PreAuthorize;
```

然后**在这 5 个方法上**加注解：

```java
    @GetMapping("/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String createForm(Model model) { ... }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public String create(...) { ... }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasRole('ADMIN')")
    public String editForm(@PathVariable Long id, Model model) { ... }

    @PostMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public String update(...) { ... }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) { ... }
```

**`list()` 方法（`@GetMapping`）不加** —— 读者要能看图书列表。

**看清楚这有多简洁**：`BookService`、`BookRepository`、`Book` 模型，**一行都没改**。因为 Day 1 的 `@RequestMapping("/books")` 按资源划分了路径，加权限只需要在方法上贴标签。

> 🔎 **`hasRole('ADMIN')` 里的 `ADMIN` 不带 `ROLE_` 前缀**——`hasRole` 会自动补。而如果写 `hasAuthority(...)`，就得写全 `ROLE_ADMIN`。这两个混用是高频错误来源。

### 7.3 读者看不到编辑/删除按钮

打开 `src/main/resources/templates/books/list.html`，找到「操作」那一列，把里面改成：

```html
                <td class="actions">
                    <a class="btn btn-small btn-ghost" th:if="${currentUser.admin}"
                       th:href="@{/books/{id}/edit(id=${book.id})}">编辑</a>
                    <form th:if="${currentUser.admin}"
                          th:action="@{/books/{id}/delete(id=${book.id})}" method="post"
                          onsubmit="return confirm('确定删除这本书吗？此操作不可撤销。');">
                        <button class="btn btn-small btn-danger" type="submit">删除</button>
                    </form>
                </td>
```

同时，把页头那个「新增图书」也加上权限判断：

```html
        <a class="btn btn-primary" th:if="${currentUser.admin}" th:href="@{/books/new}">+ 新增图书</a>
```

**改动只有两处 `th:if`。** 读者打开列表页，"操作"列是**空的**（不是灰按钮，是根本不渲染）。

### ✅ 验证：核心部分

**重启**，然后做这两组对照试验。

**A. 用读者账号 `reader` / `reader123`：**

- [ ] 图书列表能打开，能看到所有书
- [ ] 页头**没有**「+ 新增图书」按钮
- [ ] 每一行的「操作」列**是空的**
- [ ] 直接访问 `http://localhost:8080/books/new` → **403 Forbidden** ← **这条是关键**
- [ ] 首页「可以做什么」里只有"浏览图书目录"

**B. 用管理员账号 `admin` / `admin123`：**

- [ ] 一切照旧：能新增、编辑、删除
- [ ] 首页多一条"新增一本图书"

**C. 顺手验证 CSRF（可选，很有意思）：**

用 curl 模拟一个**不带 token** 的删除请求：

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/books/1/delete
```

应该输出 **403** —— 因为没登录、没 CSRF token。这就是 Spring Security 在替你挡刀。

---

## 8. 自测清单

完整走一遍，**两套账号都要试**：

- [ ] `./mvnw -q clean package` 打包成功
- [ ] 启动后日志出现「已初始化默认账号」，且**只有第一次出现**（第二次启动不再打印）
- [ ] `cat data/users.json`，密码是 `$2a$10$...` 哈希，**不是明文**
- [ ] 未登录访问 `/books` → 跳转到 `/login`
- [ ] 密码输错 → 回登录页 + 红色提示
- [ ] `admin` / `admin123` 登录成功
- [ ] `ADMIN` / `admin123`（大写用户名）**也能**登录
- [ ] 首页显示姓名、角色、4 个统计数字
- [ ] 导航栏显示「角色 + 姓名 + 退出」
- [ ] 退出后回登录页 + 绿色提示；此时再访问 `/books` → **又跳回登录页**（会话已失效）
- [ ] 读者：看不到新增/编辑/删除按钮；直接敲 `/books/new` 得到 403
- [ ] 管理员：增删改查全部正常
- [ ] **重启应用后，用 admin 登录仍然有效**（用户数据持久化 ✅）

全过 = **V2 完成** 🎉

---

## 9. 常见坑速查

| 现象 | 原因 | 解决 |
|---|---|---|
| 提交表单 403 Forbidden | 表单用了 `action=` 而不是 `th:action=`，没有 CSRF token | 改成 `th:action` |
| 登录后仍 403 / 权限永远不通过 | `ROLE_` 前缀问题 | `getAuthorities()` 里写 `"ROLE_" + role.name()`；`@PreAuthorize` 用 `hasRole('ADMIN')`（不带前缀） |
| `@PreAuthorize` 完全不起作用，也不报错 | 忘了 `@EnableMethodSecurity` | 加在 `SecurityConfig` 类上 |
| CSS 不生效，页面没样式 | `/css/**` 没放行，样式被拦截 | `permitAll()` 里加上 `/css/**` |
| 登录后无限重定向到 `/login` | `@GetMapping("/login")` 没写，或路径和 `loginPage()` 对不上 | 确认 `HomeController` 里有 `/login`，且 `SecurityConfig` 里 `loginPage("/login")` 一致 |
| 报 `There is no PasswordEncoder mapped for the id "null"` | 注入的 `PasswordEncoder` 不是 BCrypt | 确认 `passwordEncoder()` Bean 存在且返回 `BCryptPasswordEncoder` |
| 启动报 `A component required a bean of type 'PasswordEncoder'` | 忘了声明 Bean | 检查 `SecurityConfig` 里的 `@Bean` |
| 页面报 `currentUser` 为 null 的错 | 用了 `${currentUser.xxx}` 但没判空 | 模板里加 `th:if="${currentUser != null}"` |
| 重启后密码没变但用户没更新 | `DataInitializer` 的幂等保护生效了（**这是正确行为**） | 想重来就删掉 `data/users.json` 再启动 |
| `hasRole` 判断一直失败 | `Role` 枚举名和注解里写的不一致 | `Role.ADMIN` ↔ `hasRole('ADMIN')` 必须完全对上 |

---

## 10. 复盘（30 分钟）

1. **认证和授权的区别是什么？** 分别对应今天哪些代码？
2. `LibraryUserPrincipal` 这个类**存在的意义**是什么？直接把 `User` 拿去实现 `UserDetails` 有什么坏处？
3. `CurrentUser` 这个 record 是多余的转发吗？**如果去掉它、模板直接用 principal，会有什么风险？**
4. **为什么"前端隐藏按钮"不算安全措施？** 用自己的话解释攻击者会怎么绕过。
5. BCrypt 的"加盐"解决了什么问题？"故意慢"又解决了什么问题？
6. `DataInitializer` 里 `if (!userRepository.findAll().isEmpty()) return;` 这一行，**删掉会怎样？**
7. **为什么给 `BookController` 加权限，`BookService` 和 `BookRepository` 一行都不用改？** 这说明了 Day 1 的哪个设计决定是对的？

### 🛠 动手改一改

1. **加第三种角色**（比如 `LIBRARIAN` 图书管理员）：只能改书，不能碰用户。想想 `Role` 枚举、`@PreAuthorize` 要改哪儿。
2. **做个"记住我"**：Spring Security 有现成的 `rememberMe()`，查文档加上。
3. **登录失败计数**：同一个账号连错 5 次锁定 10 分钟。想想这个状态该存在哪（内存？文件？）。
4. **换个提示语**：把登录失败的"用户名或密码错误"改得更友好，但**别泄露是用户名错还是密码错**。

---

## 11. 明天预告

现在的系统有两个明显的缺口：

1. **没法加人**。`admin` 和 `reader` 是硬编码在 `DataInitializer` 里的。要加第三个用户，你得手改 JSON 文件——而密码还得是 BCrypt 哈希，**你根本算不出来**。
2. **读者和普通访客没区别**。说好的"读者能借书"呢？现在读者除了看列表什么都干不了。

Day 3 我们会：

- 做一个完整的**用户管理**界面（增删改查 + 分配角色），密码在那里被正确地加密
- 实现**借阅 / 归还**，让"读者"这个角色真正有意义
- 把图书数据换成 **豆瓣 Top 250**——**并在这里遇到一个真实的设计问题**：这 250 本书没有 ISBN，而我们的 ISBN 是必填的。看看怎么处理
- 打包成可执行 jar，写完 README

最后一天，我们会把整个系统**串起来**。

---

**V2 打卡：** 当读者账号成功被 403 拦在 `/books/new` 外面时，写下今天的日期 ______。明天见 👋
