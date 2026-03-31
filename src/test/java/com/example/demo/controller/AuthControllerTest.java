package com.example.demo.controller;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AuthControllerTest {

    @Mock
    private UserRepository userRepo;

    @Mock
    private PasswordEncoder encoder;

    @Mock
    private JwtTokenUtil jwt;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testRegister() {
        User user = new User();
        user.setEmail("test@example.com");
        user.setPassword("pass");

        when(encoder.encode(user.getPassword())).thenReturn("encodedPass");
        when(userRepo.save(any(User.class))).thenReturn(user);

        String result = authController.register(user);

        assertEquals("Registration successful", result);
        verify(userRepo, times(1)).save(user);
    }

    @Test
    void testLoginSuccess() {
        String email = "test@example.com";
        String password = "pass";
        String encodedPassword = "encodedPass";
        String token = "jwtToken";

        User user = new User();
        user.setEmail(email);
        user.setPassword(encodedPassword);

        when(userRepo.findByEmail(email)).thenReturn(Optional.of(user));
        when(encoder.matches(password, encodedPassword)).thenReturn(true);
        when(jwt.generateAccessToken(anyString())).thenReturn("fake-access-token");
        when(jwt.generateRefreshToken(anyString())).thenReturn("fake-refresh-token");

        var response = authController.login(new com.example.demo.dto.LoginRequest(email, password));

        assertEquals("fake-access-token", response.getAccessToken());
        assertEquals("fake-refresh-token", response.getRefreshToken());
    }

    @Test
    void testLoginInvalidPassword() {
        String email = "test@example.com";
        String password = "wrong";
        User user = new User();
        user.setEmail(email);
        user.setPassword("encodedPass");

        when(userRepo.findByEmail(email)).thenReturn(Optional.of(user));
        when(encoder.matches(password, "encodedPass")).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                authController.login(new com.example.demo.dto.LoginRequest(email, password))
        );
        assertEquals("Invalid Password", ex.getMessage());
    }
}
