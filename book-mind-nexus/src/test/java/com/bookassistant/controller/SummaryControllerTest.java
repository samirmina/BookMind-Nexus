package com.bookassistant.controller;

import com.bookassistant.controller.ai.SummaryController;
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
import org.springframework.ui.Model;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SummaryController.
 */
@ExtendWith(MockitoExtension.class)
class SummaryControllerTest {

    @Mock
    private BookSession bookSession;

    @Mock
    private AiService aiService;

    @Mock
    private CacheService cacheService;

    @Mock
    private MarkdownService markdownService;

    @Mock
    private TextDirectionService textDirectionService;

    @Mock
    private BookOutputService bookOutputService;

    @Mock
    private OnDemandExtractionService onDemandExtractionService;

    @Mock
    private Model model;

    @Mock
    private MessageSource messageSource;

    @Mock
    private HttpSession session;

    @Mock
    private TextDirectionService.FormattedResponse formattedResponse;

    @InjectMocks
    private SummaryController summaryController;

    private BookData testBook;

    @BeforeEach
    void setUp() throws Exception {
        List<PageData> pages = List.of(
            new PageData(1, "Page 1 content"),
            new PageData(2, "Page 2 content")
        );
        testBook = new BookData("test.pdf", pages, "Full book content");
        lenient().when(bookSession.getUserId()).thenReturn(1L);
        lenient().when(bookSession.getCurrentBookId()).thenReturn(1L);
        lenient().when(bookSession.resolveBookId(anyLong())).thenReturn(null);
        lenient().when(session.getAttribute("userId")).thenReturn(1L);
        lenient().when(messageSource.getMessage(anyString(), isNull(), any())).thenReturn("default");
        lenient().when(aiService.truncateToTokens(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(0));

        java.lang.reflect.Field pagesModeField = SummaryController.class.getDeclaredField("pagesMode");
        pagesModeField.setAccessible(true);
        pagesModeField.set(summaryController, "aisummary");
    }

    @Test
    void testGenerateGeneralSummary_Success() {
        when(bookSession.getBook()).thenReturn(Optional.of(testBook));
        when(bookSession.hasBook()).thenReturn(true);
        when(bookSession.getDetectedLanguage()).thenReturn("ar");
        when(bookSession.getPageCount()).thenReturn(500);
        when(bookSession.extractTextFromPageRange(anyInt(), anyInt())).thenReturn("Chunk text");
        when(aiService.getAnswerFromAi(anyString(), anyString(), anyString())).thenReturn("ملخص الكتاب");
        when(markdownService.convertMarkdownToHtml(anyString())).thenReturn("<p>ملخص الكتاب</p>");
        when(textDirectionService.createFormattedResponse(anyString(), anyString())).thenReturn(formattedResponse);
        when(formattedResponse.getDirection()).thenReturn("rtl");
        when(formattedResponse.getLanguage()).thenReturn("ar");
        when(formattedResponse.getHtmlAttributes()).thenReturn("dir=\"rtl\" lang=\"ar\"");
        when(formattedResponse.getInlineStyle()).thenReturn("direction: rtl; text-align: right;");
        when(model.addAttribute(anyString(), any())).thenReturn(model);

        String view = summaryController.generateGeneralSummary(null, session, model);

        assertEquals("index", view);
        verify(aiService, atLeastOnce()).getAnswerFromAi(anyString(), anyString(), eq("ar"));
        verify(model).addAttribute("summaryGeneral", "<p>ملخص الكتاب</p>");
        verify(model).addAttribute("hasBook", true);
        verify(model).addAttribute("bookLanguage", "ar");
    }

    @Test
    void testGenerateGeneralSummary_EnglishOutput() {
        when(bookSession.getBook()).thenReturn(Optional.of(testBook));
        when(bookSession.hasBook()).thenReturn(true);
        when(bookSession.getDetectedLanguage()).thenReturn("en");
        when(bookSession.getPageCount()).thenReturn(500);
        when(bookSession.extractTextFromPageRange(anyInt(), anyInt())).thenReturn("Chunk text");
        when(aiService.getAnswerFromAi(anyString(), anyString(), anyString())).thenReturn("Book summary");
        when(markdownService.convertMarkdownToHtml(anyString())).thenReturn("<p>Book summary</p>");
        when(textDirectionService.createFormattedResponse(anyString(), anyString())).thenReturn(formattedResponse);
        when(formattedResponse.getDirection()).thenReturn("ltr");
        when(formattedResponse.getLanguage()).thenReturn("en");
        when(formattedResponse.getHtmlAttributes()).thenReturn("dir=\"ltr\" lang=\"en\"");
        when(formattedResponse.getInlineStyle()).thenReturn("direction: ltr; text-align: left;");
        when(model.addAttribute(anyString(), any())).thenReturn(model);

        String view = summaryController.generateGeneralSummary("en", session, model);

        assertEquals("index", view);
        verify(aiService, atLeastOnce()).getAnswerFromAi(anyString(), anyString(), eq("en"));
        verify(model).addAttribute("summaryGeneral", "<p>Book summary</p>");
    }

    @Test
    void testGenerateGeneralSummary_NoBook() {
        lenient().when(bookSession.getBook()).thenReturn(Optional.empty());
        lenient().when(bookSession.hasBook()).thenReturn(false);
        when(bookSession.getDetectedLanguage()).thenReturn("ar");
        when(model.addAttribute(anyString(), any())).thenReturn(model);

        String view = summaryController.generateGeneralSummary(null, session, model);

        assertEquals("index", view);
        verify(model, atLeastOnce()).addAttribute(eq("error"), anyString());
    }

    @Test
    void testGeneratePagesSummary_Success() {
        when(bookSession.extractTextFromPageRange(1, 2)).thenReturn("Page 1 content\n\nPage 2 content");
        when(bookSession.hasBook()).thenReturn(true);
        when(bookSession.getDetectedLanguage()).thenReturn("ar");
        when(bookSession.getPageCount()).thenReturn(500);
        when(aiService.getAnswerFromAi(anyString(), anyString(), anyString())).thenReturn("ملخص الصفحات");
        when(markdownService.convertMarkdownToHtml(anyString())).thenReturn("<p>ملخص الصفحات</p>");
        when(textDirectionService.createFormattedResponse(anyString(), anyString())).thenReturn(formattedResponse);
        when(formattedResponse.getDirection()).thenReturn("rtl");
        when(formattedResponse.getLanguage()).thenReturn("ar");
        when(formattedResponse.getHtmlAttributes()).thenReturn("dir=\"rtl\" lang=\"ar\"");
        when(formattedResponse.getInlineStyle()).thenReturn("direction: rtl; text-align: right;");
        when(model.addAttribute(anyString(), any())).thenReturn(model);

        String view = summaryController.generatePagesSummary(1, 2, null, "5", session, model);

        assertEquals("index", view);
        verify(aiService).getAnswerFromAi(anyString(), argThat(s -> s != null && s.contains("Page 1 content")), eq("ar"));
        verify(model).addAttribute("summaryPages", "<p>ملخص الصفحات</p>");
        verify(model, atLeastOnce()).addAttribute(eq("hasBook"), eq(true));
    }

    @Test
    void testGeneratePagesSummary_EnglishOutput() {
        when(bookSession.extractTextFromPageRange(1, 5)).thenReturn("Content from pages 1-5");
        when(bookSession.hasBook()).thenReturn(true);
        when(bookSession.getDetectedLanguage()).thenReturn("en");
        when(bookSession.getPageCount()).thenReturn(500);
        when(aiService.getAnswerFromAi(anyString(), anyString(), anyString())).thenReturn("Pages summary");
        when(markdownService.convertMarkdownToHtml(anyString())).thenReturn("<p>Pages summary</p>");
        when(textDirectionService.createFormattedResponse(anyString(), anyString())).thenReturn(formattedResponse);
        when(formattedResponse.getDirection()).thenReturn("ltr");
        when(formattedResponse.getLanguage()).thenReturn("en");
        when(formattedResponse.getHtmlAttributes()).thenReturn("dir=\"ltr\" lang=\"en\"");
        when(formattedResponse.getInlineStyle()).thenReturn("direction: ltr; text-align: left;");
        when(model.addAttribute(anyString(), any())).thenReturn(model);

        String view = summaryController.generatePagesSummary(1, 5, "en", "5", session, model);

        assertEquals("index", view);
        verify(aiService).getAnswerFromAi(anyString(), eq("Book text (pages 1-5):\nContent from pages 1-5"), eq("en"));
    }

    @Test
    void testGeneratePagesSummary_InvalidRange() {
        lenient().when(bookSession.hasBook()).thenReturn(true);
        when(bookSession.getDetectedLanguage()).thenReturn("ar");
        when(model.addAttribute(anyString(), any())).thenReturn(model);

        String view = summaryController.generatePagesSummary(10, 5, null, "5", session, model);

        assertEquals("index", view);
        verify(aiService, never()).getAnswerFromAi(anyString(), anyString(), anyString());
        verify(model, atLeastOnce()).addAttribute(eq("error"), anyString());
    }

    @Test
    void testControllerCreation() {
        assertNotNull(summaryController);
    }

    @Test
    void testLanguageFallbackToBookLanguage() {
        when(bookSession.getBook()).thenReturn(Optional.of(testBook));
        when(bookSession.hasBook()).thenReturn(true);
        when(bookSession.getDetectedLanguage()).thenReturn("en");
        when(bookSession.getPageCount()).thenReturn(500);
        when(bookSession.extractTextFromPageRange(anyInt(), anyInt())).thenReturn("Full book content");
        when(aiService.getAnswerFromAi(anyString(), anyString(), anyString())).thenReturn("Summary");
        when(markdownService.convertMarkdownToHtml(anyString())).thenReturn("<p>Summary</p>");
        when(textDirectionService.createFormattedResponse(anyString(), anyString())).thenReturn(formattedResponse);
        when(formattedResponse.getDirection()).thenReturn("ltr");
        when(formattedResponse.getLanguage()).thenReturn("en");
        when(formattedResponse.getHtmlAttributes()).thenReturn("dir=\"ltr\" lang=\"en\"");
        when(formattedResponse.getInlineStyle()).thenReturn("direction: ltr; text-align: left;");
        when(model.addAttribute(anyString(), any())).thenReturn(model);

        String view = summaryController.generateGeneralSummary(null, session, model);

        verify(aiService, atLeastOnce()).getAnswerFromAi(anyString(), anyString(), eq("en"));
    }

    @Test
    void testOutputLanguageOverridesBookLanguage() {
        when(bookSession.getBook()).thenReturn(Optional.of(testBook));
        when(bookSession.hasBook()).thenReturn(true);
        when(bookSession.getDetectedLanguage()).thenReturn("en");
        when(bookSession.getPageCount()).thenReturn(500);
        when(bookSession.extractTextFromPageRange(anyInt(), anyInt())).thenReturn("Full book content");
        when(aiService.getAnswerFromAi(anyString(), anyString(), anyString())).thenReturn("ملخص");
        when(markdownService.convertMarkdownToHtml(anyString())).thenReturn("<p>ملخص</p>");
        when(textDirectionService.createFormattedResponse(anyString(), anyString())).thenReturn(formattedResponse);
        when(formattedResponse.getDirection()).thenReturn("rtl");
        when(formattedResponse.getLanguage()).thenReturn("ar");
        when(formattedResponse.getHtmlAttributes()).thenReturn("dir=\"rtl\" lang=\"ar\"");
        when(formattedResponse.getInlineStyle()).thenReturn("direction: rtl; text-align: right;");
        when(model.addAttribute(anyString(), any())).thenReturn(model);

        String view = summaryController.generateGeneralSummary("ar", session, model);

        verify(aiService, atLeastOnce()).getAnswerFromAi(anyString(), anyString(), eq("ar"));
    }
}
