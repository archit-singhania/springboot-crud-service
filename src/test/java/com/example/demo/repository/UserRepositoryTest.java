package com.example.demo.repository;

import com.example.demo.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    // Helper method to create a user with password
    private User createUser(String name, String email, String password) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(password);
        return userRepository.save(user);
    }

    @Test
    void testSaveAndFindUser() {
        User saved = createUser("John Doe", "john@test.com", "secret123");

        assertNotNull(saved.getId());

        Optional<User> found = userRepository.findById(saved.getId());
        assertTrue(found.isPresent());

        User u = found.get();
        assertEquals("John Doe", u.getName());
        assertEquals("john@test.com", u.getEmail());
        assertEquals("secret123", u.getPassword());
    }

    @Test
    void testDeleteUser() {
        User saved = createUser("Jane Doe", "jane@test.com", "pass123");

        userRepository.deleteById(saved.getId());

        Optional<User> found = userRepository.findById(saved.getId());
        assertFalse(found.isPresent());
    }

    @Test
    void testFindAllUsers() {
        createUser("ABC", "abc@test.com", "abc123");
        createUser("XYZ", "xyz@test.com", "xyz123");
        createUser("DEF", "def@test.com", "def123");

        List<User> allUsers = userRepository.findAll();

        assertTrue(allUsers.size() >= 3, "Should have at least 3 users");

        assertTrue(allUsers.stream().anyMatch(u -> u.getEmail().equals("abc@test.com")));
        assertTrue(allUsers.stream().anyMatch(u -> u.getEmail().equals("xyz@test.com")));
        assertTrue(allUsers.stream().anyMatch(u -> u.getEmail().equals("def@test.com")));
    }

    @Test
    void testUpdateUser() {
        User saved = createUser("Michael", "michael@test.com", "mike123");
        Long userId = saved.getId();

        saved.setName("Michael Updated");
        saved.setEmail("michael.updated@test.com");
        saved.setPassword("mikeUpdated123");

        User updated = userRepository.save(saved);

        assertEquals(userId, updated.getId());
        assertEquals("Michael Updated", updated.getName());
        assertEquals("michael.updated@test.com", updated.getEmail());
        assertEquals("mikeUpdated123", updated.getPassword());

        Optional<User> fetched = userRepository.findById(userId);
        assertTrue(fetched.isPresent());
        assertEquals("Michael Updated", fetched.get().getName());
        assertEquals("michael.updated@test.com", fetched.get().getEmail());
        assertEquals("mikeUpdated123", fetched.get().getPassword());
    }
}
