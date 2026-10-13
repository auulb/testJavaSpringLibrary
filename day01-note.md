# Day 01 · V1：能跑的图书管理（无登录）

> 目标用时 6 小时。今天结束你会得到一个**能真正用起来**的网页程序：图书列表、搜索、新增、编辑、删除，数据存进 `data/books.json`，重启不丢。
>
> 今天**不做**登录、不做用户管理、不做借阅。那些是 Day 2 / Day 3 的事——先把地基打牢。

---

## 0. 开始之前：先把「答案」备份走

当前项目 `hi/` 里已经躺着写好的最终版代码。你要是一边看一边抄，就学不到东西了。所以第一件事：

```bash
cd /Users/jwj/Documents/IdeaProjects/test
cp -r hi hi-backup        # 备份一份，Day 3 会回来取两个文件
ls hi-backup/src/main/resources/seed/books.json   # 确认在
```

`hi-backup` 里有两样东西后面要用，现在记一下位置：

| 文件 | 什么时候用 |
|---|---|
| `hi-backup/src/main/resources/static/css/style.css` | Day 1 第 5 步，直接拷过去 |
| `hi-backup/src/main/resources/seed/books.json` | Day 3，豆瓣 Top 250 种子数据 |

然后**清空 `hi/src`**，从零开始写：

```bash
cd /Users/jwj/Documents/IdeaProjects/test/hi
rm -rf src            # 删掉整个 src，回到空白
mkdir -p src/main/java/com/library
mkdir -p src/main/resources/templates/fragments
mkdir -p src/main/resources/static/css
```

> ⚠️ 别删 `pom.xml` 以外的 `hi.iml` / `.idea/`，那是 IDEA 的工程配置，留着省事。`pom.xml` 我们一会自己写。

---

## 1. 环境自检（10 分钟）

在终端里逐条跑，确认输出符合预期：

```bash
java -version
```

应该看到 `java version "27"` 之类。本机只装了 JDK 27。

```bash
which mvn
```

**大概率什么都没有**——这很正常，本机没装 Maven。我们不用它。

```bash
ls -l mvnw
```

应该看到 `mvnw` 文件存在（Maven Wrapper，项目自带的，不需要你装 Maven）。

所以今天所有 Maven 命令，一律写成 `./mvnw ...`：

| 你想做的事 | 命令 |
|---|---|
| 编译 | `./mvnw -q compile` |
| 打包 | `./mvnw -q package` |
| 直接跑起来 | `./mvnw spring-boot:run` |

> 💡 如果你更习惯在 IDEA 里点按钮：右侧 Maven 面板 → Lifecycle → `package`。两种都行。

---

## 2. 今天的时间表

| 时段 | 内容 | 产出 |
|---|---|---|
| 00:00–00:20 | 环境自检、清理目录 | 空工程 |
| 00:20–01:00 | 搭骨架：pom + 启动类，**第一次跑起来** | 能看到 404 白页 = 框架活了 |
| 01:00–02:00 | 模型 + JSON 持久化 | `Book` / `JsonFileStore` / `BookRepository` |
| 02:00–02:15 | ☕ 休息 | |
| 02:15–03:00 | 业务层 | `BookService` + 两个异常类 |
| 03:00–04:00 | Web 层 | `BookController` |
| 04:00–04:15 | ☕ 休息 | |
| 04:15–05:30 | 页面 | 4 个 HTML + CSS |
| 05:30–06:00 | 自测清单 + 复盘 | V1 完成 ✅ |

**重要**：每个步骤末尾都有「✅ 验证」。**没看到预期结果就不要往下走**，先解决。这是最重要的一条学习纪律。

---

## 3. 搭骨架（40 分钟）

### 3.1 `pom.xml`

在 `hi/pom.xml` 写入（**覆盖**原文件）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.1</version>
        <relativePath/>
    </parent>

    <groupId>com.library</groupId>
    <artifactId>hi</artifactId>
    <version>1.0.0</version>
    <name>library-management</name>
    <description>图书馆管理系统</description>

    <properties>
        <java.version>27</java.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-thymeleaf</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**这段在干什么？**

- `<parent>` 继承 Spring Boot 官方父 POM。好处：下面所有依赖**不用写版本号**，版本由父 POM 统一管。这就是为什么 `<dependency>` 里只有 `groupId` + `artifactId`。
- `spring-boot-starter-webmvc`：一个「starter」= 一捆打包好的依赖。它把 Spring MVC、内嵌 Tomcat、JSON 处理全拉进来。**注意名字是 `webmvc` 不是 `web`**——网上老教程会写 `spring-boot-starter-web`，那对老版本是常识，但**请照这份 pom 抄**，跟本项目保持一致。
- `spring-boot-starter-thymeleaf`：模板引擎，负责把 HTML 里的 `th:xxx` 标签渲染成真正的网页。
- `spring-boot-starter-validation`：表单校验，提供 `@NotBlank`、`@Min` 这些注解。
- `<java.version>27</java.version>`：告诉编译器用 Java 27。

### 3.2 `application.yml`

新建 `src/main/resources/application.yml`：

```yaml
server:
  port: 8080

spring:
  application:
    name: library-management
  thymeleaf:
    # 开发阶段关闭模板缓存，改完 HTML 刷新即可见
    cache: false
  jackson:
    deserialization:
      # 数据文件里出现模型中没有的字段时忽略即可，避免旧数据/字段改名导致启动失败
      fail-on-unknown-properties: false

library:
  # JSON 数据文件存放目录（相对应用启动目录）
  data-dir: data
```

**三个关键点：**

1. `thymeleaf.cache: false` —— 开发时改了 HTML 不用重启，刷新浏览器就行。上线要关掉（缓存能提速），但今天开着。
2. `fail-on-unknown-properties: false` —— 以后你给 `Book` 加字段、删字段，旧数据文件不会让程序启动失败。是个很实用的保险。
3. `library.data-dir: data` —— 这是**我们自己的**配置项，下面 `LibraryProperties` 会读它。

