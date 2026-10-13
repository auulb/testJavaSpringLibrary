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
