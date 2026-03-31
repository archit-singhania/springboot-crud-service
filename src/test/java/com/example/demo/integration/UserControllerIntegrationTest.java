package com.example.demo.integration;

import com.example.demo.DemoApplication;
import com.example.demo.dto.UserRequest;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = DemoApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Import(UserControllerIntegrationTest.TestSecurityConfig.class)
class UserControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepo;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/users";
        userRepo.deleteAll();
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    void testCreateAndGetUser() {
        UserRequest req = new UserRequest();
        req.setName("John Doe");
        req.setEmail("john@example.com");

        HttpEntity<UserRequest> entity = new HttpEntity<>(req, jsonHeaders());
        ResponseEntity<String> createResp = restTemplate.postForEntity(baseUrl, entity, String.class);
        assertEquals(HttpStatus.CREATED, createResp.getStatusCode());
        assertTrue(createResp.getBody().contains("User created successfully"));

        List<User> users = userRepo.findAll();
        assertEquals(1, users.size());
        assertEquals("John Doe", users.get(0).getName());

        ResponseEntity<String> getResp = restTemplate.getForEntity(baseUrl + "/" + users.get(0).getId(), String.class);
        assertEquals(HttpStatus.OK, getResp.getStatusCode());
        assertTrue(getResp.getBody().contains("John Doe"));
    }

    @Test
    void testPagination() {
        for (int i = 1; i <= 12; i++) {
            User user = new User();
            user.setName("User " + i);
            user.setEmail("user" + i + "@example.com");
            userRepo.save(user);
        }

        UriComponentsBuilder uri = UriComponentsBuilder.fromHttpUrl(baseUrl).queryParam("page", 2);
        ResponseEntity<String> response = restTemplate.getForEntity(uri.toUriString(), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("User 6"));
    }

    @Test
    void testSearchUsers() {
        User u1 = new User();
        u1.setName("Alice");
        u1.setEmail("alice@example.com");
        userRepo.save(u1);

        User u2 = new User();
        u2.setName("Bob");
        u2.setEmail("bob@example.com");
        userRepo.save(u2);

        UriComponentsBuilder uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/search")
                .queryParam("keyword", "ali")
                .queryParam("page", 1);
        ResponseEntity<String> resp = restTemplate.getForEntity(uri.toUriString(), String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().contains("Alice"));
        assertFalse(resp.getBody().contains("Bob"));
    }

    @Test
    void testUpdateUser() {
        User user = new User();
        user.setName("Old Name");
        user.setEmail("old@example.com");
        user = userRepo.save(user);

        UserRequest req = new UserRequest();
        req.setName("New Name");
        req.setEmail("new@example.com");

        HttpEntity<UserRequest> entity = new HttpEntity<>(req, jsonHeaders());
        ResponseEntity<String> resp = restTemplate.exchange(baseUrl + "/" + user.getId(), HttpMethod.PUT, entity, String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().contains("User updated successfully"));
        assertTrue(resp.getBody().contains("New Name"));
    }

    @Test
    void testDeleteAndRestoreUser() {
        User user = new User();
        user.setName("To Delete");
        user.setEmail("delete@example.com");
        user = userRepo.save(user);

        restTemplate.delete(baseUrl + "/" + user.getId());
        assertTrue(userRepo.findById(user.getId()).get().isDeleted());

        restTemplate.put(baseUrl + "/restore/" + user.getId(), null);
        assertFalse(userRepo.findById(user.getId()).get().isDeleted());
    }

    @Configuration
    static class TestSecurityConfig {
        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }
}
