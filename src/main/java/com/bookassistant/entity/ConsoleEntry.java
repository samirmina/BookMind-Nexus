package com.bookassistant.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Console output entry captured from System.out/System.err.
 * <p>
 * <b>What:</b> Stores a single line of console output with its parsed log level,
 * source component, and timestamp.
 * <p>
 * <b>Why:</b> Provides an audit trail of application console output in the database
 * for debugging and project history.
 */
@Entity
@Table(name = "console_entries")
public class ConsoleEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "level", length = 20)
    private String level;

    @Column(name = "source", length = 100)
    private String source;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "occurred_at")
    private LocalDateTime occurredAt;

    public ConsoleEntry() {}

    public ConsoleEntry(String level, String source, String message) {
        this.level = level;
        this.source = source;
        this.message = message;
        this.occurredAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
}
