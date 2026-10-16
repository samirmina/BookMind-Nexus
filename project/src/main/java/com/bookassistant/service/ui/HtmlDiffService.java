package com.bookassistant.service.ui;

import com.bookassistant.entity.ui.UiAnomaly;
import com.bookassistant.entity.ui.UiElementRegistry;
import com.bookassistant.entity.ui.UiSnapshot;
import com.bookassistant.repository.ui.UiAnomalyRepository;
import com.bookassistant.repository.ui.UiElementRegistryRepository;
import com.bookassistant.repository.ui.UiSnapshotRepository;
import com.bookassistant.util.LoggingUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Analyzes HTML snapshots against expected element registries and
 * detects anomalies such as duplicates, missing elements, and state mismatches.
 */
@Service
public class HtmlDiffService {

    private static final Logger log = LoggerFactory.getLogger(HtmlDiffService.class);

    private final UiSnapshotRepository snapshotRepository;
    private final UiAnomalyRepository anomalyRepository;
    private final UiElementRegistryRepository elementRegistryRepository;

    public HtmlDiffService(UiSnapshotRepository snapshotRepository,
                           UiAnomalyRepository anomalyRepository,
                           UiElementRegistryRepository elementRegistryRepository) {
        this.snapshotRepository = snapshotRepository;
        this.anomalyRepository = anomalyRepository;
        this.elementRegistryRepository = elementRegistryRepository;
    }

    public List<UiAnomaly> analyzeSnapshot(UiSnapshot snapshot, String userState) {
        LoggingUtils.enter(log, "analyzeSnapshot", "page", snapshot.getPagePath());
        List<UiAnomaly> anomalies = new ArrayList<>();

        Document doc = Jsoup.parse(snapshot.getHtmlContent());
        doc.outputSettings().prettyPrint(false);

        List<UiElementRegistry> registry = elementRegistryRepository
                .findByPagePathAndUserStateOrderByPriorityDesc(snapshot.getPagePath(), userState);

        for (UiElementRegistry expected : registry) {
            Element element = doc.selectFirst(expected.getElementSelector());
            boolean exists = element != null;
            boolean visible = exists && isElementVisible(element);

            if (expected.getExpectedVisible() && !exists) {
                anomalies.add(createAnomaly(snapshot, expected,
                        "missing_element",
                        "Expected element not found: " + expected.getElementSelector(),
                        "Element should be present for user state: " + userState));
            } else if (!expected.getExpectedVisible() && exists) {
                anomalies.add(createAnomaly(snapshot, expected,
                        "unexpected_element",
                        "Unexpected element present: " + expected.getElementSelector(),
                        "Element should be hidden for user state: " + userState));
            }

            if (exists && expected.getExpectedText() != null && !expected.getExpectedText().isEmpty()) {
                String actualText = element.text().trim().toLowerCase(Locale.ROOT);
                String expectedText = expected.getExpectedText().toLowerCase(Locale.ROOT);
                if (!actualText.contains(expectedText)) {
                    anomalies.add(createAnomaly(snapshot, expected,
                            "text_mismatch",
                            "Element text mismatch for: " + expected.getElementSelector(),
                            "Expected text containing: " + expected.getExpectedText(),
                            "Actual text: " + element.text().trim()));
                }
            }

            if (exists && expected.getExpectedHref() != null && !expected.getExpectedHref().isEmpty()) {
                String actualHref = element.attr("href");
                if (!expected.getExpectedHref().equals(actualHref)) {
                    anomalies.add(createAnomaly(snapshot, expected,
                            "href_mismatch",
                            "Link href mismatch for: " + expected.getElementSelector(),
                            "Expected href: " + expected.getExpectedHref(),
                            "Actual href: " + actualHref));
                }
            }
        }

        anomalies.addAll(checkDuplicateIds(doc, snapshot));
        anomalies.addAll(checkOrphanedScripts(doc, snapshot));
        anomalies.addAll(checkAuthStateConsistency(doc, snapshot, userState));

        for (UiAnomaly anomaly : anomalies) {
            anomalyRepository.save(anomaly);
        }

        LoggingUtils.exit(log, "analyzeSnapshot", "anomalies", anomalies.size());
        return anomalies;
    }

