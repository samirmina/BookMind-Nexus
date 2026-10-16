package com.bookassistant.repository;

import com.bookassistant.entity.BookChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Data access for book chunks used in RAG retrieval.
 *
 * <p><b>Where:</b> Implements the repository pattern for RAG chunks.</p>
 */
public interface BookChunkRepository extends JpaRepository<BookChunk, Long> {

    List<BookChunk> findByBookId(Long bookId);

    List<BookChunk> findByUserId(Long userId);

    List<BookChunk> findByBookIdAndUserId(Long bookId, Long userId);

    void deleteByBookId(Long bookId);

    @Query(value = """
        SELECT * FROM book_chunks
        WHERE book_id = :bookId
        ORDER BY embedding <=> cast(:embedding as vector(768))
        LIMIT :limit
        """, nativeQuery = true)
    List<BookChunk> findTopKByBookIdOrderByCosineSimilarity(
            @Param("bookId") Long bookId,
            @Param("embedding") String embedding,
            @Param("limit") int limit);

    @Query(value = """
        SELECT * FROM book_chunks
        WHERE user_id = :userId
        ORDER BY embedding <=> cast(:embedding as vector(768))
        LIMIT :limit
        """, nativeQuery = true)
    List<BookChunk> findTopKByUserIdOrderByCosineSimilarity(
            @Param("userId") Long userId,
            @Param("embedding") String embedding,
            @Param("limit") int limit);
}
