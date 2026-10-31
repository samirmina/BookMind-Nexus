package com.bookassistant.repository;

import com.bookassistant.entity.LinkedAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Data access for linked external accounts.
 * <p>
 * <b>What:</b> Provides CRUD and lookup operations for linked_accounts.
 * <p>
 * <b>Where:</b> Implements the repository pattern for the LinkedAccount aggregate.
 */
public interface LinkedAccountRepository extends JpaRepository<LinkedAccount, Long> {
    List<LinkedAccount> findByUserId(Long userId);
    Optional<LinkedAccount> findByUserIdAndPlatform(Long userId, String platform);
    boolean existsByUserIdAndPlatform(Long userId, String platform);
    void deleteByUserIdAndPlatform(Long userId, String platform);
}


