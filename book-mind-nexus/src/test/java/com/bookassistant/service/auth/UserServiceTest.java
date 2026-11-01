package com.bookassistant.service.auth;

import com.bookassistant.entity.User;
import com.bookassistant.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class UserServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        passwordEncoder = Mockito.mock(PasswordEncoder.class);
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    void testRegister_Success() {
        Mockito.when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        Mockito.when(passwordEncoder.encode(Mockito.anyString())).thenReturn("encoded-password");
        Mockito.when(userRepository.save(Mockito.any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            return u;
        });

        User user = userService.register("test@example.com", "password123", "Test User");

        assertNotNull(user);
        assertEquals("test@example.com", user.getEmail());
        assertEquals("Test User", user.getName());
        assertNotNull(user.getPasswordHash());
        assertNotEquals("password123", user.getPasswordHash());
    }

    @Test
    void testRegister_DuplicateEmail() {
        Mockito.when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> userService.register("test@example.com", "password123", "Test User"));
        assertTrue(ex.getMessage().contains("already registered"));
    }

    @Test
    void testRegister_EmptyEmail() {
        assertThrows(IllegalArgumentException.class,
            () -> userService.register("", "password123", "Test User"));
    }

    @Test
    void testRegister_ShortPassword() {
        assertThrows(IllegalArgumentException.class,
            () -> userService.register("test@example.com", "123", "Test User"));
    }

    @Test
    void testLogin_Success() {
        User user = new User();
        user.setEmail("test@example.com");
        user.setPasswordHash("encoded-password");

        Mockito.when(userRepository.findByEmail("test@example.com")).thenReturn(user);
        Mockito.when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);

        User loggedIn = userService.login("test@example.com", "password123");
        assertEquals("test@example.com", loggedIn.getEmail());
    }

    @Test
    void testLogin_WrongPassword() {
        User user = new User();
        user.setEmail("test@example.com");
        user.setPasswordHash("encoded-password");

        Mockito.when(userRepository.findByEmail("test@example.com")).thenReturn(user);
        Mockito.when(passwordEncoder.matches(Mockito.anyString(), Mockito.anyString())).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
            () -> userService.login("test@example.com", "wrongpassword"));
    }

    @Test
    void testLogin_UserNotFound() {
        Mockito.when(userRepository.findByEmail("test@example.com")).thenReturn(null);

        assertThrows(IllegalArgumentException.class,
            () -> userService.login("test@example.com", "password123"));
    }
}
