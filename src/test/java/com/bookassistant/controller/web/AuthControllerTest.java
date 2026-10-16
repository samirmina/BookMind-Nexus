package com.bookassistant.controller.web;

import com.bookassistant.entity.User;
import com.bookassistant.service.auth.UserService;
import com.bookassistant.service.infrastructure.ExternalNoteService;
import com.bookassistant.service.infrastructure.LinkedAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private LinkedAccountService linkedAccountService;

    @Mock
    private ExternalNoteService externalNoteService;

    @Mock
    private MessageSource messageSource;

    @Mock
    private Model model;

    @Mock
    private RedirectAttributes redirectAttributes;

    @Mock
    private HttpSession session;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        StaticMessageSource ms = new StaticMessageSource();
        ms.addMessage("register.success", Locale.US, "تم إنشاء الحساب بنجاح");
        ms.addMessage("login.success", Locale.US, "تم تسجيل الدخول بنجاح");
        ms.addMessage("logout.success", Locale.US, "تم تسجيل الخروج");
        this.messageSource = ms;
        this.authController = new AuthController(userService, messageSource);
    }

    @Test
    void testShowRegisterPage() {
        String view = authController.showRegisterPage(model);
        assertEquals("auth/register", view);
    }

    @Test
    void testShowLoginPage() {
        String view = authController.showLoginPage(model);
        assertEquals("auth/login", view);
    }

    @Test
    void testProcessRegistration_Success() {
        User user = new User();
        user.setEmail("test@example.com");
        user.setName("Test User");

        when(userService.register(anyString(), anyString(), any())).thenReturn(user);

        String view = authController.processRegistration("test@example.com", "password123", "Test User", session, redirectAttributes, model);

        assertEquals("redirect:/app", view);
        verify(redirectAttributes).addFlashAttribute("success", "تم إنشاء الحساب بنجاح");
    }

    @Test
    void testProcessRegistration_ValidationError() {
        when(userService.register(anyString(), anyString(), any()))
            .thenThrow(new IllegalArgumentException("Email is already registered"));

        String view = authController.processRegistration("test@example.com", "password123", "Test User", session, redirectAttributes, model);

        assertEquals("auth/register", view);
    }

    @Test
    void testProcessLogin_Success() {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getEmail()).thenReturn("test@example.com");
        when(user.getId()).thenReturn(1L);

        when(userService.login(anyString(), anyString())).thenReturn(user);

        String view = authController.processLogin("test@example.com", "password123", session, redirectAttributes, model);

        assertEquals("redirect:/app", view);
        verify(session).setAttribute("userId", 1L);
        verify(session).setAttribute("userEmail", "test@example.com");
        verify(redirectAttributes).addFlashAttribute("success", "تم تسجيل الدخول بنجاح");
    }

    @Test
    void testProcessLogin_InvalidCredentials() {
        when(userService.login(anyString(), anyString()))
            .thenThrow(new IllegalArgumentException("Invalid email or password"));

        String view = authController.processLogin("test@example.com", "wrongpassword", session, redirectAttributes, model);

        assertEquals("auth/login", view);
    }

    @Test
    void testProcessLogout() {
        when(session.getAttribute("userId")).thenReturn(1L);

        String view = authController.processLogout(session, redirectAttributes);

        assertEquals("redirect:/", view);
        verify(session).invalidate();
        verify(redirectAttributes).addFlashAttribute("success", "تم تسجيل الخروج");
    }
}
