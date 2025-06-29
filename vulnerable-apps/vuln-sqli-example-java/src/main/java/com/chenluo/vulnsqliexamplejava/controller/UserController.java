package com.chenluo.vulnsqliexamplejava.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@Controller
public class UserController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/")
    public String index(Model model) {
        // Optionally load all users on the main page (safely)
        try {
            List<Map<String, Object>> users = jdbcTemplate.queryForList("SELECT id, username, info FROM users");
            model.addAttribute("users", users);
        } catch (Exception e) {
            model.addAttribute("error", "Error loading initial users: " + e.getMessage());
        }
        return "index"; // Corresponds to src/main/resources/templates/index.html
    }

    @GetMapping("/search")
    public String searchUser(@RequestParam(name = "username", required = false) String username, Model model) {
        model.addAttribute("queryUsername", username); // Keep the searched username in the input field

        if (username == null || username.trim().isEmpty()) {
            model.addAttribute("message", "Please enter a username to search.");
            return "index";
        }

        // VULNERABLE SQL QUERY
        String sql = "SELECT id, username, info FROM users WHERE username = '" + username + "'";
        // For demonstration, we print the query. In a real app, this is a security risk.
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
            // This might expose too much information in a real app, but useful for a靶场
            model.addAttribute("error", "Error executing query: " + e.getMessage());
            e.printStackTrace(); // Log to console
        }
        return "index";
    }
}