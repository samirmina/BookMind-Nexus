package com.bookassistant.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents a note fetched from an external linked platform.
 * <p>
 * <b>What:</b> Stores the content, title, and source URL of a note synced from
 * Notion or another linked service.
 * <p>
 * <b>Why:</b> Required to correlate external user knowledge with book content
 * for cross-platform Q&A and learning-path generation.
 * <p>
 * <b>Where:</b> Implements the external-note aggregate owned by the user.
 */
@Entity
@Table(name = "external_notes")
public class ExternalNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "linked_account_id", nullable = false)
    private Long linkedAccountId;

    @Column(name = "platform_note_id", nullable = false, length = 255)
    private String platformNoteId;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(length = 500)
    private String title;

    @Column(length = 1000)
    private String url;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        syncedAt = LocalDateTime.now();
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
    public Long getLinkedAccountId() { return linkedAccountId; }
    public void setLinkedAccountId(Long linkedAccountId) { this.linkedAccountId = linkedAccountId; }
    public String getPlatformNoteId() { return platformNoteId; }
    public void setPlatformNoteId(String platformNoteId) { this.platformNoteId = platformNoteId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public LocalDateTime getSyncedAt() { return syncedAt; }
    public void setSyncedAt(LocalDateTime syncedAt) { this.syncedAt = syncedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}


