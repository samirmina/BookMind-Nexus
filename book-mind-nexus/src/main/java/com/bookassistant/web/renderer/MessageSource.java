package com.bookassistant.web.renderer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MessageSource {
    private static final ConcurrentMap<String, ResourceBundle> BUNDLE_CACHE = new ConcurrentHashMap<>();

    private MessageSource() {}

    public static String get(String key, Locale locale) {
        return get(key, locale, new Object[0]);
    }

    public static String get(String key, Locale locale, Object... args) {
        ResourceBundle bundle = BUNDLE_CACHE.computeIfAbsent(locale.toString(), l -> {
            try {
                return ResourceBundle.getBundle("messages", locale);
            } catch (Exception e) {
                return ResourceBundle.getBundle("messages", Locale.ENGLISH);
            }
        });
        String msg;
        try {
            msg = bundle.getString(key);
        } catch (Exception e) {
            msg = key;
        }
        if (args.length > 0) {
            msg = String.format(msg, args);
        }
        return msg;
    }

    public static String getRaw(String key, Locale locale) {
        ResourceBundle bundle = BUNDLE_CACHE.computeIfAbsent(locale.toString(), l -> {
            try {
                return ResourceBundle.getBundle("messages", locale);
            } catch (Exception e) {
                return ResourceBundle.getBundle("messages", Locale.ENGLISH);
            }
        });
        try {
            return bundle.getString(key);
        } catch (Exception e) {
            return key;
        }
    }
}