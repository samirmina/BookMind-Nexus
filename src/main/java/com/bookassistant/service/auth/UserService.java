package com.bookassistant.service.auth;

import com.bookassistant.entity.User;
import com.bookassistant.repository.UserRepository;
import com.bookassistant.util.LoggingUtils;
import com.bookassistant.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Handles user registration, authentication, and password encoding.
 * <p>
 * <b>What:</b> Provides operations to register new users, verify credentials,
 * and encode passwords for secure storage.
 * <p>
 * <b>Why:</b> Required for the account system mandated by the project's linked-accounts
 * and book-ownership requirements. Centralizes authentication logic outside controllers.
 * <p>
 * <b>Where:</b> Implements the user aggregate's authentication service.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    /**
     * Creates the service with the required repository.
     *
     * @param userRepository the user data access layer
     * @param passwordEncoder the Spring Security password encoder
     */
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        log.info("UserService initialized");
    }

    /**
     * Registers a new user with email and password.
     * <p>
     * <b>Where:</b> Required for account creation flow.
     *
     * @param email    the user's email (must be unique)
     * @param password the raw password (will be encoded)
     * @param name     optional display name
     * @return the persisted {@link User}
     * @throws IllegalArgumentException if email is already registered
     */
    public User register(String email, String password, String name) {
        LoggingUtils.enter(log, "register", "email", email);

        if (StringUtils.isEmpty(email)) {
            throw new IllegalArgumentException("Email is required");
        }
        if (StringUtils.isEmpty(password) || password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email is already registered");
        }

        User user = new User();
        user.setEmail(email.toLowerCase().trim());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setName(name != null ? name.trim() : null);

        User saved = userRepository.save(user);
        log.info("User registered | userId={} | email={}", saved.getId(), saved.getEmail());
        return saved;
    }

    /**
     * Authenticates a user by email and raw password.
     * <p>
     * <b>Where:</b> Required for login flow.
     *
     * @param email    the user's email
     * @param password the raw password to verify
     * @return the {@link User} if credentials match
     * @throws IllegalArgumentException if credentials are invalid
     */
    public User login(String email, String password) {
        LoggingUtils.enter(log, "login", "email", email);

        if (StringUtils.isEmpty(email) || StringUtils.isEmpty(password)) {
            throw new IllegalArgumentException("Email and password are required");
        }

        User user = userRepository.findByEmail(email.toLowerCase().trim());
        if (user == null) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        log.info("User logged in | userId={} | email={}", user.getId(), user.getEmail());
        return user;
    }

    /**
     * Encodes a raw password using BCrypt.
     * <p>
     * <b>Where:</b> Exposed for use in tests and batch operations.
     *
     * @param rawPassword the plaintext password
     * @return the BCrypt-encoded hash
     */
    public String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }
}


