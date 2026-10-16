package com.bookassistant.controller.web;

import com.bookassistant.service.infrastructure.OAuthService;
import com.bookassistant.service.integration.SyncService;
import com.bookassistant.util.ErrorMessageSanitizer;
import com.bookassistant.util.LoggingUtils;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Handles OAuth 2.0 authorization flows for external account linking.
 * <p>
 * <b>What:</b> Initiates OAuth flows for Google and Notion, handles callbacks,
 * and exchanges authorization codes for access tokens.
 * <p>
 * <b>Why:</b> Real OAuth integration replaces the previous stub implementation
 * that created database entries without real credentials.
 * <p>
 * <b>Where:</b> Called from the accounts page when user clicks "Connect".
 */
@Controller
public class OAuthController {

    private static final Logger log = LoggerFactory.getLogger(OAuthController.class);
    private static final String STATE_ATTRIBUTE = "oauthState";
    private static final String REDIRECT_ATTRIBUTE = "oauthRedirectUrl";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OAuthService oauthService;
    private final SyncService syncService;
    private final MessageSource messageSource;

    public OAuthController(OAuthService oAuthService, SyncService syncService, MessageSource messageSource) {
        this.oauthService = oAuthService;
        this.syncService = syncService;
        this.messageSource = messageSource;
        log.info("OAuthController initialized");
    }

    /**
     * Initiates OAuth flow by redirecting user to the provider's authorization page.
     *
     * @param platform   "google" or "notion"
     * @param session    the HTTP session
     * @return redirect to provider's authorization URL
     */
    @GetMapping("/oauth2/authorize")
    public String authorize(@RequestParam("platform") String platform, HttpSession session, Model model) {
        LoggingUtils.enter(log, "authorize", "platform", platform);

        String state = generateState();
        session.setAttribute(STATE_ATTRIBUTE, state);

        try {
            String authUrl = oauthService.buildAuthorizationUrl(platform, state);
            LoggingUtils.exit(log, "authorize", "authUrl", authUrl);
            return "redirect:" + authUrl;
        } catch (IllegalStateException e) {
            log.warn("OAuth not configured: {}", e.getMessage());
            String redirectUrl = (String) session.getAttribute(REDIRECT_ATTRIBUTE);
            if (redirectUrl == null) redirectUrl = "/accounts";
            model.addAttribute("error", ErrorMessageSanitizer.sanitize(e.getMessage()));
            return "redirect:" + redirectUrl;
        }
    }

    /**
     * Handles OAuth callback from the provider.
     *
     * @param platform  the OAuth provider
     * @param code      the authorization code
     * @param state     the CSRF state token
     * @param session   the HTTP session
     * @param model     the view model
     * @return redirect to accounts page with success/error message
     */
    @GetMapping("/oauth2/callback/{platform}")
    public String callback(@PathVariable("platform") String platform,
                          @RequestParam("code") String code,
                          @RequestParam(value = "state", required = false) String state,
                          @RequestParam(value = "error", required = false) String error,
                          HttpSession session,
                          Model model) {
        LoggingUtils.enter(log, "callback", "platform", platform);

        String redirectUrl = (String) session.getAttribute(REDIRECT_ATTRIBUTE);
        if (redirectUrl == null) redirectUrl = "/accounts";

        if (error != null) {
            log.warn("OAuth error from provider: {}", error);
            model.addAttribute("error", messageSource.getMessage("oauth.authorization.failed", new Object[]{error}, LocaleContextHolder.getLocale()));
            return "redirect:" + redirectUrl;
        }

        String expectedState = (String) session.getAttribute(STATE_ATTRIBUTE);
        if (expectedState == null || !expectedState.equals(state)) {
            log.warn("Invalid OAuth state: expected={}, actual={}", expectedState, state);
            model.addAttribute("error", messageSource.getMessage("oauth.invalid.state", null, LocaleContextHolder.getLocale()));
            return "redirect:" + redirectUrl;
        }

        try {
            Long userId = (Long) session.getAttribute("userId");
            if (userId == null) {
                model.addAttribute("error", messageSource.getMessage("oauth.login.required", null, LocaleContextHolder.getLocale()));
                return "redirect:/login";
            }

            oauthService.exchangeCodeForToken(platform, code, userId);

            try {
                int synced = syncService.syncUserNotes(userId);
                log.info("Auto-synced {} notes after linking {} for user {}", synced, platform, userId);
            } catch (Exception syncEx) {
                log.warn("Auto-sync failed after linking {} for user {} | error={}", platform, userId, syncEx.getMessage());
            }

            model.addAttribute("success", messageSource.getMessage("oauth.link.success", null, LocaleContextHolder.getLocale()));
            LoggingUtils.exit(log, "callback", "status", "success");
        } catch (Exception e) {
            log.error("OAuth callback failed", e);
            String sanitized = ErrorMessageSanitizer.sanitize(e.getMessage());
            model.addAttribute("error", messageSource.getMessage("oauth.link.error", new Object[]{sanitized}, LocaleContextHolder.getLocale()));
        }

        return "redirect:" + redirectUrl;
    }

    /**
     * Generates a random state token for CSRF protection.
     */
    private String generateState() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
