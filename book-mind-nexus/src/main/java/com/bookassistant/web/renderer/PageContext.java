package com.bookassistant.web.renderer;

import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

public final class PageContext {
    private final String lang;
    private final Locale locale;
    private final String dir;
    private final Long userId;
    private final String userEmail;
    private final boolean isLoggedIn;
    private final boolean hasLinkedAccount;
    private final int externalNotesCount;
    private final String cacheBookName;
    private final String bookLanguage;
    private final String uiLanguage;
    private final String extractionMode;
    private final Map<String, Object> attributes = new HashMap<>();

    private PageContext(Builder b) {
        this.lang = b.lang;
        this.locale = b.locale;
        this.dir = b.dir;
        this.userId = b.userId;
        this.userEmail = b.userEmail;
        this.isLoggedIn = b.isLoggedIn;
        this.hasLinkedAccount = b.hasLinkedAccount;
        this.externalNotesCount = b.externalNotesCount;
        this.cacheBookName = b.cacheBookName;
        this.bookLanguage = b.bookLanguage;
        this.uiLanguage = b.uiLanguage;
        this.extractionMode = b.extractionMode;
        this.attributes.putAll(b.attributes);
    }

    public String getMessage(String key) {
        return MessageSource.get(key, locale);
    }

    public String getMessage(String key, Object... args) {
        return MessageSource.get(key, locale, args);
    }

    public String lang() { return lang; }
    public Locale locale() { return locale; }
    public String dir() { return dir; }
    public Long userId() { return userId; }
    public String userEmail() { return userEmail; }
    public boolean isLoggedIn() { return isLoggedIn; }
    public boolean hasLinkedAccount() { return hasLinkedAccount; }
    public int externalNotesCount() { return externalNotesCount; }
    public String cacheBookName() { return cacheBookName; }
    public String bookLanguage() { return bookLanguage; }
    public String uiLanguage() { return uiLanguage; }
    public String extractionMode() { return extractionMode; }
    public Object attr(String key) { return attributes.get(key); }
    public PageContext attr(String key, Object value) {
        attributes.put(key, value);
        return this;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String lang = "en";
        private Locale locale = Locale.ENGLISH;
        private String dir = "ltr";
        private Long userId;
        private String userEmail;
        private boolean isLoggedIn;
        private boolean hasLinkedAccount;
        private int externalNotesCount;
        private String cacheBookName;
        private String bookLanguage = "en";
        private String uiLanguage = "en";
        private String extractionMode = "model-only";
        private final Map<String, Object> attributes = new HashMap<>();

        public Builder lang(String lang) { this.lang = lang; return this; }
        public Builder locale(Locale locale) { this.locale = locale; return this; }
        public Builder dir(String dir) { this.dir = dir; return this; }
        public Builder userId(Long userId) { this.userId = userId; return this; }
        public Builder userEmail(String email) { this.userEmail = email; return this; }
        public Builder isLoggedIn(boolean v) { this.isLoggedIn = v; return this; }
        public Builder hasLinkedAccount(boolean v) { this.hasLinkedAccount = v; return this; }
        public Builder externalNotesCount(int v) { this.externalNotesCount = v; return this; }
        public Builder cacheBookName(String v) { this.cacheBookName = v; return this; }
        public Builder bookLanguage(String v) { this.bookLanguage = v; return this; }
        public Builder uiLanguage(String v) { this.uiLanguage = v; return this; }
        public Builder extractionMode(String v) { this.extractionMode = v; return this; }
        public Builder attr(String key, Object value) { this.attributes.put(key, value); return this; }
        public PageContext build() { return new PageContext(this); }
    }
}