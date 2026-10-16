package com.bookassistant.repository;

import com.bookassistant.entity.BookOutput;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Data access for {@link BookOutput} entries.
 * <p>
 * <b>What:</b> Provides CRUD and query methods for AI output logs.
 * <p>
 * <b>Why:</b> Required to persist and retrieve tab-specific AI outputs per book.
 * <p>
 * <b>Where:</b> Implements the repository layer for the book-output aggregate.
 */
@Repository
public interface BookOutputRepository extends JpaRepository<BookOutput, Long> {
    List<BookOutput> findByUserId(Long userId);
    List<BookOutput> findByBookId(Long bookId);
    List<BookOutput> findByUserIdAndTabUsed(Long userId, String tabUsed);
    List<BookOutput> findByBookIdAndTabUsed(Long bookId, String tabUsed);

    @Query("SELECT b FROM BookOutput b WHERE b.userId = :userId ORDER BY b.createdAt DESC")
    List<BookOutput> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    @Query("SELECT b FROM BookOutput b WHERE b.userId = :userId AND b.tabUsed = :tabUsed ORDER BY b.createdAt DESC")
    List<BookOutput> findByUserIdAndTabUsedOrderByCreatedAtDesc(@Param("userId") Long userId, @Param("tabUsed") String tabUsed);
}