### 3.3 启动类

新建 `src/main/java/com/library/LibraryApplication.java`：

```java
package com.library;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 图书馆管理系统启动类。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class LibraryApplication {

    public static void main(String[] args) {
        SpringApplication.run(LibraryApplication.class, args);
    }
}
```

**两个注解：**

- `@SpringBootApplication` —— 三合一：① 标记这是 Spring Boot 应用 ② 开启自动配置（Spring 看到你引入了 webmvc 就自动配好 Tomcat + MVC）③ 从 `com.library` 包往下扫描所有 `@Controller` / `@Service` / `@Repository` 组件。
- `@ConfigurationPropertiesScan` —— 自动扫描 `@ConfigurationProperties` 类（下面 `LibraryProperties` 用）。

> 🔍 **包名很重要**。Spring 默认从启动类所在包往下扫。启动类是 `com.library`，所以 `com.library.service`、`com.library.web` 都能被扫到；但如果你把类放到 `com.library2` 就扫不到了。新手最常见的"我的 Controller 怎么 404"就是这个原因。

### ✅ 验证：框架活了吗

```bash
./mvnw spring-boot:run
```

第一次会下载一堆依赖，耐心等。看到这两行就是成功：

```
Tomcat started on port 8080 (http)
Started LibraryApplication in x.xxx seconds
```

浏览器打开 <http://localhost:8080> —— 会看到 **Whitelabel Error Page / 404**。

**这是好事**：说明 Tomcat 起来了、Spring 启动了，只是你还没写任何页面。按 `Ctrl+C` 停掉。

> ❌ 如果报 `Port 8080 was already in use`，说明有别的程序占着 8080。查出来杀掉：
> ```bash
> lsof -ti:8080 | xargs kill -9
> ```

---

## 4. 模型 + JSON 持久化（60 分钟）

这一节是**今天最重要的部分**。理解它，就理解了"没有数据库怎么存数据"。

### 4.1 图书模型 `Book`

新建 `src/main/java/com/library/model/Book.java`：

```java
package com.library.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 图书。既作为持久化实体，也直接作为新增/编辑表单的绑定对象。
 */
public class Book {

    private Long id;

    @NotBlank(message = "书名不能为空")
    @Size(max = 200, message = "书名不能超过 200 个字符")
    private String title;

    @NotBlank(message = "作者不能为空")
    @Size(max = 120, message = "作者不能超过 120 个字符")
    private String author;

    @NotBlank(message = "ISBN 不能为空")
    @Size(max = 32, message = "ISBN 不能超过 32 个字符")
    private String isbn;

    @Size(max = 60, message = "分类不能超过 60 个字符")
    private String category;

    @NotNull(message = "总册数不能为空")
    @Min(value = 1, message = "总册数至少为 1")
    private Integer totalCopies;

    @NotNull(message = "可借册数不能为空")
    @Min(value = 0, message = "可借册数不能为负数")
    private Integer availableCopies;

    public Book() {
    }

    public Book(String title, String author, String isbn, String category, int totalCopies) {
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.category = category;
        this.totalCopies = totalCopies;
        this.availableCopies = totalCopies;
    }

    /** 已借出册数 = 总册数 - 可借册数。派生值，不写入 JSON。 */
    @JsonIgnore
    public int getBorrowedCopies() {
        int total = totalCopies == null ? 0 : totalCopies;
        int available = availableCopies == null ? 0 : availableCopies;
        return Math.max(0, total - available);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getTotalCopies() {
        return totalCopies;
    }

    public void setTotalCopies(Integer totalCopies) {
        this.totalCopies = totalCopies;
    }

    public Integer getAvailableCopies() {
        return availableCopies;
    }

    public void setAvailableCopies(Integer availableCopies) {
        this.availableCopies = availableCopies;
    }
}
```

**要理解的三件事：**

1. **为什么要有无参构造 `Book()`？** 两个框架都要用：Jackson 从 JSON 反序列化时要先 `new Book()` 再逐个 `setXxx()`；Spring MVC 把表单数据装进对象时也一样。**删掉它程序就崩**。另一个带参数的构造器是我们自己代码里方便用的。
2. **`@JsonIgnore` 是干嘛的？** `borrowedCopies` 是由另外两个字段**算出来**的，不该存进 JSON——存了反而可能不一致。加了它，Jackson 序列化时会跳过。这就是所谓「派生字段」。
3. **校验注解**：`@NotBlank`（非空白字符串）、`@Size`（长度）、`@NotNull`、`@Min`。它们**自己什么都不做**，要配合控制器里的 `@Valid` 才生效（第 5 节讲）。`message` 是校验失败时给用户看的中文提示——注意这里直接写中文，因为文件是 UTF-8。

> 🐛 **今天第一个大坑，务必看**：
>
> 这个项目的 `message` 里的中文、页面里的中文，全靠 UTF-8。pom 里的 `project.build.sourceEncoding` 已经设好了。但**IDEA 里如果看到中文变乱码**，去 `Settings → Editor → File Encodings`，把三处都设成 `UTF-8`，并勾上 `Transparent native-to-ASCII conversion` 的**反面**（不要勾）。

### 4.2 通用 JSON 仓储 `JsonFileStore`

这是今天的核心。需求很简单：**把一串对象整个存成一个 JSON 文件，读的时候整个读回来。**

新建 `src/main/java/com/library/repository/JsonFileStore.java`：

