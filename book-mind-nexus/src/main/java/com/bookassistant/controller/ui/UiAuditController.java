package com.bookassistant.controller.ui;

import com.bookassistant.entity.ui.UiAnomaly;
import com.bookassistant.entity.ui.UiSnapshot;
import com.bookassistant.service.ui.HtmlDiffService;
import com.bookassistant.util.LoggingUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST endpoint for frontend DOM audit results.
 * Receives anomaly reports from JavaScript running in the browser.
 */
@RestController
@RequestMapping("/ui")
public class UiAuditController {

    private static final Logger log = LoggerFactory.getLogger(UiAuditController.class);
    private final HtmlDiffService htmlDiffService;

    public UiAuditController(HtmlDiffService htmlDiffService) {
        this.htmlDiffService = htmlDiffService;
    }

    public static class FrontendAnomaly {
        public String type;
        public String severity;
        public String description;
        public String selector;
        public String expected;
        public String actual;
    }

    public static class AuditReport {
        public String pagePath;
        public String userState;
        public List<FrontendAnomaly> anomalies = new ArrayList<>();
        public Map<String, Object> domStats = new HashMap<>();
    }

    @PostMapping("/audit")
    public ResponseEntity<?> receiveAudit(HttpServletRequest request, @RequestBody AuditReport report) {
        LoggingUtils.enter(log, "receiveAudit", "page", report.pagePath);

        String sessionId = request.getSession().getId();
        String userState = report.userState != null ? report.userState : "anonymous";

        UiSnapshot snapshot = new UiSnapshot();
        snapshot.setPagePath(report.pagePath);
        snapshot.setSessionId(sessionId);
        snapshot.setSnapshotType("frontend_audit");
        snapshot.setUserAgent(request.getHeader("User-Agent"));

        List<UiAnomaly> savedAnomalies = new ArrayList<>();
        for (FrontendAnomaly fa : report.anomalies) {
            UiAnomaly anomaly = new UiAnomaly();
            anomaly.setPagePath(report.pagePath);
            anomaly.setAnomalyType(fa.type);
            anomaly.setSeverity(fa.severity != null ? fa.severity : "warning");
            anomaly.setDescription(fa.description);
            anomaly.setElementSelector(fa.selector);
            anomaly.setExpectedValue(fa.expected);
            anomaly.setActualValue(fa.actual);
            savedAnomalies.add(anomaly);
        }

        log.info("Frontend audit received for {} | anomalies={} | stats={}",
                report.pagePath, savedAnomalies.size(), report.domStats);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "received");
        response.put("anomaliesCount", savedAnomalies.size());
        response.put("pagePath", report.pagePath);
        return ResponseEntity.ok(response);
    }
}
