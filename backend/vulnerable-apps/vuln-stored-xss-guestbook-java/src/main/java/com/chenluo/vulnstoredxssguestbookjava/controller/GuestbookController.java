package com.chenluo.vulnstoredxssguestbookjava.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * Intentionally unsafe guestbook controller used for stored XSS practice.
 */
@Controller
public class GuestbookController {

    private final JdbcTemplate jdbcTemplate;

    public GuestbookController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/")
    public String index(Model model) {
        List<Map<String, Object>> entries = jdbcTemplate.queryForList(
                "SELECT id, author, content, created_at FROM guestbook_entries ORDER BY id DESC"
        );
        model.addAttribute("entries", entries);
        return "index";
    }

    @PostMapping("/entries")
    public String createEntry(
            @RequestParam(name = "author", defaultValue = "anonymous") String author,
            @RequestParam(name = "content", defaultValue = "") String content,
            RedirectAttributes redirectAttributes
    ) {
        if (content.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Content must not be empty.");
            return "redirect:/";
        }

        jdbcTemplate.update(
                "INSERT INTO guestbook_entries(author, content, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
                author,
                content
        );
        redirectAttributes.addFlashAttribute(
                "message",
                "Entry stored. The feed intentionally renders HTML without escaping for training use."
        );
        return "redirect:/";
    }
}