```java
package com.library.repository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

/**
 * 把一组对象以 JSON 数组的形式存到单个文件的通用仓储基类。
 *
 * <p>读写都用 {@link ReentrantReadWriteLock} 保护：读走读锁，写走写锁，
 * 而 {@link #update} 在整个「读取-修改-写回」期间持有写锁，保证并发下不会丢失更新。
 * 本系统数据量很小，整文件读写完全够用。
 *
 * <p>用的是 Jackson 3（Spring Boot 4 的默认版本），包名为 {@code tools.jackson.*}，
 * 其异常为非受检的 {@link JacksonException}。
 *
 * @param <T> 记录类型
 */
public abstract class JsonFileStore<T> {

    private final ObjectMapper mapper;
    private final Path file;
    private final JavaType listType;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    protected JsonFileStore(ObjectMapper mapper, Path dataDir, String fileName, Class<T> elementType) {
        this.mapper = mapper;
        this.file = dataDir.resolve(fileName);
        this.listType = mapper.getTypeFactory().constructCollectionType(List.class, elementType);
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            throw new UncheckedIOException("无法创建数据目录: " + dataDir, e);
        }
    }

    /** 返回全部记录的快照。 */
    protected List<T> readAll() {
        lock.readLock().lock();
        try {
            return load();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 在写锁内执行「读取-修改-写回」。{@code mutator} 收到的是可变列表，
     * 其返回值会原样返回给调用方；方法结束前列表会被写回文件。
     */
    protected <R> R update(Function<List<T>, R> mutator) {
        lock.writeLock().lock();
        try {
            List<T> items = load();
            R result = mutator.apply(items);
            save(items);
            return result;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** 数据文件路径，供实现类判断是否需要初始化。 */
    protected Path file() {
        return file;
    }

    private List<T> load() {
        try {
            if (!Files.exists(file) || Files.size(file) == 0) {
                return new ArrayList<>();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("读取数据文件失败: " + file, e);
        }
        try {
            List<T> items = mapper.readValue(file.toFile(), listType);
            return items == null ? new ArrayList<>() : items;
        } catch (JacksonException e) {
            throw new IllegalStateException("解析数据文件失败: " + file, e);
        }
    }

    private void save(List<T> items) {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), items);
        } catch (JacksonException e) {
            throw new IllegalStateException("写入数据文件失败: " + file, e);
        }
    }
}
```

#### 🚨 全项目最大的坑：Jackson 3

**看第一行 import：**

```java
import tools.jackson.databind.ObjectMapper;      // ← 项目用的
```

而你从网上搜到的教程、问 AI 得到的答案，十有八九写的是：

```java
import com.fasterxml.jackson.databind.ObjectMapper;   // ← 老写法，本项目用不了
```

**Spring Boot 4 内置的是 Jackson 3，包名从 `com.fasterxml.jackson` 换成了 `tools.jackson`。** 具体差异：

| | Jackson 2（老） | Jackson 3（本项目） |
|---|---|---|
| 核心包 | `com.fasterxml.jackson.databind` | `tools.jackson.databind` |
| **注解包** | `com.fasterxml.jackson.annotation` | **`com.fasterxml.jackson.annotation`（没变！）** |
| 抛出的异常 | 受检异常 `JsonProcessingException` | **非受检** `JacksonException`（`RuntimeException`） |
| 静态构造 | `JsonMapper.builder()` | `JsonMapper.builder()` |

**划重点两条：**

1. `ObjectMapper`、`JavaType` 这些**类**在 `tools.jackson.*` 下；
   但 `@JsonIgnore`、`@JsonProperty` 这些**注解**还在 `com.fasterxml.jackson.annotation` 下。这就是为什么 `Book.java` 里 import 的是 `com.fasterxml`，而这里 import 的是 `tools.jackson`。**看起来矛盾，其实是对的。**
2. `JacksonException` 是 `RuntimeException`，**不需要 `throws`**，可以自由地 `throw`。老教程里那种 `try/catch (JsonProcessingException)` 的写法在这里是编译不过的。

> 遇到"找不到符号 ObjectMapper"的报错，99% 是 import 写错了。回来对一眼这两张表。

#### 这段 `JsonFileStore` 的设计思路

**为什么是抽象类 + 泛型 `<T>`？**

因为图书和用户都要存 JSON，逻辑一模一样。写一份通用的，子类只需告诉它「存哪个文件、存什么类型」。

**`update(Function<List<T>, R> mutator)` 这个签名是什么意思？**

`Function<List<T>, R>` 是 Java 的函数式接口：**你交给我一个函数，我调用它、把数据列表传进去，它返回一个结果 R**。看它的用法就懂了：

```java
update(items -> {
    items.add(book);     // items 是「已从文件读出来的完整列表」，可以随便改
    return book;         // 返回值会原样交还给调用方
});
```

**关键在于整个「读文件 → 你改列表 → 写回文件」全程持有写锁**。两个管理员同时点"新增"，不会有一个的修改被覆盖掉。这就是为什么不能简单地"读、改、写"三步分开做。

**`ReentrantReadWriteLock` 是什么？**

一把能同时允许多个读者、但写者独占的锁。读多写少的场景比普通锁快。`readLock()` 给读，`writeLock()` 给写，必须 `finally` 解锁——你可能会忘了写 `unlock()`，看看上面代码里 `try/finally` 的结构，这是标准写法，照抄。

**`mapper.getTypeFactory().constructCollectionType(List.class, elementType)` 在干嘛？**

Java 的泛型在运行时会「擦除」，Jackson 光看 `List` 不知道里面装的是什么。这行是在**运行时构造出 `List<Book>` 这个完整的类型信息**交给 Jackson，它才知道该 new 多少个 `Book`。

### 4.3 配置绑定 `LibraryProperties`

新建 `src/main/java/com/library/config/LibraryProperties.java`：

