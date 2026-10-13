# 图书馆管理系统

基于 **Spring Boot 4.1** 的图书馆管理系统，包含登录认证、角色权限、图书增删改查、用户增删改查，数据以 JSON 文件持久化。

## 技术栈

| 组件 | 版本 / 说明 |
|---|---|
| Java | 27 |
| Spring Boot | 4.1.1（Spring Framework 7、Spring Security 7、Tomcat 11） |
| 视图 | Thymeleaf（服务端渲染） |
| 持久化 | Jackson 3 写 JSON 文件（`data/*.json`），无数据库 |
| 构建 | Maven（自带 `mvnw` wrapper，无需本机装 Maven） |

## 快速开始

```bash
./mvnw spring-boot:run
```

启动后访问 <http://localhost:8080>，会自动跳转到登录页。

也可以打包成可执行 jar：

```bash
./mvnw clean package
java -jar target/hi-1.0.0.jar
```

### 默认账号

首次启动会自动写入默认账号，并从 `src/main/resources/seed/books.json` 导入豆瓣图书 Top 250 书目：

| 用户名 | 密码 | 角色 |
|---|---|---|
| `admin` | `admin123` | 管理员 |
| `reader` | `reader123` | 读者 |

## 功能

### 角色权限

| 功能 | 管理员 | 读者 |
|---|:---:|:---:|
| 浏览图书列表 / 搜索 | ✅ | ✅ |
| 借阅、归还图书 | ✅ | ✅ |
| 新增 / 编辑 / 删除图书 | ✅ | ❌ |
| 用户管理（增删改查） | ✅ | ❌ |

读者的「用户管理」菜单与「新增图书」按钮不会渲染；即使直接访问 `/users` 或提交写操作也会被 Spring Security 拦截（403）。

### 图书管理
- 列表 + 按书名 / 作者 / ISBN / 分类搜索
- 新增、编辑、删除
- 借阅（可借册数 −1）、归还（可借册数 +1），已全部借出时无法借阅
- 校验：ISBN **选填**，填了才做唯一性校验；可借册数不得超过总册数

### 用户管理
- 列表 + 按用户名 / 姓名搜索
- 新增、编辑、删除，可分配管理员 / 读者角色
- 编辑时密码留空表示不修改原密码
- 密码以 **BCrypt** 哈希存储，不存明文
- 保护规则：不能删除当前登录账号，也不能删掉系统里最后一名管理员

## 项目结构

```
src/main/java/com/library/
  LibraryApplication.java        启动类
  config/LibraryProperties.java  绑定 library.* 配置
  model/                         Book / User / Role
  repository/
    JsonFileStore.java           通用 JSON 读写基类（读写锁保证并发安全）
    BookRepository.java
    UserRepository.java
  service/
    BookService.java             图书业务 + 借阅归还
    UserService.java             用户业务 + 密码加密
    DataInitializer.java         首次启动写入种子数据
    BusinessException.java       业务校验异常（可指定回显字段）
  security/
    SecurityConfig.java          过滤链、登录/登出、URL 鉴权
    LibraryUserDetailsService.java
    LibraryUserPrincipal.java
  web/
    HomeController.java          登录页 + 首页概览
    BookController.java          /books 增删改查 + 借阅归还
    UserController.java          /users 增删改查
    GlobalModelAdvice.java       向所有模板注入当前用户
    dto/UserForm.java            用户表单对象

src/main/resources/
  application.yml
  templates/                     login / dashboard / books / users / error
  static/css/style.css
```

## 数据存储

数据存在项目根目录的 `data/` 下（已被 `.gitignore` 忽略）：

- `data/books.json`
- `data/users.json`

**想重置系统**：停掉应用，删除 `data/` 目录再启动，即可恢复到初始的默认账号与 250 本种子图书。

## 注意事项

- 图书的 `borrowedCopies` 等派生字段、用户的 `isAdmin` 标记都不会写入 JSON（标了 `@JsonIgnore`），文件里只保存真实字段。
- `spring.jackson.deserialization.fail-on-unknown-properties: false`：数据文件里出现模型中没有的字段时会被忽略，避免改动模型后因旧数据导致启动失败。
- 图书种子数据来自 `src/main/resources/seed/books.json`，是**豆瓣图书 Top 250** 的一份快照，含书名 / 作者 / 分类 / 总册数。该书目**不带 ISBN**（豆瓣没有可批量获取 ISBN 的接口），因此 `Book.isbn` 已改为**选填**，列表页留空的 ISBN 显示为 `—`。
- 豆瓣榜单会随时间与评分算法变动，此处快照只代表抓取时的排名；分类为人工归类，仅供参考。
- 借阅/归还目前是轻量实现，只增减可借册数，**不记录借阅历史**与借阅人。如需借阅记录表，可在此基础上扩展。
- 在 IntelliJ IDEA 中打开时，若提示导入 Maven 项目请确认；或右键 `pom.xml` → *Add as Maven Project*。
