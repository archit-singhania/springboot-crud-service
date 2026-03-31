package com.example.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HomeController {

    @GetMapping("/")
    public Map<String, Object> home() {
        return Map.of(
                "status", "running",
                "application", "Spring Boot CRUD API",
                "description", "This API supports Create, Read, Update, and Delete operations for User entities.",
                "endpoints", Map.of(
                        "GET /users", "Fetch all users",
                        "GET /users/{id}", "Fetch a user by ID",
                        "POST /users", "Create a new user",
                        "PUT /users/{id}", "Update existing user",
                        "DELETE /users/{id}", "Delete a user"
                )
        );
    }
}