```java
package com.library.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * 绑定 application.yml 中 library.* 的配置。
 */
@ConfigurationProperties(prefix = "library")
public class LibraryProperties {

    /** JSON 数据文件所在目录。 */
    private String dataDir = "data";

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public Path dataDirPath() {
        return Path.of(dataDir);
    }
}
```

`@ConfigurationProperties(prefix = "library")` 会把 yml 里 `library:` 底下的配置，**按名字**塞进这个类的字段：`library.data-dir` → `dataDir`（Spring 自动处理「短横线 / 驼峰」的转换）。所以以后想换数据目录，改 yml 就行，不用改代码。

`dataDirPath()` 是给仓储用的便捷方法，把字符串转成 `Path`。

### 4.4 图书仓储 `BookRepository`

新建 `src/main/java/com/library/repository/BookRepository.java`：

```java
package com.library.repository;

import tools.jackson.databind.ObjectMapper;
import com.library.config.LibraryProperties;
import com.library.model.Book;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 图书仓储，数据落在 {@code data/books.json}。
 */
@Repository
public class BookRepository extends JsonFileStore<Book> {

    public BookRepository(ObjectMapper mapper, LibraryProperties properties) {
        super(mapper, properties.dataDirPath(), "books.json", Book.class);
    }

    public List<Book> findAll() {
        return readAll().stream()
                .sorted(Comparator.comparing(Book::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public Optional<Book> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return readAll().stream().filter(book -> id.equals(book.getId())).findFirst();
    }

    /** ISBN 是否已被其它图书占用；{@code excludeId} 用于编辑时排除自身。 */
    public boolean existsByIsbn(String isbn, Long excludeId) {
        if (isbn == null || isbn.isBlank()) {
            return false;
        }
        return readAll().stream().anyMatch(book ->
                isbn.equalsIgnoreCase(book.getIsbn())
                        && !(excludeId != null && excludeId.equals(book.getId())));
    }

    /** 新增（id 为 null）或更新（id 已存在）一本书，返回保存后的对象。 */
    public Book save(Book book) {
        return update(items -> {
            if (book.getId() == null) {
                book.setId(nextId(items));
                items.add(book);
                return book;
            }
            for (int i = 0; i < items.size(); i++) {
                if (book.getId().equals(items.get(i).getId())) {
                    items.set(i, book);
                    return book;
                }
            }
            items.add(book);
            return book;
        });
    }

    public boolean deleteById(Long id) {
        return update(items -> items.removeIf(book -> id != null && id.equals(book.getId())));
    }

    private Long nextId(List<Book> items) {
        return items.stream()
                .map(Book::getId)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .orElse(0L) + 1;
    }
}
```

**几个点：**

- **构造器就是子类要提供的全部信息**：`dataDir`、文件名 `books.json`、元素类型 `Book.class`。剩下的读写逻辑都在父类里。这就是继承的价值。
- `ObjectMapper` 参数是**从 Spring 容器里注入进来的**。你没写任何 `new ObjectMapper()` 的代码——Spring Boot 看到你用了 webmvc，就自动配好了一个 `ObjectMapper` Bean。`@Repository` 标记这个类由 Spring 创建和管理。
- `Optional<Book>` 是 Java 用来表达「可能没有」的类型。`findById` 找不到时返回 `Optional.empty()`，强迫调用方处理「找不到」的情况，比返回 `null` 安全。
- `nextId` 取现有最大 id + 1。简单粗暴，但对我们这种小数据量够用。
- `save` 同时负责「新增」和「更新」：`id == null` 就是新增。

### ✅ 验证：能编译

```bash
./mvnw -q compile
```

**没有任何输出 = 成功。** 有报错就照着 `${}` 提示的位置改。

> 如果报 `找不到符号: 类 ObjectMapper`，回头检查 import 是不是写成了 `com.fasterxml.jackson.databind.ObjectMapper`。

---

## 5. 业务层（45 分钟）

### 5.1 两个异常类

先说**为什么要自定义异常**。校验失败时（比如 ISBN 重复），我们希望：
1. 业务层只管「发现错误、抛出来」，不用管界面怎么显示；
2. 控制器接住它，把中文提示塞回表单对应的输入框底下。

所以做一个能携带「是哪個字段出错了」的异常。

新建 `src/main/java/com/library/service/BusinessException.java`：

```java
package com.library.service;

/**
 * 业务规则校验失败（如 ISBN 重复、可借册数越界）。
 *
 * <p>{@code field} 指出应把错误挂在表单的哪个字段上，方便控制器回显到对应输入框；
 * 为 {@code null} 时作为整表单的全局错误显示。
 */
public class BusinessException extends RuntimeException {

    private final String field;

    public BusinessException(String message) {
        this(null, message);
    }

    public BusinessException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
```

新建 `src/main/java/com/library/service/NotFoundException.java`：

```java
package com.library.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 记录不存在。映射为 HTTP 404，由 error 页面渲染。
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
```

- `RuntimeException` 是**非受检异常**，抛出时不需要在方法签名上写 `throws`，一路往上冒到能处理它的地方。业务异常用它最合适。
- `@ResponseStatus(HttpStatus.NOT_FOUND)` 让 Spring 在没人接住这个异常时，自动返回 **404** 而不是 500。访问 `/books/999/edit` 而 999 不存在时，用户看到的就是 404。

### 5.2 `BookService`

新建 `src/main/java/com/library/service/BookService.java`：

