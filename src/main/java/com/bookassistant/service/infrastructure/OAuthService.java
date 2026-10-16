package com.bookassistant.service.infrastructure;

import com.bookassistant.entity.LinkedAccount;
import com.bookassistant.repository.LinkedAccountRepository;
import com.bookassistant.util.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

/**
 * Handles OAuth 2.0 flows for external account linking.
 * <p>
 * <b>What:</b> Implements the authorization code flow for Google and Notion,
 * exchanging authorization codes for access tokens and storing them securely.
 * <p>
 * <b>Why:</b> Real OAuth integration is required per PROJECT_RULES.md - stubs
 * that pretend to link accounts without real credentials are not acceptable.
 * <p>
 * <b>Where:</b> Called by OAuthController during the callback phase.
 */
@Service
public class OAuthService {

    private static final Logger log = LoggerFactory.getLogger(OAuthService.class);
    private static final String NOTION_TOKEN_URL = "https://api.notion.com/v1/oauth/token";

    private final LinkedAccountRepository linkedAccountRepository;
    private final RestTemplate restTemplate;

    @Value("${app.oauth.notion.client-id:}")
    private String notionClientId;

    @Value("${app.oauth.notion.client-secret:}")
    private String notionClientSecret;

    @Value("${app.oauth.notion.redirect-uri:}")
    private String notionRedirectUri;

    public OAuthService(LinkedAccountRepository linkedAccountRepository) {
        this.linkedAccountRepository = linkedAccountRepository;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Exchanges an authorization code for an access token.
     *
     * @param platform     "google" or "notion"
     * @param authCode     the authorization code from the callback
     * @param userId       the current user ID
     * @return the linked account with tokens stored
     */
    @Transactional
    public LinkedAccount exchangeCodeForToken(String platform, String authCode, Long userId) {
        LoggingUtils.enter(log, "exchangeCodeForToken", "platform", platform);

        String tokenUrl;
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();

        if ("notion".equals(platform)) {
            if (notionClientId.isEmpty() || notionClientSecret.isEmpty()) {
                throw new IllegalStateException("Notion OAuth not configured. Set NOTION_OAUTH_CLIENT_ID and NOTION_OAUTH_CLIENT_SECRET.");
            }
            tokenUrl = NOTION_TOKEN_URL;
            params.add("grant_type", "authorization_code");
            params.add("code", authCode);
            params.add("redirect_uri", notionRedirectUri);
        } else {
            throw new IllegalArgumentException("Unsupported platform: " + platform);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        String basicAuth = notionClientId + ":" + notionClientSecret;
        String encodedAuth = Base64.getEncoder().encodeToString(basicAuth.getBytes());
        headers.set("Authorization", "Basic " + encodedAuth);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        log.info("Exchanging code for token | platform={} | tokenUrl={} | redirectUri={}", platform, tokenUrl, notionRedirectUri);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(tokenUrl, request, Map.class);

            if (response == null || !response.containsKey("access_token")) {
                String error = response != null ? (String) response.get("error") : "null response";
                throw new RuntimeException("Failed to obtain access token from " + platform + ": " + error);
            }

            String accessToken = (String) response.get("access_token");
            String refreshToken = (String) response.get("refresh_token");
            Integer expiresIn = (Integer) response.get("expires_in");

            LocalDateTime tokenExpiry = expiresIn != null
                    ? LocalDateTime.now().plusSeconds(expiresIn)
                    : LocalDateTime.now().plusDays(30);

            LinkedAccount account = linkedAccountRepository.findByUserIdAndPlatform(userId, platform)
                    .orElseGet(() -> {
                        LinkedAccount newAccount = new LinkedAccount();
                        newAccount.setUserId(userId);
                        newAccount.setPlatform(platform);
                        return newAccount;
                    });

            account.setAccessToken(accessToken);
            account.setRefreshToken(refreshToken);
            account.setTokenExpiry(tokenExpiry);

            LinkedAccount saved = linkedAccountRepository.save(account);
            LoggingUtils.exit(log, "exchangeCodeForToken", "accountId", saved.getId());
            log.info("Token exchange successful | platform={} | userId={} | accountId={}", platform, userId, saved.getId());
            return saved;

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Token exchange failed | platform={} | userId={} | status={} | body={}", 
                platform, userId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Token exchange failed: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Token exchange failed | platform={} | userId={} | error={}", platform, userId, e.getMessage(), e);
            throw new RuntimeException("Token exchange failed: " + e.getMessage(), e);
        }
    }

    /**
     * Builds the authorization URL for the given platform.
     *
     * @param platform "google" or "notion"
     * @param state     CSRF protection token
     * @return the authorization URL
     */
    public String buildAuthorizationUrl(String platform, String state) {
        if ("notion".equals(platform)) {
            if (notionClientId == null || notionClientId.isBlank()) {
                throw new IllegalStateException("Notion OAuth not configured. Set NOTION_OAUTH_CLIENT_ID.");
            }
            return "https://api.notion.com/v1/oauth/authorize" +
                    "?client_id=" + notionClientId +
                    "&redirect_uri=" + notionRedirectUri +
                    "&response_type=code" +
                    "&state=" + state;
        }
        throw new IllegalArgumentException("Unsupported platform: " + platform);
    }
}
