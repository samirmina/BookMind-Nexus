package com.bookassistant.controller.web;

import com.bookassistant.service.config.RuntimeConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/settings")
public class SettingsController {

    private final RuntimeConfigService runtimeConfigService;

    public SettingsController(RuntimeConfigService runtimeConfigService) {
        this.runtimeConfigService = runtimeConfigService;
    }

    @GetMapping("/extraction-mode")
    public Map<String, String> getExtractionMode() {
        return Map.of("mode", runtimeConfigService.getExtractionMode());
    }

    @PostMapping("/extraction-mode")
    public ResponseEntity<?> setExtractionMode(@RequestBody Map<String, String> body) {
        String mode = body.get("mode");
        if (mode == null || mode.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "mode is required"));
        }
        runtimeConfigService.setExtractionMode(mode);
        return ResponseEntity.ok(Map.of("mode", mode));
    }
}