```java
package com.library.service;

import com.library.model.Book;
import com.library.repository.BookRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * 图书业务逻辑：增删改查。
 */
@Service
public class BookService {

    private final BookRepository bookRepository;

    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    public List<Book> findAll() {
        return bookRepository.findAll();
    }

    /** 按关键字过滤书名、作者、ISBN、分类；关键字为空时返回全部。 */
    public List<Book> search(String keyword) {
        List<Book> all = bookRepository.findAll();
        if (keyword == null || keyword.isBlank()) {
            return all;
        }
        String needle = keyword.trim().toLowerCase(Locale.ROOT);
        return all.stream().filter(book -> contains(book.getTitle(), needle)
                || contains(book.getAuthor(), needle)
                || contains(book.getIsbn(), needle)
                || contains(book.getCategory(), needle)).toList();
    }

    public Book get(Long id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("图书不存在（id=" + id + "）"));
    }

    public Book create(Book book) {
        book.setId(null);
        validate(book, null);
        if (book.getAvailableCopies() == null) {
            book.setAvailableCopies(book.getTotalCopies());
        }
        return bookRepository.save(book);
    }

    public Book update(Long id, Book book) {
        get(id); // 确认存在，否则抛 404
        book.setId(id);
        validate(book, id);
        return bookRepository.save(book);
    }

    public void delete(Long id) {
        if (!bookRepository.deleteById(id)) {
            throw new NotFoundException("图书不存在（id=" + id + "）");
        }
    }

    private void validate(Book book, Long excludeId) {
        if (bookRepository.existsByIsbn(book.getIsbn(), excludeId)) {
            throw new BusinessException("isbn", "ISBN「" + book.getIsbn() + "」已被另一本图书使用");
        }
        int total = book.getTotalCopies() == null ? 0 : book.getTotalCopies();
        int available = book.getAvailableCopies() == null ? 0 : book.getAvailableCopies();
        if (available > total) {
            throw new BusinessException("availableCopies", "可借册数不能大于总册数（当前 " + total + "）");
        }
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }
}
```

**理解分层：** Controller 只负责"接 HTTP 请求、调 Service、选返回哪个页面"；Service 负责"业务规则"（ISBN 不能重复、可借数不能超总册数）。**校验规则写在 Service 里，意味着不管从哪个入口进来都生效**，不会因为将来加了个 API 就绕过去。

**`search` 为什么用 `Locale.ROOT`？** `toLowerCase()` 不带参数会用系统默认区域（土耳其语环境下 `I` 会变成 `ı`，导致搜索诡异出错）。传 `Locale.ROOT` 表示"用最中性的规则"，是个好习惯。

**`create` 里为什么要 `book.setId(null)`？** 防止表单里被塞了 id 导致"新增"变成"覆盖"。

### ✅ 验证

```bash
./mvnw -q compile
```

无输出 = 通过。

---

## 6. Web 层（60 分钟）

### 6.1 `BookController`

新建 `src/main/java/com/library/web/BookController.java`：

```java
package com.library.web;

import com.library.model.Book;
import com.library.service.BookService;
import com.library.service.BusinessException;
import jakarta.validation.Valid;
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
 * 图书管理：列表、搜索、增删改查。
 */
@Controller
@RequestMapping("/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String keyword, Model model) {
        model.addAttribute("books", bookService.search(keyword));
        model.addAttribute("keyword", keyword);
        return "books/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        Book book = new Book();
        book.setTotalCopies(1);
        book.setAvailableCopies(1);
        model.addAttribute("book", book);
        model.addAttribute("editing", false);
        return "books/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("book") Book book,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("editing", false);
            return "books/form";
        }
        try {
            bookService.create(book);
        } catch (BusinessException e) {
            reject(binding, e);
            model.addAttribute("editing", false);
            return "books/form";
        }
        redirect.addFlashAttribute("message", "已新增图书《" + book.getTitle() + "》");
        return "redirect:/books";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("book", bookService.get(id));
        model.addAttribute("editing", true);
        return "books/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("book") Book book,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("editing", true);
            return "books/form";
        }
        try {
            bookService.update(id, book);
        } catch (BusinessException e) {
            reject(binding, e);
            model.addAttribute("editing", true);
            return "books/form";
        }
        redirect.addFlashAttribute("message", "已更新图书《" + book.getTitle() + "》");
        return "redirect:/books";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        bookService.delete(id);
        redirect.addFlashAttribute("message", "已删除该图书");
        return "redirect:/books";
    }

    /** 把业务异常回显到对应字段；无字段信息时作为整表单的全局错误。 */
    private void reject(BindingResult binding, BusinessException e) {
        if (e.getField() != null && binding.getFieldError(e.getField()) == null) {
            binding.rejectValue(e.getField(), "business", e.getMessage());
        } else {
            binding.reject("business", e.getMessage());
        }
    }
}
```

### 6.2 逐个概念拆解

| 注解 | 作用 |
|---|---|
| `@Controller` | 声明「这是个处理 HTTP 请求的类」，Spring 会创建它 |
| `@RequestMapping("/books")` | 类级别的路径前缀，下面所有方法都在这之下 |
| `@GetMapping` | 处理 GET 请求（浏览器地址栏直接访问） |
| `@PostMapping` | 处理 POST 请求（表单提交） |
| `@RequestParam` | 取 URL 里的查询参数，如 `/books?keyword=三体` |
| `@PathVariable` | 取路径里的变量，如 `/books/{id}/edit` 里的 id |
| `@ModelAttribute` | 把请求参数装进一个对象（表单绑定） |
| `@Valid` | 触发 `Book` 上的校验注解 |
| `Model` | 往里面 `addAttribute` 的东西，模板里能直接取到 |
| `RedirectAttributes` | 专用于重定向的 Model，`addFlashAttribute` 的数据只活一次 |

### 6.3 三个必须弄懂的设计点

**① 为什么「新增」和「编辑」用同一个 `books/form` 页面？**

因为表单长得一模一样，只是标题不同。用一个 `editing` 布尔值区分：

```java
model.addAttribute("editing", false);   // 新增
model.addAttribute("editing", true);    // 编辑
```

模板里 `th:text="${editing} ? '编辑图书' : '新增图书'"`。**少写一个页面，少一处维护。**

**② 为什么表单提交失败时 `return "books/form"` 而不是 `redirect:`？**

