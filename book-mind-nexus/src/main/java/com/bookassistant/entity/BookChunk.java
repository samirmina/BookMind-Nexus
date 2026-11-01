package com.bookassistant.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents a chunk of book text with its vector embedding for RAG retrieval.
 *
 * <p><b>What:</b> Stores a piece of book text along with its embedding vector
 * for semantic search.</p>
 *
 * <p><b>Why:</b> Required for the RAG pipeline to retrieve relevant passages
 * based on user queries.</p>
 *
 * <p><b>Where:</b> Part of the RAG system used by {@link com.bookassistant.service.rag.RagService}.</p>
 */
@Entity
@Table(name = "book_chunks")
public class BookChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "chunk_text", columnDefinition = "TEXT", nullable = false)
    private String chunkText;

    @Column(name = "embedding", columnDefinition = "vector(768)")
    private String embedding;

    @Column(name = "page_start")
    private Integer pageStart;

    @Column(name = "page_end")
    private Integer pageEnd;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getChunkText() { return chunkText; }
    public void setChunkText(String chunkText) { this.chunkText = chunkText; }
    public String getEmbedding() { return embedding; }
    public void setEmbedding(String embedding) { this.embedding = embedding; }
    public Integer getPageStart() { return pageStart; }
    public void setPageStart(Integer pageStart) { this.pageStart = pageStart; }
    public Integer getPageEnd() { return pageEnd; }
    public void setPageEnd(Integer pageEnd) { this.pageEnd = pageEnd; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
