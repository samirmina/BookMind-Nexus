package com.bookassistant.service.integration;

import com.bookassistant.entity.ExternalNote;
import com.bookassistant.entity.LinkedAccount;
import com.bookassistant.util.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter for Notion API.
 * <p>
 * <b>What:</b> Fetches pages and databases from Notion using OAuth tokens.
 * <p>
 * <b>Why:</b> Required to import user's Notion notes for cross-platform analysis.
 * <p>
 * <b>Where:</b> Implements the Notion integration per the project's linked-accounts feature.
 */
@Service
public class NotionAdapter implements ExternalPlatformAdapter {

    private static final Logger log = LoggerFactory.getLogger(NotionAdapter.class);
    private static final String NOTION_API_URL = "https://api.notion.com/v1";
    private static final String NOTION_VERSION = "2022-06-28";

    private final RestTemplate restTemplate;

    public NotionAdapter() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String getPlatformName() {
        return "notion";
    }

    @Override
    public List<ExternalNote> fetchNotes(Long userId, LinkedAccount account) {
        LoggingUtils.enter(log, "fetchNotes", "userId", userId);
        
        if (account == null || account.getAccessToken() == null || account.getAccessToken().isBlank()) {
            log.warn("No valid Notion access token for user {}", userId);
            return List.of();
        }

        try {
            List<ExternalNote> notes = new ArrayList<>();
            String token = account.getAccessToken();
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            headers.set("Notion-Version", NOTION_VERSION);
            headers.set("Content-Type", "application/json");

            int pageSize = 100;
            String startCursor = null;
            boolean hasMore = true;
            int totalResults = 0;

            while (hasMore) {
                Map<String, Object> body = new HashMap<>();
                body.put("filter", Map.of(
                    "property", "object",
                    "value", "page"
                ));
                body.put("page_size", pageSize);
                if (startCursor != null) {
                    body.put("start_cursor", startCursor);
                }

                HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
                log.info("Notion search request | userId={} | url={}", userId, NOTION_API_URL + "/search");
                
                ResponseEntity<Map> response = restTemplate.exchange(
                    NOTION_API_URL + "/search",
                    HttpMethod.POST,
                    request,
                    Map.class
                );

                int statusCode = response.getStatusCodeValue();
                log.info("Notion search response | userId={} | status={}", userId, statusCode);

                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    log.warn("Notion search failed for user {} | status={}", userId, statusCode);
                    break;
                }

                Map<String, Object> bodyResponse = response.getBody();
                List<Map<String, Object>> results = (List<Map<String, Object>>) bodyResponse.getOrDefault("results", List.of());
                totalResults += results.size();
                
                log.info("Notion search returned {} results for user {} | has_more={}", 
                    results.size(), userId, bodyResponse.get("has_more"));

                for (Map<String, Object> page : results) {
                    String pageId = (String) page.get("id");
                    String pageUrl = (String) page.get("url");
                    log.info("Processing page | userId={} | pageId={} | url={}", userId, pageId, pageUrl);
                    
                    ExternalNote note = extractNoteFromPage(page, userId, account.getId(), headers);
                    if (note != null) {
                        notes.add(note);
                        log.info("Extracted note | userId={} | pageId={} | title={} | contentLen={}", 
                            userId, pageId, note.getTitle(), note.getContent().length());
                    } else {
                        log.warn("Failed to extract note from page | userId={} | pageId={}", userId, pageId);
                    }
                }

                hasMore = (Boolean) bodyResponse.getOrDefault("has_more", false);
                startCursor = (String) bodyResponse.get("next_cursor");
                if (startCursor == null || startCursor.isBlank()) {
                    hasMore = false;
                }
            }

            log.info("Notion fetch completed | userId={} | totalResults={} | notesExtracted={}", 
                userId, totalResults, notes.size());
            return notes;

        } catch (Exception e) {
            log.error("Failed to fetch Notion notes for user {} | error={}", userId, e.getMessage(), e);
            return List.of();
        }
    }

    private ExternalNote extractNoteFromPage(Map<String, Object> page, Long userId, Long accountId, HttpHeaders headers) {
        try {
            String pageId = (String) page.get("id");
            String title = extractTitle(page);
            String content = extractContent(pageId, headers);
            String url = (String) page.get("url");

            if (title == null && content == null) {
                return null;
            }

            ExternalNote note = new ExternalNote();
            note.setUserId(userId);
            note.setLinkedAccountId(accountId);
            note.setPlatformNoteId(pageId);
            note.setTitle(title != null ? title : "Untitled");
            note.setContent(content != null ? content : "");
            note.setUrl(url != null ? url : "");
            note.setSyncedAt(LocalDateTime.now());
            return note;
        } catch (Exception e) {
            log.warn("Failed to extract note from page | error={}", e.getMessage());
            return null;
        }
    }

    private String extractTitle(Map<String, Object> page) {
        try {
            Map<String, Object> properties = (Map<String, Object>) page.get("properties");
            if (properties == null) return null;

            for (Object value : properties.values()) {
                if (value instanceof Map) {
                    Map<String, Object> prop = (Map<String, Object>) value;
                    String type = (String) prop.get("type");
                    if ("title".equals(type)) {
                        List<Map<String, Object>> titleList = (List<Map<String, Object>>) prop.get("title");
                        if (titleList != null && !titleList.isEmpty()) {
                            Map<String, Object> firstTitle = titleList.get(0);
                            Object textObj = firstTitle.get("plain_text");
                            if (textObj != null) return textObj.toString();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract title | error={}", e.getMessage());
        }
        return null;
    }

    private String extractContent(String pageId, HttpHeaders headers) {
        try {
            StringBuilder content = new StringBuilder();
            String startCursor = null;
            boolean hasMore = true;

            while (hasMore) {
                String url = NOTION_API_URL + "/blocks/" + pageId + "/children?page_size=100";
                if (startCursor != null) {
                    url += "&start_cursor=" + startCursor;
                }

                HttpEntity<Void> request = new HttpEntity<>(headers);
                ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, Map.class
                );

                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    break;
                }

                Map<String, Object> bodyResponse = response.getBody();
                List<Map<String, Object>> blocks = (List<Map<String, Object>>) bodyResponse.getOrDefault("results", List.of());
                if (blocks == null || blocks.isEmpty()) {
                    break;
                }

                for (Map<String, Object> block : blocks) {
                    String type = (String) block.get("type");
                    if (type == null) continue;

                    Map<String, Object> blockData = (Map<String, Object>) block.get(type);
                    if (blockData == null) continue;

                    List<Map<String, Object>> richText = (List<Map<String, Object>>) blockData.get("rich_text");
                    if (richText != null) {
                        for (Map<String, Object> rt : richText) {
                            Object plainText = rt.get("plain_text");
                            if (plainText != null) {
                                content.append(plainText.toString());
                            }
                        }
                        content.append("\n");
                    }
                }

                hasMore = (Boolean) bodyResponse.getOrDefault("has_more", false);
                startCursor = (String) bodyResponse.get("next_cursor");
                if (startCursor == null || startCursor.isBlank()) {
                    hasMore = false;
                }
            }

            return content.toString().trim();
        } catch (Exception e) {
            log.warn("Failed to fetch block children for page {} | error={}", pageId, e.getMessage());
            return "";
        }
    }
}
