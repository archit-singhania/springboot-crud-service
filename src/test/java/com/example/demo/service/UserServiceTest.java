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

    private User createUser(Long id, String name, String email, boolean deleted) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setEmail(email);
        user.setDeleted(deleted);
        return user;
    }

    @Test
    void testCreateUser() {
        UserRequest request = new UserRequest();
        request.setName("John");
        request.setEmail("john@example.com");

        User savedUser = createUser(1L, "John", "john@example.com", false);

        when(userRepository.existsByEmailAndIsDeletedFalse("john@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserResponse response = userService.createUser(request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("John", response.getName());
    }

    @Test
    void testCreateUserEmailAlreadyExists() {
        UserRequest request = new UserRequest();
        request.setName("John");
        request.setEmail("john@example.com");

        when(userRepository.existsByEmailAndIsDeletedFalse("john@example.com"))
                .thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> userService.createUser(request));
    }

    @Test
    void testGetAllUsersWithPagination() {
        List<User> usersList = List.of(
                createUser(1L, "John", "john@example.com", false)
        );
        Page<User> page = new PageImpl<>(usersList);

        when(userRepository.findByIsDeletedFalse(any(PageRequest.class)))
                .thenReturn(page);

        Page<UserResponse> users = userService.getAllUsers(PageRequest.of(0, 10));

        assertEquals(1, users.getTotalElements());
        assertEquals("John", users.getContent().get(0).getName());
    }

    @Test
    void testGetUserByIdFound() {
        User user = createUser(1L, "John", "john@example.com", false);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        UserResponse response = userService.getUserById(1L);

        assertEquals("John", response.getName());
    }

    @Test
    void testGetUserByIdNotFound() {
        when(userRepository.findById(1L))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> userService.getUserById(1L));
    }

    @Test
    void testUpdateUser() {
        UserRequest request = new UserRequest();
        request.setName("John Updated");
        request.setEmail("johnupdated@example.com");

        User existing = createUser(1L, "John", "john@example.com", false);
        User updated = createUser(1L, "John Updated", "johnupdated@example.com", false);

        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmailAndIdNotAndIsDeletedFalse("johnupdated@example.com", 1L))
                .thenReturn(false);
        when(userRepository.save(existing)).thenReturn(updated);

        UserResponse response = userService.updateUser(1L, request);

        assertEquals("John Updated", response.getName());
        assertEquals("johnupdated@example.com", response.getEmail());
    }

    @Test
    void testUpdateUserEmailAlreadyExists() {
        UserRequest request = new UserRequest();
        request.setName("John Updated");
        request.setEmail("johnupdated@example.com");

        User existing = createUser(1L, "John", "john@example.com", false);

        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmailAndIdNotAndIsDeletedFalse("johnupdated@example.com", 1L))
                .thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> userService.updateUser(1L, request));
    }

    @Test
    void testDeleteUserExists() {
        User existing = createUser(1L, "John", "john@example.com", false);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(existing));

        assertDoesNotThrow(() -> userService.deleteUser(1L));

        assertTrue(existing.isDeleted());
        verify(userRepository, times(1)).save(existing);
    }

    @Test
    void testDeleteUserNotExists() {
        when(userRepository.findById(1L))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> userService.deleteUser(1L));
    }

    @Test
    void testRestoreUser() {
        User deletedUser = createUser(1L, "John", "john@example.com", true);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(deletedUser));

        assertDoesNotThrow(() -> userService.restoreUser(1L));

        assertFalse(deletedUser.isDeleted());
        verify(userRepository).save(deletedUser);
    }

    @Test
    void testRestoreUserNotDeleted() {
        User activeUser = createUser(1L, "John", "john@example.com", false);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(activeUser));

        assertThrows(IllegalStateException.class,
                () -> userService.restoreUser(1L));
    }
}
