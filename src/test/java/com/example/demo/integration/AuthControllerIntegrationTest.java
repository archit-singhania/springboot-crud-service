package com.example.demo.integration;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)

class AuthControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepo;

    @Test
    void testRegisterAndLogin() {
        String url = "http://localhost:" + port + "/auth/register";

        User user = new User();
        user.setEmail("integration@example.com");
        user.setPassword("password");
        user.setName("Integration User");

        ResponseEntity<String> registerResponse = restTemplate.postForEntity(url, user, String.class);
        assertEquals(HttpStatus.OK, registerResponse.getStatusCode());
        assertEquals("Registration successful", registerResponse.getBody());

        // Login
        String loginUrl = "http://localhost:" + port + "/auth/login";
        var loginReq = new com.example.demo.dto.LoginRequest("integration@example.com", "password");

        ResponseEntity<com.example.demo.dto.LoginResponse> loginResponse =
                restTemplate.postForEntity(loginUrl, loginReq, com.example.demo.dto.LoginResponse.class);

        assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
        assertNotNull(loginResponse.getBody().getToken());
    }
}
