package com.bookassistant.controller.web;

import com.bookassistant.controller.web.LanguageController;
import com.bookassistant.service.infrastructure.ExternalNoteService;
import com.bookassistant.service.infrastructure.LinkedAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Adds common model attributes to all controllers.
 * <p>
 * <b>What:</b> Injects UI language preference and linked account status into every
 * Thymeleaf model.
 * <p>
 * <b>Why:</b> Eliminates repetitive model.addAttribute("uiLanguage", ...) calls
 * in every controller method and ensures sync-status attributes are always present.
 */
@ControllerAdvice
public class GlobalModelAttributes {

    private final LinkedAccountService linkedAccountService;
    private final ExternalNoteService externalNoteService;

    public GlobalModelAttributes(LinkedAccountService linkedAccountService,
                                 ExternalNoteService externalNoteService) {
        this.linkedAccountService = linkedAccountService;
        this.externalNoteService = externalNoteService;
    }

    @ModelAttribute
    public void addCommonAttributes(HttpServletRequest request, Model model) {
        String uiLang = LanguageController.getUiLanguage(request);
        model.addAttribute("uiLanguage", uiLang);
        model.addAttribute("isRtl", "ar".equals(uiLang));
        model.addAttribute("dirAttr", "ar".equals(uiLang) ? "rtl" : "ltr");
        model.addAttribute("langAttr", uiLang);

        HttpSession session = request.getSession(false);
        if (session != null) {
            Object userIdObj = session.getAttribute("userId");
            if (userIdObj instanceof Long userId) {
                model.addAttribute("currentUserId", userId);
                model.addAttribute("isLoggedIn", true);

                try {
                    var accounts = linkedAccountService.findUserAccounts(userId);
                    boolean hasLinkedAccount = accounts.stream().anyMatch(a -> "notion".equals(a.getPlatform()));
                    int noteCount = externalNoteService.findByUserId(userId).size();
                    model.addAttribute("hasLinkedAccount", hasLinkedAccount);
                    model.addAttribute("appSyncNoteCount", noteCount);
                    model.addAttribute("appSyncConfigured", hasLinkedAccount);
                } catch (Exception e) {
                    model.addAttribute("hasLinkedAccount", false);
                    model.addAttribute("appSyncNoteCount", 0);
                    model.addAttribute("appSyncConfigured", false);
                }
            } else {
                model.addAttribute("isLoggedIn", false);
                model.addAttribute("hasLinkedAccount", false);
                model.addAttribute("appSyncNoteCount", 0);
                model.addAttribute("appSyncConfigured", false);
            }

            Object userEmailObj = session.getAttribute("userEmail");
            model.addAttribute("currentUserEmail", userEmailObj != null ? userEmailObj : null);
        } else {
            model.addAttribute("isLoggedIn", false);
            model.addAttribute("currentUserEmail", null);
            model.addAttribute("hasLinkedAccount", false);
            model.addAttribute("appSyncNoteCount", 0);
            model.addAttribute("appSyncConfigured", false);
        }
    }
}
