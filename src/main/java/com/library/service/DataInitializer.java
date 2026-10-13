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
