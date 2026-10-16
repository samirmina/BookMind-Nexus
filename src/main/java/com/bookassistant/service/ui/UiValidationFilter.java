package com.bookassistant.service.ui;

import com.bookassistant.entity.ui.UiSnapshot;
import com.bookassistant.repository.ui.UiSnapshotRepository;
import com.bookassistant.util.LoggingUtils;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Captures rendered HTML responses for UI validation.
 */
@Service
public class UiValidationFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(UiValidationFilter.class);
    private static final Set<String> EXCLUDED_EXTENSIONS = new HashSet<>();

    private final UiSnapshotRepository snapshotRepository;
    private final HtmlDiffService htmlDiffService;
    private final ScreenshotService screenshotService;

    static {
        EXCLUDED_EXTENSIONS.add(".css");
        EXCLUDED_EXTENSIONS.add(".js");
        EXCLUDED_EXTENSIONS.add(".png");
        EXCLUDED_EXTENSIONS.add(".jpg");
        EXCLUDED_EXTENSIONS.add(".svg");
        EXCLUDED_EXTENSIONS.add(".ico");
        EXCLUDED_EXTENSIONS.add(".woff");
        EXCLUDED_EXTENSIONS.add(".woff2");
    }

    public UiValidationFilter(UiSnapshotRepository snapshotRepository,
                              HtmlDiffService htmlDiffService,
                              ScreenshotService screenshotService) {
        this.snapshotRepository = snapshotRepository;
        this.htmlDiffService = htmlDiffService;
        this.screenshotService = screenshotService;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("UiValidationFilter initialized");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();
        if (isExcluded(path)) {
            chain.doFilter(request, response);
            return;
        }

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(httpResponse);
        chain.doFilter(request, wrappedResponse);

        byte[] content = wrappedResponse.getContentAsByteArray();
        String html = new String(content, httpResponse.getCharacterEncoding() != null ? httpResponse.getCharacterEncoding() : "UTF-8");

        if (!html.isEmpty() && html.contains("<html")) {
            String sessionId = httpRequest.getSession().getId();
            String userState = httpRequest.getUserPrincipal() != null ? "logged_in" : "anonymous";

            UiSnapshot snapshot = new UiSnapshot();
            snapshot.setPagePath(path);
            snapshot.setSessionId(sessionId);
            snapshot.setHtmlContent(html);
            snapshot.setSnapshotType("current");
            snapshot.setUserAgent(httpRequest.getHeader("User-Agent"));
            snapshotRepository.save(snapshot);

            htmlDiffService.analyzeSnapshot(snapshot, userState);

            try {
                screenshotService.captureAndCompare(snapshot, path, userState);
            } catch (Exception e) {
                log.warn("Screenshot capture failed for {}: {}", path, e.getMessage());
            }
        }

        wrappedResponse.copyBodyToResponse();
    }

    @Override
    public void destroy() {
        log.info("UiValidationFilter destroyed");
    }

    private boolean isExcluded(String path) {
        if (path.startsWith("/ui/")) return true;
        if (path.startsWith("/upload/progress/")) return true;
        for (String ext : EXCLUDED_EXTENSIONS) {
            if (path.toLowerCase().endsWith(ext)) return true;
        }
        return false;
    }
}
