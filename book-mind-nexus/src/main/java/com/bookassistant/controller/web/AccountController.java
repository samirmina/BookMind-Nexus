package com.bookassistant.controller.web;

import com.bookassistant.entity.LinkedAccount;
import com.bookassistant.service.infrastructure.ExternalNoteService;
import com.bookassistant.service.infrastructure.LinkedAccountService;
import com.bookassistant.service.integration.SyncService;
import com.bookassistant.util.ErrorMessageSanitizer;
import com.bookassistant.util.LoggingUtils;
import com.bookassistant.util.StringUtils;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.List;

@Controller
public class AccountController {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    private final LinkedAccountService linkedAccountService;
    private final MessageSource messageSource;
    private final SyncService syncService;
    private final ExternalNoteService externalNoteService;

    public AccountController(LinkedAccountService linkedAccountService,
                             MessageSource messageSource,
                             SyncService syncService,
                             ExternalNoteService externalNoteService) {
        this.linkedAccountService = linkedAccountService;
        this.messageSource = messageSource;
        this.syncService = syncService;
        this.externalNoteService = externalNoteService;
    }

    @GetMapping("/accounts")
    public String showAccountsPage(HttpSession session, Model model) {
        LoggingUtils.enter(log, "showAccountsPage");

        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            log.warn("Unauthorized accounts access attempt");
            return "redirect:/login";
        }

        try {
            List<LinkedAccount> accounts = linkedAccountService.findUserAccounts(userId);
            model.addAttribute("accounts", accounts);
            model.addAttribute("bookLanguage", "en");

            List<LinkedAccount> notionAccounts = Collections.emptyList();
            if (userId != null) {
                notionAccounts = accounts.stream()
                    .filter(a -> "notion".equals(a.getPlatform()))
                    .toList();
            }
            model.addAttribute("notionAccounts", notionAccounts);
            model.addAttribute("hasNotionAccount", !notionAccounts.isEmpty());

            long noteCount = externalNoteService.findByUserId(userId).size();
            model.addAttribute("syncCount", noteCount);
            String syncStatusKey = noteCount > 0 ? "sync.success" : "sync.noNotes";
            String syncStatus = messageSource.getMessage(syncStatusKey, null, LocaleContextHolder.getLocale());
            model.addAttribute("syncStatus", syncStatus);

            if (!notionAccounts.isEmpty()) {
                LinkedAccount notionAccount = notionAccounts.get(0);
                boolean hasToken = notionAccount.getAccessToken() != null && !notionAccount.getAccessToken().isBlank();
                model.addAttribute("notionHasToken", hasToken);
                log.info("Accounts page | userId={} | notionAccountId={} | hasToken={}", userId, notionAccount.getId(), hasToken);
            } else {
                model.addAttribute("notionHasToken", false);
            }

            return "accounts/index";
        } catch (Exception e) {
            log.error("Failed to load accounts page | error={}", e.getMessage(), e);
            model.addAttribute("error", messageSource.getMessage("account.page.error", null, LocaleContextHolder.getLocale()));
            model.addAttribute("bookLanguage", "en");
            return "accounts/index";
        }
    }

    @PostMapping("/accounts/link")
    public String linkAccount(@RequestParam("platform") String platform,
                              @RequestParam(value = "accountName", required = false) String accountName,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        LoggingUtils.enter(log, "linkAccount", "platform", platform);

        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            redirectAttributes.addFlashAttribute("error", messageSource.getMessage("account.login.required", null, LocaleContextHolder.getLocale()));
            return "redirect:/login";
        }

        long startTime = System.currentTimeMillis();

        if (StringUtils.isEmpty(platform)) {
            redirectAttributes.addFlashAttribute("error", messageSource.getMessage("account.platform.required", null, LocaleContextHolder.getLocale()));
            return "redirect:/accounts";
        }

        try {
            LinkedAccount linked = linkedAccountService.linkAccount(
                userId, platform, accountName, null, null, null, null
            );

            redirectAttributes.addFlashAttribute("success", messageSource.getMessage("account.link.success", null, LocaleContextHolder.getLocale()));
            return "redirect:/accounts";

        } catch (Exception e) {
            String rawErrorMessage = e.getMessage();
            String userErrorMessage = ErrorMessageSanitizer.sanitize(rawErrorMessage);

            log.error("Failed to link account | userId={} | platform={} | error={}",
                userId, platform, rawErrorMessage);

            redirectAttributes.addFlashAttribute("error", messageSource.getMessage("account.link.error", new Object[]{userErrorMessage}, LocaleContextHolder.getLocale()));
            return "redirect:/accounts";
        }
    }

    @PostMapping("/accounts/unlink")
    public String unlinkAccount(@RequestParam("platform") String platform,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        LoggingUtils.enter(log, "unlinkAccount", "platform", platform);

        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            redirectAttributes.addFlashAttribute("error", messageSource.getMessage("account.login.required", null, LocaleContextHolder.getLocale()));
            return "redirect:/login";
        }

        try {
            linkedAccountService.unlinkAccount(userId, platform);

            redirectAttributes.addFlashAttribute("success", messageSource.getMessage("account.unlink.success", null, LocaleContextHolder.getLocale()));
            return "redirect:/accounts";

        } catch (Exception e) {
            log.error("Failed to unlink account | userId={} | platform={} | error={}",
                userId, platform, e.getMessage());

            redirectAttributes.addFlashAttribute("error", messageSource.getMessage("account.unlink.error", new Object[]{e.getMessage()}, LocaleContextHolder.getLocale()));
            return "redirect:/accounts";
        }
    }

    @PostMapping("/accounts/sync")
    public String syncAccounts(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        LoggingUtils.enter(log, "syncAccounts");

        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            redirectAttributes.addFlashAttribute("error", messageSource.getMessage("account.login.required", null, LocaleContextHolder.getLocale()));
            return "redirect:/login";
        }

        try {
            log.info("Manual sync started | userId={}", userId);
            int synced = syncService.syncUserNotes(userId);

            log.info("Manual sync completed | userId={} | synced={}", userId, synced);
            String syncSuccess = messageSource.getMessage("sync.success.count", new Object[]{synced}, LocaleContextHolder.getLocale());
            redirectAttributes.addFlashAttribute("success", syncSuccess);
            return "redirect:/accounts";

        } catch (Exception e) {
            log.error("Failed to sync accounts | userId={} | error={}", userId, e.getMessage(), e);

            redirectAttributes.addFlashAttribute("error", messageSource.getMessage("account.sync.error.with.message", new Object[]{e.getMessage()}, LocaleContextHolder.getLocale()));
            return "redirect:/accounts";
        }
    }

    private String buildAccountStateJson(Long userId) {
        try {
            List<LinkedAccount> accounts = linkedAccountService.findUserAccounts(userId);
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            for (LinkedAccount acc : accounts) {
                sb.append("\"").append(acc.getPlatform()).append("\":{");
                sb.append("\"id\":").append(acc.getId()).append(",");
                sb.append("\"name\":\"").append(acc.getAccountName()).append("\"");
                sb.append("},");
            }
            if (sb.length() > 1) sb.setLength(sb.length() - 1);
            sb.append("}");
            return sb.toString();
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }
}
