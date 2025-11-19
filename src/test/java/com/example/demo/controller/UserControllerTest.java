package com.example.demo.controller;

import com.example.demo.dto.UserRequest;
import com.example.demo.dto.UserResponse;
import com.example.demo.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testCreateUser() throws Exception {
        UserResponse response = new UserResponse(1L, "Test User", "test@example.com");
        Mockito.when(userService.createUser(any(UserRequest.class))).thenReturn(response);

        UserRequest request = new UserRequest();
        request.setName("Test User");
        request.setEmail("test@example.com");

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Test User"))
                .andExpect(jsonPath("$.data.email").value("test@example.com"));
    }

    @Test
    void testGetAllUsers() throws Exception {
        List<UserResponse> users = List.of(new UserResponse(1L, "John", "john@example.com"));
        Page<UserResponse> page = new PageImpl<>(users, PageRequest.of(0, 5, Sort.by("id").ascending()), users.size());
        Mockito.when(userService.getAllUsers(any(PageRequest.class))).thenReturn(page);

        mockMvc.perform(get("/users?page=1")) // 1-based page
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("John"))
                .andExpect(jsonPath("$.data[0].email").value("john@example.com"))
                .andExpect(jsonPath("$.pageInfo.currentPage").value(1));
    }

    @Test
    void testSearchUsers() throws Exception {
        List<UserResponse> users = List.of(new UserResponse(1L, "John", "john@example.com"));
        Page<UserResponse> page = new PageImpl<>(users, PageRequest.of(0, 5, Sort.by("id").ascending()), users.size());
        Mockito.when(userService.searchUsers(eq("John"), any(PageRequest.class))).thenReturn(page);

        mockMvc.perform(get("/users/search?keyword=John&page=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("John"))
                .andExpect(jsonPath("$.data[0].email").value("john@example.com"))
                .andExpect(jsonPath("$.pageInfo.currentPage").value(1));
    }

    @Test
    void testDeleteUser() throws Exception {
        mockMvc.perform(delete("/users/1"))
                .andExpect(status().isOk());
    }

    @Test
    void testRestoreUser() throws Exception {
        mockMvc.perform(put("/users/restore/1"))
                .andExpect(status().isOk());
    }
}
