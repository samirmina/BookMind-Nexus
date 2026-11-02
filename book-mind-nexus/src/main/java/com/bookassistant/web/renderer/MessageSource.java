package com.bookassistant.web.renderer;

import java.util.Locale;
import java.util.ResourceBundle;

public final class MessageSource {
    private MessageSource() {}

    public static String get(String key, Locale locale) {
        return get(key, locale, new Object[0]);
    }

    public static String get(String key, Locale locale, Object... args) {
        ResourceBundle bundle = null;
        try {
            bundle = ResourceBundle.getBundle("messages", locale);
        } catch (Exception e) {
            try {
                bundle = ResourceBundle.getBundle("messages", Locale.ENGLISH);
            } catch (Exception e2) {
                bundle = null;
            }
        }
        String msg;
        if (bundle != null) {
            try {
                msg = bundle.getString(key);
            } catch (Exception e) {
                msg = key;
            }
        } else {
            msg = key;
        }
        if (args.length > 0) {
            msg = String.format(msg, args);
        }
        return msg;
    }

    public static String getRaw(String key, Locale locale) {
        ResourceBundle bundle = null;
        try {
            bundle = ResourceBundle.getBundle("messages", locale);
        } catch (Exception e) {
            try {
                bundle = ResourceBundle.getBundle("messages", Locale.ENGLISH);
            } catch (Exception e2) {
                bundle = null;
            }
        }
        if (bundle != null) {
            try {
                return bundle.getString(key);
            } catch (Exception e) {
                return key;
            }
        }
        return key;
    }
}