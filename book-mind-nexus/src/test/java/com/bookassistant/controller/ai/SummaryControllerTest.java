package com.bookassistant.controller.ai;

import com.bookassistant.dto.PageSummaryRequest;
import com.bookassistant.model.BookData;
import com.bookassistant.model.PageData;
import com.bookassistant.service.ai.AiService;
import com.bookassistant.service.file.OnDemandExtractionService;
import com.bookassistant.service.infrastructure.CacheService;
import com.bookassistant.service.infrastructure.BookOutputService;
import com.bookassistant.service.text.MarkdownService;
import com.bookassistant.service.text.TextDirectionService;
import com.bookassistant.session.BookSession;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SummaryControllerTest {

    @Mock
    private BookSession bookSession;

    @Mock
    private AiService aiService;

    @Mock
    private MarkdownService markdownService;

    @Mock
    private TextDirectionService textDirectionService;

    @Mock
    private CacheService cacheService;

    @Mock
    private BookOutputService bookOutputService;

    @Mock
    private OnDemandExtractionService onDemandExtractionService;

    @Mock
    private MessageSource messageSource;

    @Mock
    private HttpSession session;

    @Mock
    private Model model;

    @Mock
    private TextDirectionService.FormattedResponse formattedResponse;

    @InjectMocks
    private SummaryController summaryController;

    @BeforeEach
    void setUp() {
        lenient().when(bookSession.hasBook()).thenReturn(true);
        lenient().when(bookSession.getPageCount()).thenReturn(10);
        lenient().when(bookSession.getDetectedLanguage()).thenReturn("en");
        lenient().when(bookSession.getBook()).thenReturn(Optional.of(new BookData("test.pdf", List.of(), "text")));
        lenient().when(bookSession.extractTextFromPageRange(anyInt(), anyInt())).thenReturn("Sample extracted text for page range.");
        lenient().when(session.getAttribute("userId")).thenReturn(1L);
        lenient().when(aiService.truncateToTokens(any(String.class), anyInt())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(aiService.getAnswerFromAi(any(String.class), any(String.class), any(String.class))).thenReturn("summary text");
        lenient().when(markdownService.convertMarkdownToHtml(any(String.class))).thenReturn("<p>summary text</p>");
        lenient().when(textDirectionService.createFormattedResponse(any(String.class), any(String.class)))
                .thenReturn(formattedResponse);
        lenient().when(formattedResponse.getDirection()).thenReturn("ltr");
        lenient().when(formattedResponse.getLanguage()).thenReturn("en");
        lenient().when(formattedResponse.getHtmlAttributes()).thenReturn("");
        lenient().when(formattedResponse.getInlineStyle()).thenReturn("");
    }

    @Test
    void testJsonSummary_Success() {
        PageSummaryRequest request = new PageSummaryRequest(1L, 1, 2, null, "auto");

        ResponseEntity<Map<String, Object>> response = summaryController.generatePagesSummaryJson(request, session);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().get("summaryPages"));
        assertEquals("ltr", response.getBody().get("summaryPagesDirection"));
    }

    @Test
    void testJsonSummary_Unauthorized() {
        when(session.getAttribute("userId")).thenReturn(null);

        PageSummaryRequest request = new PageSummaryRequest(1L, 1, 2, null, "auto");

        ResponseEntity<Map<String, Object>> response = summaryController.generatePagesSummaryJson(request, session);

        assertNotNull(response);
        assertEquals(401, response.getStatusCode().value());
        assertEquals("Unauthorized", response.getBody().get("error"));
    }

    @Test
    void testJsonSummary_InvalidRange() {
        PageSummaryRequest request = new PageSummaryRequest(1L, 5, 2, null, "auto");

        ResponseEntity<Map<String, Object>> response = summaryController.generatePagesSummaryJson(request, session);

        assertNotNull(response);
        assertEquals(400, response.getStatusCode().value());
        assertEquals("Invalid page range", response.getBody().get("error"));
    }

    @Test
    void testJsonSummary_ModelOnlyChunkSize() {
        when(session.getAttribute("userId")).thenReturn(1L);

        PageSummaryRequest request = new PageSummaryRequest(1L, 1, 2, null, "model-only");

        ResponseEntity<Map<String, Object>> response = summaryController.generatePagesSummaryJson(request, session);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().get("summaryPages"));
    }
}
