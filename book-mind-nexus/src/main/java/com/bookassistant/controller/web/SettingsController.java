package com.bookassistant.controller.web;

import com.bookassistant.service.config.RuntimeConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/settings")
public class SettingsController {

    private static final Set<String> ALLOWED_EXTRACTION_MODES = Set.of("model-only", "pix2text", "text-layer");

    private final RuntimeConfigService runtimeConfigService;

    public SettingsController(RuntimeConfigService runtimeConfigService) {
        this.runtimeConfigService = runtimeConfigService;
    }

    @GetMapping("/extraction-mode")
    public Map<String, String> getExtractionMode(HttpSession session) {
        requireAuthenticated(session);
        return Map.of("mode", runtimeConfigService.getExtractionMode());
    }

    @PostMapping("/extraction-mode")
    public ResponseEntity<?> setExtractionMode(HttpSession session,
                                               @RequestBody Map<String, String> body) {
        requireAuthenticated(session);
        String mode = body.get("mode");
        if (mode == null || mode.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "mode is required"));
        }
        if (!ALLOWED_EXTRACTION_MODES.contains(mode)) {
            return ResponseEntity.badRequest().body(Map.of("error", "unsupported extraction mode: " + mode));
        }
        runtimeConfigService.setExtractionMode(mode);
        return ResponseEntity.ok(Map.of("mode", mode));
    }

    private void requireAuthenticated(HttpSession session) {
        if (session == null || session.getAttribute("userId") == null) {
            throw new IllegalStateException("Authentication required");
        }
    }
}
