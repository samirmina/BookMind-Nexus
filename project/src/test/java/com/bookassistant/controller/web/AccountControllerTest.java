package com.bookassistant.controller.web;

import com.bookassistant.entity.LinkedAccount;
import com.bookassistant.service.infrastructure.ExternalNoteService;
import com.bookassistant.service.infrastructure.LinkedAccountService;
import com.bookassistant.service.integration.SyncService;
import com.bookassistant.service.telemetry.TelemetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Mock
    private LinkedAccountService linkedAccountService;

    @Mock
    private TelemetryService telemetryService;

    @Mock
    private SyncService syncService;

    @Mock
    private ExternalNoteService externalNoteService;

    @Mock
    private MessageSource messageSource;

    @Mock
    private Model model;

    @Mock
    private HttpSession session;

    @Mock
    private RedirectAttributes redirectAttributes;

    @InjectMocks
    private AccountController accountController;

    @BeforeEach
    void setUp() {
        lenient().when(messageSource.getMessage(anyString(), isNull(), any())).thenReturn("default");
        lenient().when(session.getAttribute("userId")).thenReturn(1L);
    }

    @Test
    void testShowAccountsPage() {
        when(session.getAttribute("userId")).thenReturn(1L);
        when(linkedAccountService.findUserAccounts(1L)).thenReturn(Collections.emptyList());
        when(externalNoteService.findByUserId(1L)).thenReturn(Collections.emptyList());
        when(messageSource.getMessage(eq("sync.noNotes"), isNull(), any())).thenReturn("No external notes synced yet");

        String view = accountController.showAccountsPage(session, model);

        assertEquals("accounts/index", view);
        verify(model).addAttribute("accounts", Collections.emptyList());
        verify(model).addAttribute("bookLanguage", "en");
        verify(model).addAttribute("notionAccounts", Collections.emptyList());
        verify(model).addAttribute("hasNotionAccount", false);
        verify(model).addAttribute("syncCount", 0L);
        verify(model).addAttribute("syncStatus", "No external notes synced yet");
    }

    @Test
    void testLinkAccount() {
        when(session.getAttribute("userId")).thenReturn(1L);
        when(linkedAccountService.findUserAccounts(1L)).thenReturn(Collections.emptyList());

        LinkedAccount linked = new LinkedAccount();
        linked.setUserId(1L);
        linked.setPlatform("notion");
        lenient().when(linkedAccountService.linkAccount(anyLong(), anyString(), anyString(), isNull(), isNull(), isNull(), isNull()))
            .thenReturn(linked);

        String view = accountController.linkAccount("notion", "My Notion", session, redirectAttributes);

        assertEquals("redirect:/accounts", view);
        verify(linkedAccountService).linkAccount(eq(1L), eq("notion"), anyString(), isNull(), isNull(), isNull(), isNull());
        verify(redirectAttributes).addFlashAttribute(eq("success"), anyString());
    }

    @Test
    void testUnlinkAccount() {
        when(session.getAttribute("userId")).thenReturn(1L);

        String view = accountController.unlinkAccount("notion", session, redirectAttributes);

        assertEquals("redirect:/accounts", view);
        verify(linkedAccountService).unlinkAccount(1L, "notion");
        verify(redirectAttributes).addFlashAttribute(eq("success"), anyString());
    }
}
