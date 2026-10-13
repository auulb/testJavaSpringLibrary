package com.library.service;

import com.library.model.Book;
import com.library.repository.BookRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * 图书业务逻辑：增删改查 + 借阅/归还。
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
