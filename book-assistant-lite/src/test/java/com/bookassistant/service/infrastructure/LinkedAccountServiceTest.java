package com.bookassistant.service.infrastructure;

import com.bookassistant.entity.LinkedAccount;
import com.bookassistant.repository.LinkedAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

/**
 * Unit tests for LinkedAccountService.
 * Tests CRUD operations for linked accounts.
 */
class LinkedAccountServiceTest {

    private LinkedAccountRepository linkedAccountRepository;
    private LinkedAccountService linkedAccountService;

    @BeforeEach
    void setUp() {
        linkedAccountRepository = Mockito.mock(LinkedAccountRepository.class);
        linkedAccountService = new LinkedAccountService(linkedAccountRepository);
    }

    @Test
    void testLinkAccount() {
        Mockito.when(linkedAccountRepository.save(Mockito.any(LinkedAccount.class))).thenAnswer(invocation -> {
            return invocation.getArgument(0);
        });

        LinkedAccount saved = linkedAccountService.linkAccount(1L, "notion", "My Notion", "token", "refresh", LocalDateTime.now(), "{}");

        assertNotNull(saved);
        assertEquals("notion", saved.getPlatform());
        assertEquals("My Notion", saved.getAccountName());
    }

    @Test
    void testFindUserAccounts() {
        LinkedAccount account = new LinkedAccount();
        account.setUserId(1L);
        account.setPlatform("notion");

        Mockito.when(linkedAccountRepository.findByUserId(1L)).thenReturn(List.of(account));

        List<LinkedAccount> accounts = linkedAccountService.findUserAccounts(1L);
        assertEquals(1, accounts.size());
        assertEquals("notion", accounts.get(0).getPlatform());
    }

    @Test
    void testFindAccount() {
        LinkedAccount account = new LinkedAccount();
        account.setUserId(1L);
        account.setPlatform("notion");

        Mockito.when(linkedAccountRepository.findByUserIdAndPlatform(1L, "notion"))
            .thenReturn(Optional.of(account));

        Optional<LinkedAccount> found = linkedAccountService.findAccount(1L, "notion");
        assertTrue(found.isPresent());
        assertEquals("notion", found.get().getPlatform());
    }

    @Test
    void testFindAccount_NotFound() {
        Mockito.when(linkedAccountRepository.findByUserIdAndPlatform(1L, "google_keep"))
            .thenReturn(Optional.empty());

        Optional<LinkedAccount> found = linkedAccountService.findAccount(1L, "google_keep");
        assertTrue(found.isEmpty());
    }

    @Test
    void testUnlinkAccount() {
        linkedAccountService.unlinkAccount(1L, "notion");
        Mockito.verify(linkedAccountRepository).deleteByUserIdAndPlatform(1L, "notion");
    }

    @Test
    void testUpdateTokens() {
        LinkedAccount account = new LinkedAccount();
        account.setUserId(1L);
        account.setPlatform("notion");

        Mockito.when(linkedAccountRepository.findById(1L)).thenReturn(Optional.of(account));
        Mockito.when(linkedAccountRepository.save(Mockito.any(LinkedAccount.class))).thenReturn(account);

        linkedAccountService.updateTokens(1L, "new-token", "new-refresh", LocalDateTime.now().plusDays(1));

        assertEquals("new-token", account.getAccessToken());
        assertEquals("new-refresh", account.getRefreshToken());
    }
}


