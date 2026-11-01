package com.bookassistant.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents an AI-generated output entry for a specific book and tab.
 * <p>
 * <b>What:</b> Logs the tab used, optional linked account context, and the
 * generated output text for auditing and history.
 * <p>
 * <b>Why:</b> Required by the database organization rules to track which
 * feature produced each AI output and whether external account data was used.
 * <p>
 * <b>Where:</b> Implements the book-output aggregate in the user's library.
 */
@Entity
@Table(name = "book_outputs")
public class BookOutput {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "tab_used", nullable = false, length = 50)
    private String tabUsed;

    @Column(name = "linked_account_id")
    private Long linkedAccountId;

    @Column(name = "linked_platform", length = 50)
    private String linkedPlatform;

    @Column(name = "output_text", nullable = false, columnDefinition = "TEXT")
    private String outputText;

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
    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTabUsed() { return tabUsed; }
    public void setTabUsed(String tabUsed) { this.tabUsed = tabUsed; }
    public Long getLinkedAccountId() { return linkedAccountId; }
    public void setLinkedAccountId(Long linkedAccountId) { this.linkedAccountId = linkedAccountId; }
    public String getLinkedPlatform() { return linkedPlatform; }
    public void setLinkedPlatform(String linkedPlatform) { this.linkedPlatform = linkedPlatform; }
    public String getOutputText() { return outputText; }
    public void setOutputText(String outputText) { this.outputText = outputText; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
