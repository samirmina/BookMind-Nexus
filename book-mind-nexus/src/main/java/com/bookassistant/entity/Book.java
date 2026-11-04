package com.bookassistant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Represents an uploaded book belonging to a user.
 * <p>
 * <b>What:</b> Stores file metadata and the full extracted text for AI processing.
 * <p>
 * <b>Why:</b> Required to persist books across sessions and associate reading progress
 * and external notes with a specific book.
 * <p>
 * <b>Where:</b> Implements the book aggregate in the user's library.
 */
@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 1000)
    private String filePath;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(columnDefinition = "TEXT")
    private String fullText;

    @Column(length = 10)
    private String language = "en";

    @Column(name = "book_folder", length = 1000, nullable = false, columnDefinition = "VARCHAR(1000) DEFAULT ''")
    private String bookFolder = "";

    @Column(name = "tags", columnDefinition = "TEXT[] DEFAULT '{}'")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> tags = new ArrayList<>();

    @Column(name = "indexing_status", length = 20, nullable = false, columnDefinition = "VARCHAR(20) DEFAULT 'PENDING'")
    private String indexingStatus = "PENDING";

    @Column(name = "indexing_error", columnDefinition = "TEXT")
    private String indexingError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public Integer getPageCount() { return pageCount; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }
    public String getFullText() { return fullText; }
    public void setFullText(String fullText) { this.fullText = fullText; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getBookFolder() { return bookFolder; }
    public void setBookFolder(String bookFolder) { this.bookFolder = bookFolder; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public String getIndexingStatus() { return indexingStatus; }
    public void setIndexingStatus(String indexingStatus) { this.indexingStatus = indexingStatus; }
    public String getIndexingError() { return indexingError; }
    public void setIndexingError(String indexingError) { this.indexingError = indexingError; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}