因为**要保住用户刚填的内容**。直接返回视图名 = 用当前 Model 渲染页面，Spring 会把 `book` 对象（用户填的值）放回表单里，错误提示也在。如果用重定向，数据就全丢了，用户得重新填一遍。

**③ `redirect.addFlashAttribute("message", ...)` 的"flash"是什么意思？**

提交成功后重定向到列表页，要显示「已新增图书《xxx》」。但这个提示**只能显示一次**——刷新页面就该消失。`addFlashAttribute` 的数据存在 session 里，被读取一次后自动删除，正好符合这个需求。（普通 `addAttribute` 在重定向后会丢失，因为重定向是新的请求。）

**④ `reject()` 这个方法在干什么？**

`BusinessException` 带着 `field`（比如 `"isbn"`）。这个方法把它转成 Spring 表单体系的字段错误，模板里的：

```html
<small class="field-error" th:if="${#fields.hasErrors('isbn')}" th:errors="*{isbn}"></small>
```

就能把「ISBN「xxx」已被另一本图书使用」显示在 ISBN 输入框下面。如果异常没有 `field`，就变成整表单的全局错误（显示在表单顶部）。

### 6.4 `HomeController`（临时版）

新建 `src/main/java/com/library/web/HomeController.java`：

```java
package com.library.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 首页。今天先简单地跳到图书列表。
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "redirect:/books";
    }
}
```

`"redirect:/books"` 里的 `redirect:` 前缀是 Spring MVC 的约定：**不是渲染页面，而是告诉浏览器「去访问 /books」**。

### ✅ 验证

```bash
./mvnw -q compile
```

编译通过。现在还不能跑——页面还没写呢。

---

## 7. 页面（75 分钟）

Thymeleaf 的语法先记住三个：

| 写法 | 含义 |
|---|---|
| `th:text="${expr}"` | 把表达式的值填进这个标签的文本里 |
| `th:if="${cond}"` | 条件为真才渲染这个标签 |
| `th:each="x : ${list}"` | 循环 |
| `th:href="@{/books}"` | 生成 URL（会自动加应用上下文路径） |
| `th:field="*{title}"` | 表单绑定：生成 `name="title"` 和 `value="当前值"` |
| `*{...}` | 配合 `th:object` 用的"当前对象"简写 |
| `@{...}` | URL 表达式，`@{/books/{id}(id=${b.id})}` 会生成 `/books/3` |

### 7.1 公共布局片段

新建 `src/main/resources/templates/fragments/layout.html`：

```html
<!DOCTYPE html>
<html lang="zh-CN" xmlns:th="http://www.thymeleaf.org">
<head th:fragment="head(pageTitle)">
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title th:text="${pageTitle} + ' · 图书馆管理系统'">图书馆管理系统</title>
    <link rel="stylesheet" th:href="@{/css/style.css}">
</head>
<body>

<nav class="navbar" th:fragment="navbar">
    <div class="navbar-inner">
        <a class="brand" th:href="@{/}">📚 图书馆管理系统</a>

        <div class="nav-links">
            <a th:href="@{/}">首页</a>
            <a th:href="@{/books}">图书</a>
        </div>
    </div>
</nav>

</body>
</html>
```

**`th:fragment` 是 Thymeleaf 的"片段"机制**——给一块 HTML 起个名字，别的页面可以复用。这里定义了两个：`head`（带一个参数 `pageTitle`）和 `navbar`。

别处这样用：

```html
<head th:replace="~{fragments/layout :: head('图书列表')}"></head>
<nav th:replace="~{fragments/layout :: navbar}"></nav>
```

`~{文件 :: 片段名}` 是引用语法。`th:replace` = 用片段**整个替换**这个标签。

> 为什么要有布局片段？因为导航栏、CSS 引用每个页面都要写一遍。抽出来后，改导航只改一个文件。**Day 2 加登录之后，导航栏要显示用户名和退出按钮——那时你只需改这一个文件。**

### 7.2 图书列表页

新建 `src/main/resources/templates/books/list.html`：

```html
<!DOCTYPE html>
<html lang="zh-CN" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragments/layout :: head('图书列表')}"></head>
<body>
<nav th:replace="~{fragments/layout :: navbar}"></nav>

<main class="container">
    <div class="page-header">
        <h1>图书列表</h1>
        <a class="btn btn-primary" th:href="@{/books/new}">+ 新增图书</a>
    </div>

    <div class="alert alert-success" th:if="${message}" th:text="${message}"></div>
    <div class="alert alert-error" th:if="${error}" th:text="${error}"></div>

    <form class="search-bar" method="get" th:action="@{/books}">
        <input type="text" name="keyword" th:value="${keyword}"
               placeholder="搜索书名 / 作者 / ISBN / 分类">
        <button class="btn" type="submit">搜索</button>
        <a class="btn btn-ghost" th:if="${keyword != null and !keyword.isBlank()}" th:href="@{/books}">清除</a>
    </form>

    <div class="table-wrap">
        <table class="table">
            <thead>
            <tr>
                <th>ID</th>
                <th>书名</th>
                <th>作者</th>
                <th>ISBN</th>
                <th>分类</th>
                <th class="num">总册数</th>
                <th class="num">可借</th>
                <th class="num">已借出</th>
                <th class="actions-col">操作</th>
            </tr>
            </thead>
            <tbody>
            <tr th:if="${books.isEmpty()}">
                <td colspan="9" class="empty">没有匹配的图书</td>
            </tr>
            <tr th:each="book : ${books}">
                <td th:text="${book.id}">1</td>
                <td th:text="${book.title}">书名</td>
                <td th:text="${book.author}">作者</td>
                <td th:text="${book.isbn}">ISBN</td>
                <td th:text="${book.category}">分类</td>
                <td class="num" th:text="${book.totalCopies}">0</td>
                <td class="num">
                    <span class="pill"
                          th:classappend="${book.availableCopies != null and book.availableCopies > 0} ? 'pill-ok' : 'pill-out'"
                          th:text="${book.availableCopies}">0</span>
                </td>
                <td class="num" th:text="${book.borrowedCopies}">0</td>
                <td class="actions">
                    <a class="btn btn-small btn-ghost"
                       th:href="@{/books/{id}/edit(id=${book.id})}">编辑</a>
                    <form th:action="@{/books/{id}/delete(id=${book.id})}" method="post"
                          onsubmit="return confirm('确定删除这本书吗？此操作不可撤销。');">
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

**看几个细节：**

- **`th:value="${keyword}"`** —— 搜索完把关键词填回输入框，用户能看到自己搜的是什么。
- **`th:classappend`** —— 在原有 class 基础上追加。可借数 > 0 时加 `pill-ok`（绿色），否则 `pill-out`（红色）。
- **`onsubmit="return confirm(...)"`** —— 原生 JS：返回 `false` 就不提交表单。删除前弹个确认框。
- **删除为什么用 `<form method="post">` 而不是 `<a>`？** 因为**删除是"写操作"，必须用 POST**。用 GET 的话，浏览器预取、爬虫、甚至用户回退都可能误触发删除。这是个重要习惯。

### 7.3 图书表单页

新建 `src/main/resources/templates/books/form.html`：

```html
<!DOCTYPE html>
<html lang="zh-CN" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragments/layout :: head('图书编辑')}"></head>
<body>
<nav th:replace="~{fragments/layout :: navbar}"></nav>

