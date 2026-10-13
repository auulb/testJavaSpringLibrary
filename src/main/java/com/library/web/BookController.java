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
