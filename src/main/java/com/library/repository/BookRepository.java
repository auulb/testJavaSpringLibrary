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
