package com.bookassistant.repository;

import com.bookassistant.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for users.
 * <p>
 * <b>What:</b> Provides CRUD and lookup operations for the users table.
 * <p>
 * <b>Why:</b> Required for authentication and ownership enforcement across the application.
 * <p>
 * <b>Where:</b> Implements the repository pattern for the User aggregate.
 */
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);
    User findByEmail(String email);
}


