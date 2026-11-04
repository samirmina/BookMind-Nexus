package com.bookassistant.controller.web;

import com.bookassistant.util.LoggingUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Locale;
import java.util.regex.Pattern;

@Controller
public class LanguageController {

    private static final Logger log = LoggerFactory.getLogger(LanguageController.class);
    public static final String SESSION_UI_LANGUAGE = "uiLanguage";
    private static final String DEFAULT_LANGUAGE = "en";
    private static final String COOKIE_NAME = "bookmind_lang";
    private static final int COOKIE_MAX_AGE = 365 * 24 * 60 * 60;
    private static final Pattern SAFE_REDIRECT_PATTERN = Pattern.compile("^/[a-zA-Z0-9_./?=&%-]*$");

    @GetMapping("/language/switch")
    public String switchLanguage(@RequestParam("lang") String lang,
                                @RequestParam(value = "redirect", required = false) String redirectUrl,
                                HttpSession session,
                                HttpServletResponse response,
                                HttpServletRequest request) {
        LoggingUtils.enter(log, "switchLanguage", "lang", lang);

        String targetLang = (lang == null || lang.isBlank()) ? DEFAULT_LANGUAGE : lang.toLowerCase();
        session.setAttribute(SESSION_UI_LANGUAGE, targetLang);
        session.setAttribute("org.springframework.web.servlet.i18n.SessionLocaleResolver.LOCALE", Locale.forLanguageTag(targetLang));

        Cookie cookie = new Cookie(COOKIE_NAME, targetLang);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(COOKIE_MAX_AGE);
        cookie.setHttpOnly(true);
        response.addCookie(cookie);

        LoggingUtils.exit(log, "switchLanguage", "newLang", targetLang);
        String fallback = "redirect:/";
        if (redirectUrl != null && !redirectUrl.isBlank()) {
            if (SAFE_REDIRECT_PATTERN.matcher(redirectUrl).matches()) {
                fallback = "redirect:" + redirectUrl;
            } else {
                log.warn("Unsafe language redirect ignored: {}", redirectUrl);
            }
        }
        return fallback;
    }

    public static String getUiLanguage(HttpServletRequest request) {
        if (request == null) return DEFAULT_LANGUAGE;
        
        HttpSession session = request.getSession(false);
        if (session != null) {
            String lang = (String) session.getAttribute(SESSION_UI_LANGUAGE);
            if (lang != null && !lang.isBlank()) {
                return lang;
            }
        }
        
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (COOKIE_NAME.equals(cookie.getName())) {
                    String cookieLang = cookie.getValue();
                    if (cookieLang != null && !cookieLang.isBlank()) {
                        return cookieLang;
                    }
                }
            }
        }
        
        return DEFAULT_LANGUAGE;
    }
}
