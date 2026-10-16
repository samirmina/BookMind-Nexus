package com.bookassistant.entity.ui;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Expected element registry for DOM validation.
 * Defines what elements should exist on each page for each user state.
 */
@Entity
@Table(name = "ui_element_registry")
public class UiElementRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String pagePath;

    @Column(nullable = false, length = 50)
    private String userState = "anonymous";

    @Column(nullable = false, length = 50)
    private String elementType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String elementSelector;

    @Column(columnDefinition = "TEXT")
    private String expectedText;

    @Column(columnDefinition = "TEXT")
    private String expectedHref;

    @Column
    private Boolean expectedVisible = true;

    @Column
    private Integer priority = 0;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getPagePath() { return pagePath; }
    public void setPagePath(String pagePath) { this.pagePath = pagePath; }
    public String getUserState() { return userState; }
    public void setUserState(String userState) { this.userState = userState; }
    public String getElementType() { return elementType; }
    public void setElementType(String elementType) { this.elementType = elementType; }
    public String getElementSelector() { return elementSelector; }
    public void setElementSelector(String elementSelector) { this.elementSelector = elementSelector; }
    public String getExpectedText() { return expectedText; }
    public void setExpectedText(String expectedText) { this.expectedText = expectedText; }
    public String getExpectedHref() { return expectedHref; }
    public void setExpectedHref(String expectedHref) { this.expectedHref = expectedHref; }
    public Boolean getExpectedVisible() { return expectedVisible; }
    public void setExpectedVisible(Boolean expectedVisible) { this.expectedVisible = expectedVisible; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
