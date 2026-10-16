package com.bookassistant.service.infrastructure;

import com.bookassistant.entity.LinkedAccount;
import com.bookassistant.repository.LinkedAccountRepository;
import com.bookassistant.util.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Manages external platform account linking for users.
 * <p>
 * <b>What:</b> Provides CRUD operations for linked accounts (Notion, etc.)
 * including token storage and metadata management.
 * <p>
 * <b>Why:</b> Required for the multi-platform sync feature; enables the app to
 * authenticate against external services on behalf of the user.
 * <p>
 * <b>Where:</b> Implements the linked-account aggregate service per {@code PROJECT_RULES.md} Section 1.
 */
@Service
public class LinkedAccountService {

    private static final Logger log = LoggerFactory.getLogger(LinkedAccountService.class);

    private final LinkedAccountRepository linkedAccountRepository;

    /**
     * Creates the service with required repository.
     *
     * @param linkedAccountRepository the linked-account data access layer
     */
    public LinkedAccountService(LinkedAccountRepository linkedAccountRepository) {
        this.linkedAccountRepository = linkedAccountRepository;
    }

    /**
     * Links a new external platform account to a user.
     *
     * @param userId       the user ID
     * @param platform     the platform name (e.g., "notion", "google_keep")
     * @param accountName  optional display name
     * @param accessToken  the OAuth access token
     * @param refreshToken the OAuth refresh token
     * @param tokenExpiry  optional token expiry timestamp
     * @param metadata     optional JSON metadata
     * @return the persisted {@link LinkedAccount}
     */
    public LinkedAccount linkAccount(Long userId, String platform, String accountName,
                                     String accessToken, String refreshToken,
                                     java.time.LocalDateTime tokenExpiry, String metadata) {
        LoggingUtils.enter(log, "linkAccount", "userId", userId, "platform", platform);

        Optional<LinkedAccount> existing = linkedAccountRepository.findByUserIdAndPlatform(userId, platform);
        if (existing.isPresent()) {
            LinkedAccount account = existing.get();
            account.setAccountName(accountName);
            account.setAccessToken(accessToken);
            account.setRefreshToken(refreshToken);
            account.setTokenExpiry(tokenExpiry);
            account.setMetadata(metadata);
            LinkedAccount saved = linkedAccountRepository.save(account);
            log.info("Account updated | userId={} | platform={} | accountId={}", userId, platform, saved.getId());
            return saved;
        }

        LinkedAccount account = new LinkedAccount();
        account.setUserId(userId);
        account.setPlatform(platform);
        account.setAccountName(accountName);
        account.setAccessToken(accessToken);
        account.setRefreshToken(refreshToken);
        account.setTokenExpiry(tokenExpiry);
        account.setMetadata(metadata);

        LinkedAccount saved = linkedAccountRepository.save(account);
        log.info("Account linked | userId={} | platform={} | accountId={}", userId, platform, saved.getId());
        return saved;
    }

    /**
     * Finds all linked accounts for a user.
     *
     * @param userId the user ID
     * @return list of linked accounts
     */
    public List<LinkedAccount> findUserAccounts(Long userId) {
        return linkedAccountRepository.findByUserId(userId);
    }

    /**
     * Finds a specific linked account by user and platform.
     *
     * @param userId  the user ID
     * @param platform the platform name
     * @return the linked account if found
     */
    public Optional<LinkedAccount> findAccount(Long userId, String platform) {
        return linkedAccountRepository.findByUserIdAndPlatform(userId, platform);
    }

    /**
     * Unlinks a platform account from a user.
     *
     * @param userId  the user ID
     * @param platform the platform name
     */
    public void unlinkAccount(Long userId, String platform) {
        LoggingUtils.enter(log, "unlinkAccount", "userId", userId, "platform", platform);
        linkedAccountRepository.deleteByUserIdAndPlatform(userId, platform);
        log.info("Account unlinked | userId={} | platform={}", userId, platform);
    }

    /**
     * Updates tokens for an existing linked account.
     *
     * @param accountId    the linked account ID
     * @param accessToken  the new access token
     * @param refreshToken the new refresh token
     * @param tokenExpiry  the new expiry timestamp
     */
    public void updateTokens(Long accountId, String accessToken, String refreshToken, java.time.LocalDateTime tokenExpiry) {
        LoggingUtils.enter(log, "updateTokens", "accountId", accountId);
        LinkedAccount account = linkedAccountRepository.findById(accountId).orElseThrow();
        account.setAccessToken(accessToken);
        account.setRefreshToken(refreshToken);
        account.setTokenExpiry(tokenExpiry);
        linkedAccountRepository.save(account);
        log.info("Tokens updated | accountId={}", accountId);
    }
}


