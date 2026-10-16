package com.bookassistant.config;

import com.bookassistant.controller.web.LanguageController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Locale;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String cacheLocation;

    public WebConfig(@Value("${app.cache-dir:cache}") String cacheLocation) {
        this.cacheLocation = "file:" + cacheLocation + "/";
    }

    @Bean
    public LocaleResolver localeResolver() {
        return new LocaleResolver() {
            @Override
            public Locale resolveLocale(HttpServletRequest request) {
                String lang = LanguageController.getUiLanguage(request);
                return Locale.forLanguageTag(lang);
            }

            @Override
            public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
                request.getSession().setAttribute("uiLanguage", locale.toLanguageTag());
            }
        };
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/cache/**")
                .addResourceLocations(cacheLocation);
    }
}
