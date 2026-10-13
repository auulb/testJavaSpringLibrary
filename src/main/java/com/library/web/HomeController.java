package com.library.web;

import com.library.model.Book;
import com.library.security.LibraryUserPrincipal;
import com.library.service.BookService;
import com.library.service.UserService;
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
    private final UserService userService;

    public HomeController(BookService bookService, UserService userService) {
        this.bookService = bookService;
        this.userService = userService;
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

        // 用户总数只对管理员可见
        if (principal != null && principal.isAdmin()) {
            model.addAttribute("userCount", userService.count());
        }
        return "dashboard";
    }
}
