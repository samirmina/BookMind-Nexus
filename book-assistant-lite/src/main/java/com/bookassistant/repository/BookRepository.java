package com.bookassistant.repository;

import com.bookassistant.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Data access for uploaded books.
 * <p>
 * <b>What:</b> Provides CRUD operations for books, scoped by user.
 * <p>
 * <b>Where:</b> Implements the repository pattern for the Book aggregate.
 */
public interface BookRepository extends JpaRepository<Book, Long> {
    List<Book> findByUserId(Long userId);
    List<Book> findByUserIdAndBookFolder(Long userId, String bookFolder);
    List<Book> findByUserIdAndTitle(Long userId, String title);
    @Query(value = "SELECT * FROM books WHERE user_id = :userId AND :tag = ANY(tags)", nativeQuery = true)
    List<Book> findByTagsContainingAndUserId(@Param("tag") String tag, @Param("userId") Long userId);
}



