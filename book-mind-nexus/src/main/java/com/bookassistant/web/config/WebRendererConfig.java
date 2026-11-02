package com.bookassistant.web.config;

import com.bookassistant.web.renderer.JavaHtmlViewResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.ViewResolver;

/**
 * Configuration for pure Java HTML rendering.
 * Registers {@link JavaHtmlViewResolver} as the sole ViewResolver,
 * replacing Thymeleaf entirely.
 */
@Configuration
public class WebRendererConfig {

    @Bean
    public ViewResolver javaHtmlViewResolver() {
        return new JavaHtmlViewResolver();
    }
}