<main class="container narrow">
    <div class="page-header">
        <h1 th:text="${editing} ? '编辑图书' : '新增图书'">图书</h1>
        <a class="btn btn-ghost" th:href="@{/books}">返回列表</a>
    </div>

    <form class="card form" method="post" th:object="${book}"
          th:action="${editing} ? @{/books/{id}(id=${book.id})} : @{/books}">

        <div class="alert alert-error" th:if="${#fields.hasGlobalErrors()}">
            <div th:each="err : ${#fields.globalErrors()}" th:text="${err}">错误</div>
        </div>

        <label>
            <span>书名 *</span>
            <input type="text" th:field="*{title}" autofocus>
            <small class="field-error" th:if="${#fields.hasErrors('title')}" th:errors="*{title}"></small>
        </label>

        <div class="form-row">
            <label>
                <span>作者 *</span>
                <input type="text" th:field="*{author}">
                <small class="field-error" th:if="${#fields.hasErrors('author')}" th:errors="*{author}"></small>
            </label>
            <label>
                <span>ISBN *</span>
                <input type="text" th:field="*{isbn}">
                <small class="field-error" th:if="${#fields.hasErrors('isbn')}" th:errors="*{isbn}"></small>
            </label>
        </div>

        <label>
            <span>分类</span>
            <input type="text" th:field="*{category}" placeholder="如：计算机 / 文学 / 历史">
            <small class="field-error" th:if="${#fields.hasErrors('category')}" th:errors="*{category}"></small>
        </label>

        <div class="form-row">
            <label>
                <span>总册数 *</span>
                <input type="number" min="1" th:field="*{totalCopies}">
                <small class="field-error" th:if="${#fields.hasErrors('totalCopies')}" th:errors="*{totalCopies}"></small>
            </label>
            <label>
                <span>可借册数 *</span>
                <input type="number" min="0" th:field="*{availableCopies}">
                <small class="field-error" th:if="${#fields.hasErrors('availableCopies')}" th:errors="*{availableCopies}"></small>
            </label>
        </div>

        <div class="form-actions">
            <button class="btn btn-primary" type="submit">保存</button>
            <a class="btn btn-ghost" th:href="@{/books}">取消</a>
        </div>
    </form>
</main>

</body>
</html>
```

**三个关键机制：**

1. **`th:object="${book}"` + `th:field="*{title}"`** —— 表单和 `Book` 对象绑定。`th:field` 会自动生成 `name="title"`，编辑时还会**自动填上当前值**。这就是为什么编辑页能回显数据，一行 JS 都不用写。
2. **`th:action="${editing} ? @{/books/{id}(id=${book.id})} : @{/books}"`** —— 一套表单，两个提交地址：编辑时 POST 到 `/books/3`，新增时 POST 到 `/books`。**和控制器里的 `@PostMapping("/{id}")` / `@PostMapping` 两两对应。**
3. **`#fields.hasErrors('title')` / `th:errors="*{title}"`** —— 显示某个字段的校验错误。`#fields` 是 Thymeleaf 内置对象，专门配合 Spring 的表单校验。

> 🔎 **`th:errors="*{title}"` 显示的错误文字从哪来？** 三个来源，按优先级：(1) `Book` 上 `@NotBlank(message="书名不能为空")` 的 message；(2) 控制器 `binding.rejectValue(...)` 塞进去的；(3) `BusinessException` 经 `reject()` 转换来的。

### 7.4 CSS（拷贝即可）

CSS 不是今天的学习重点，直接从上一步的备份里拷：

```bash
mkdir -p src/main/resources/static/css
cp ../hi-backup/src/main/resources/static/css/style.css src/main/resources/static/css/style.css
```

> 你只需要知道模板里用到的几个 class 名：`container`（居中容器）、`card`（白卡片）、`btn / btn-primary / btn-danger / btn-ghost`（按钮）、`table`（表格）、`form`（表单）、`alert-success / alert-error`（提示条）、`pill-ok / pill-out`（可借数徽章）。名字对上了页面就好看，对不上就是没样式——不影响功能。

### ✅ 验证：跑起来！

```bash
./mvnw spring-boot:run
```