    private List<UiAnomaly> checkDuplicateIds(Document doc, UiSnapshot snapshot) {
        List<UiAnomaly> anomalies = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (Element el : doc.getAllElements()) {
            String id = el.id();
            if (!id.isEmpty()) {
                if (ids.contains(id)) {
                    anomalies.add(createAnomaly(snapshot, null,
                            "duplicate_id",
                            "Duplicate element ID detected: " + id,
                            "IDs must be unique per page",
                            "Found multiple elements with id=" + id));
                }
                ids.add(id);
            }
        }
        return anomalies;
    }

    private List<UiAnomaly> checkOrphanedScripts(Document doc, UiSnapshot snapshot) {
        List<UiAnomaly> anomalies = new ArrayList<>();
        for (Element script : doc.select("script[src]")) {
            String src = script.attr("src");
            if (src.startsWith("http") && !src.contains("cdn.jsdelivr.net") && !src.contains("fonts.googleapis.com")) {
                anomalies.add(createAnomaly(snapshot, null,
                        "external_script",
                        "External script loaded: " + src,
                        "Verify external script dependency is necessary",
                        src));
            }
        }
        return anomalies;
    }

    private List<UiAnomaly> checkAuthStateConsistency(Document doc, UiSnapshot snapshot, String userState) {
        List<UiAnomaly> anomalies = new ArrayList<>();
        boolean isLoggedIn = "logged_in".equals(userState);

        if (isLoggedIn) {
            for (Element link : doc.select("a[href=/login], a[href=/register]")) {
                if (isElementVisible(link)) {
                    anomalies.add(createAnomaly(snapshot, null,
                            "auth_state_mismatch",
                            "Login/Register link visible while user is logged in",
                            "Login/Register links should be hidden for logged-in users",
                            link.outerHtml()));
                }
            }
        } else {
            for (Element link : doc.select("a[href=/app], a[href=/outputs]")) {
                if (isElementVisible(link)) {
                    anomalies.add(createAnomaly(snapshot, null,
                            "auth_state_mismatch",
                            "App/Outputs link visible while user is logged out",
                            "App/Outputs links should be hidden for anonymous users",
                            link.outerHtml()));
                }
            }
        }
        return anomalies;
    }

    private boolean isElementVisible(Element element) {
        String style = element.attr("style").toLowerCase(Locale.ROOT);
        String hidden = element.attr("hidden");

        if (style.contains("display: none")) return false;
        if (style.contains("visibility: hidden")) return false;
        if (!hidden.isEmpty()) return false;
        if (element.hasClass("hidden") || element.hasClass("d-none")) return false;

        try {
            String opacity = element.attr("style").replaceAll("(?i).*opacity:\\s*([0-9.]+).*", "$1");
            if (opacity.matches("[0-9.]+") && Double.parseDouble(opacity) == 0) return false;
        } catch (Exception ignored) {}

        return true;
    }

    private UiAnomaly createAnomaly(UiSnapshot snapshot, UiElementRegistry expected,
                                    String type, String description, String... extras) {
        UiAnomaly anomaly = new UiAnomaly();
        anomaly.setSnapshotId(snapshot.getId());
        anomaly.setPagePath(snapshot.getPagePath());
        anomaly.setAnomalyType(type);
        anomaly.setSeverity(extras.length > 2 ? "error" : "warning");
        anomaly.setDescription(description);
        anomaly.setElementSelector(expected != null ? expected.getElementSelector() : null);
        anomaly.setExpectedValue(expected != null ? expected.getExpectedText() : null);
        anomaly.setActualValue(extras.length > 1 ? extras[1] : null);
        anomaly.setRemediation(extras.length > 2 ? extras[2] : null);
        return anomaly;
    }
}
