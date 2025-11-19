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

    @Test
    void testSaveAndFindUser() {
        User user = new User();
        user.setName("John Doe");
        user.setEmail("john@test.com");

        User saved = userRepository.save(user);

        assertNotNull(saved.getId());

        Optional<User> found = userRepository.findById(saved.getId());
        assertTrue(found.isPresent());

        User u = found.get();
        assertEquals("John Doe", u.getName());
        assertEquals("john@test.com", u.getEmail());
    }

    @Test
    void testDeleteUser() {
        User user = new User();
        user.setName("Jane Doe");
        user.setEmail("jane@test.com");

        User saved = userRepository.save(user);

        userRepository.deleteById(saved.getId());

        Optional<User> found = userRepository.findById(saved.getId());
        assertFalse(found.isPresent());
    }

    @Test
    void testFindAllUsers() {
        User user1 = new User();
        user1.setName("ABC");
        user1.setEmail("abc@test.com");

        User user2 = new User();
        user2.setName("XYZ");
        user2.setEmail("xyz@test.com");

        User user3 = new User();
        user3.setName("DEF");
        user3.setEmail("def@test.com");

        userRepository.save(user1);
        userRepository.save(user2);
        userRepository.save(user3);

        List<User> allUsers = userRepository.findAll();

        assertTrue(allUsers.size() >= 3, "Should have at least 3 users");

        assertTrue(allUsers.stream().anyMatch(u -> u.getEmail().equals("abc@test.com")));
        assertTrue(allUsers.stream().anyMatch(u -> u.getEmail().equals("xyz@test.com")));
        assertTrue(allUsers.stream().anyMatch(u -> u.getEmail().equals("def@test.com")));
    }

    @Test
    void testUpdateUser() {
        User user = new User();
        user.setName("Michael");
        user.setEmail("michael@test.com");

        User saved = userRepository.save(user);
        Long userId = saved.getId();

        saved.setName("Michael");
        saved.setEmail("michael@test.com");

        User updated = userRepository.save(saved);

        assertEquals(userId, updated.getId(), "ID should remain the same");
        assertEquals("Michael", updated.getName());
        assertEquals("michael@test.com", updated.getEmail());

        Optional<User> fetched = userRepository.findById(userId);
        assertTrue(fetched.isPresent());
        assertEquals("Michael", fetched.get().getName());
        assertEquals("michael@test.com", fetched.get().getEmail());
    }
}