打开 <http://localhost:8080> —— 应该自动跳到 `/books`，看到空列表和「没有匹配的图书」。

---

## 8. 自测清单（今天必须逐条走完）

打开浏览器，按顺序做，**每条都亲手验证**：

- [ ] 访问 `http://localhost:8080` → 自动跳到 `/books`，显示空列表
- [ ] 点「+ 新增图书」→ 出现表单，总册数/可借册数默认都是 1
- [ ] **什么都填，直接点保存** → 表单重新出现，书名/作者/ISBN 下面出现红色提示「xxx不能为空」
- [ ] 填一本书：`三体 / 刘慈欣 / 9787536692930 / 科幻 / 3` → 保存 → 回到列表，看到这本书，可借数显示 **3**（绿色徽章）
- [ ] 再新增一本，**ISBN 故意填一样的** `9787536692930` → 保存被拒绝，ISBN 输入框下方提示「ISBN「…」已被另一本图书使用」
- [ ] 新增第二本：`活着 / 余华 / 9787506365437 / 小说 / 5`
- [ ] **总册数填 2、可借册数填 5** → 保存 → 提示「可借册数不能大于总册数（当前 2）」
- [ ] 点某本书的「编辑」→ 表单**已经填好了这本书的数据**，改书名保存 → 列表里变了
- [ ] 搜索框输入 `科幻` → 只剩《三体》；输入 `余华` → 只剩《活着》；输入 `三体` → 书名匹配
- [ ] 搜索框输入 `不存在的书` → 显示「没有匹配的图书」，旁边出现「清除」按钮，点了能回到全部
- [ ] 点「删除」→ 弹出确认框 → 确定 → 列表里消失
- [ ] 打开 `data/books.json`，看到格式化好的 JSON 数组
- [ ] **`Ctrl+C` 停掉程序，再 `./mvnw spring-boot:run` 重启** → 数据还在 ✅ **（这条最重要，证明持久化真的生效了）**
- [ ] 访问 `http://localhost:8080/books/999/edit` → 显示 **404**（不是 500）

全过 = **V1 完成** 🎉

---

## 9. 常见坑速查

| 现象 | 原因 | 解决 |
|---|---|---|
| 启动报 `Port 8080 was already in use` | 有别的进程占着 8080 | `lsof -ti:8080 \| xargs kill -9` |
| 编译报「找不到符号 ObjectMapper」 | import 写成了 `com.fasterxml.jackson.databind` | 改成 `tools.jackson.databind` |
| 编译报 `unreported exception JsonProcessingException` | 套用了 Jackson 2 的老代码 | Jackson 3 的 `JacksonException` 是非受检的，删掉 `throws` |
| 页面 404，但类明明写了 | 包位置不对，没被扫描到 | 所有类必须在 `com.library` 或其子包下 |
| 中文全是 `???` 或乱码 | 编码不是 UTF-8 | pom 已配好；IDEA 里检查 File Encodings 三处都是 UTF-8 |
| `th:field` 报错 `Neither BindingResult nor plain target object` | 表单页缺少 `th:object` 对应的 Model 属性 | 控制器里确认 `model.addAttribute("book", ...)` |
| 改完 HTML 刷新没变化 | 模板缓存 | 确认 `spring.thymeleaf.cache: false` |
| 保存后列表没变，也没报错 | 提交失败被静默吞了 | 看浏览器是否停在表单页 + 有没有红字提示 |
| `data/` 里没有 JSON 文件 | 只有写入时才会创建 | 新增一本书后就有了 |

---

## 10. 复盘：回答这几个问题（30 分钟）

合上代码，**用自己的话**写在笔记里。答不上来的，回去重看对应章节。

1. 从浏览器点「保存」到数据写进 `books.json`，中间经过了哪些类？按顺序说出来。
2. `JsonFileStore.update()` 为什么不直接写成"读、改、写"三步？**那把锁保护的是什么？**
3. 如果没有 `@JsonIgnore`，`data/books.json` 里会多出什么？会有什么问题？
4. `BookService.validate()` 里的校验，和 `Book` 上的 `@NotBlank` 有什么本质区别？（提示：一个校验的是「格式」，一个校验的是「业务规则」；一个需要查仓库，一个不需要）
5. 为什么"删除"必须用 POST？
6. 表单提交失败返回 `books/form`、成功返回 `redirect:/books`——**如果成功也直接返回 `books/list` 会怎样？**（提示：刷新页面时会重复提交）

### 🛠 动手改一改（选做，但强烈建议）

1. **加分页**：现在 250 本书会一页全显示。在 `BookService.search()` 后面加个分页，页面上加「上一页 / 下一页」。
2. **加排序**：点表头「书名」按书名排序，再点一次倒序。提示：加个 `sort` 查询参数。
3. **加一个字段** `publisher`（出版社），走完「模型 → 表单 → 列表 → 校验」全流程。**这一条做完，你就掌握了整个 CRUD 的套路。**

---

## 11. 明天预告

今天的程序**谁都能用**——包括删掉你所有图书的人。而且"新增图书"和"借阅图书"这两件事，显然不该是同一批人干的。

Day 2 我们会：

- 引入 **Spring Security**，做出登录页
- 建 `User` / `Role` 模型，密码用 **BCrypt** 哈希存储（**绝不存明文**）
- 分成**管理员 / 读者**两种角色
- 让「新增 / 编辑 / 删除图书」**只有管理员能做**，读者只能看
- 首页从"跳转到列表"变成一个真正的**概览页**

到时候你会发现：**今天写的 `BookController`，只需要加几个 `@PreAuthorize` 注解就完成了权限控制**——因为好的分层设计，改动总是局部的。

---

**V1 打卡：** 当你完成自测清单并重启验证了数据持久化，在 [day02.md](day02.md) 开头写下今天的日期和一句话感受。明天见 👋
