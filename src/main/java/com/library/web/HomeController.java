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
