package com.chenluo.vulnsqliexamplejava.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * MVC controller for SQL injection lab pages.
 *
 * <p>The {@code /search} endpoint intentionally uses insecure SQL string concatenation for
 * vulnerability demonstration.
 */
@Controller
public class UserController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Renders home page and loads baseline user list.
     *
     * @param model Thymeleaf model
     * @return view name
     */
    @GetMapping("/")
    public String index(Model model) {
        try {
            List<Map<String, Object>> users = jdbcTemplate.queryForList("SELECT id, username, info FROM users");
            model.addAttribute("users", users);
        } catch (Exception e) {
            model.addAttribute("error", "Error loading initial users: " + e.getMessage());
        }
        return "index";
    }

    /**
     * Executes intentionally vulnerable query by username.
     *
     * @param username request query parameter
     * @param model Thymeleaf model
     * @return view name
     */
    @GetMapping("/search")
    public String searchUser(@RequestParam(name = "username", required = false) String username, Model model) {
        model.addAttribute("queryUsername", username);

        if (username == null || username.trim().isEmpty()) {
            model.addAttribute("message", "Please enter a username to search.");
            return "index";
        }

        // Intentionally vulnerable SQL construction for lab use.
        String sql = "SELECT id, username, info FROM users WHERE username = '" + username + "'";
        System.out.println("Executing SQL: " + sql);
        model.addAttribute("executedSql", sql);

        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            if (result.isEmpty()) {
                model.addAttribute("message", "No user found with username: " + username);
            } else {
                model.addAttribute("results", result);
            }
        } catch (Exception e) {
            model.addAttribute("error", "Error executing query: " + e.getMessage());
            e.printStackTrace();
        }

        return "index";
    }
}
