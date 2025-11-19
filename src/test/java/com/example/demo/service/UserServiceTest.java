package com.example.demo.service;

import com.example.demo.dto.UserRequest;
import com.example.demo.dto.UserResponse;
import com.example.demo.exception.NotFoundException;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceTest {

    private UserRepository userRepository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        userService = new UserServiceImpl(userRepository);
    }

    @Test
    void testCreateUser() {
        UserRequest request = new UserRequest();
        request.setName("John");
        request.setEmail("john@example.com");

        when(userRepository.existsByEmailAndIsDeletedFalse("john@example.com")).thenReturn(false);
        User savedUser = new User(1L, "John", "john@example.com", false);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserResponse response = userService.createUser(request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("John", response.getName());
    }

    @Test
    void testGetAllUsers() {
        User user = new User(1L, "John", "john@example.com", false);
        Page<User> page = new PageImpl<>(List.of(user), PageRequest.of(0, 5, Sort.by("id").ascending()), 1);
        when(userRepository.findByIsDeletedFalse(any(PageRequest.class))).thenReturn(page);

        Page<UserResponse> result = userService.getAllUsers(PageRequest.of(0, 5, Sort.by("id").ascending()));

        assertEquals(1, result.getContent().size());
        assertEquals("John", result.getContent().get(0).getName());
    }

    @Test
    void testGetUserByIdFound() {
        User user = new User(1L, "John", "john@example.com", false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse response = userService.getUserById(1L);

        assertEquals("John", response.getName());
    }

    @Test
    void testGetUserByIdNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> userService.getUserById(1L));
    }

    @Test
    void testUpdateUser() {
        UserRequest request = new UserRequest();
        request.setName("John Updated");
        request.setEmail("johnupdated@example.com");

        User existing = new User(1L, "John", "john@example.com", false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmailAndIdNotAndIsDeletedFalse("johnupdated@example.com", 1L)).thenReturn(false);

        User updated = new User(1L, "John Updated", "johnupdated@example.com", false);
        when(userRepository.save(existing)).thenReturn(updated);

        UserResponse response = userService.updateUser(1L, request);

        assertEquals("John Updated", response.getName());
    }

    @Test
    void testDeleteUser() {
        User existing = new User(1L, "John", "john@example.com", false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertDoesNotThrow(() -> userService.deleteUser(1L));
        assertTrue(existing.isDeleted());
        verify(userRepository, times(1)).save(existing);
    }

    @Test
    void testRestoreUser() {
        User deletedUser = new User(1L, "John", "john@example.com", true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(deletedUser));

        assertDoesNotThrow(() -> userService.restoreUser(1L));
        assertFalse(deletedUser.isDeleted());
        verify(userRepository, times(1)).save(deletedUser);
    }

    @Test
    void testSearchUsers() {
        User user = new User(1L, "John", "john@example.com", false);
        Page<User> page = new PageImpl<>(List.of(user), PageRequest.of(0, 5, Sort.by("id").ascending()), 1);
        when(userRepository.searchByNameOrEmail("John", PageRequest.of(0, 5, Sort.by("id").ascending()))).thenReturn(page);

        Page<UserResponse> result = userService.searchUsers("John", PageRequest.of(0, 5, Sort.by("id").ascending()));

        assertEquals(1, result.getContent().size());
        assertEquals("John", result.getContent().get(0).getName());
    }
}
