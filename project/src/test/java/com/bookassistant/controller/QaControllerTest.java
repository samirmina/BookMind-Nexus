package com.bookassistant.controller;

import com.bookassistant.controller.ai.QaController;
import com.bookassistant.entity.ExternalNote;
import com.bookassistant.model.BookData;
import com.bookassistant.model.PageData;
import com.bookassistant.service.file.OnDemandExtractionService;
import com.bookassistant.service.infrastructure.ExternalNoteService;
import com.bookassistant.service.ai.AiService;
import com.bookassistant.service.infrastructure.CacheService;
import com.bookassistant.service.infrastructure.LinkedAccountService;
import com.bookassistant.service.integration.CrossPlatformQaService;
import com.bookassistant.service.infrastructure.BookOutputService;
import com.bookassistant.service.text.MarkdownService;
import com.bookassistant.service.text.TextDirectionService;
import com.bookassistant.session.BookSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.ui.Model;

import jakarta.servlet.http.HttpSession;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QaControllerTest {

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
    private CrossPlatformQaService crossPlatformQaService;

    @Mock
    private LinkedAccountService linkedAccountService;

    @Mock
    private ExternalNoteService externalNoteService;

    @Mock
    private BookOutputService bookOutputService;

    @Mock
    private OnDemandExtractionService onDemandExtractionService;

    @Mock
    private MessageSource messageSource;

    @Mock
    private Model model;

    @Mock
    private HttpSession session;

    @Mock
    private TextDirectionService.FormattedResponse formattedResponse;

    @InjectMocks
    private QaController qaController;

    private BookData testBook;

    @BeforeEach
    void setUp() {
        List<PageData> pages = List.of(
            new PageData(1, "Page 1 content with answer"),
            new PageData(2, "Page 2 content")
        );
        testBook = new BookData("test.pdf", pages, "Full book content with the answer");
        lenient().when(bookSession.getUserId()).thenReturn(1L);
        lenient().when(bookSession.getCurrentBookId()).thenReturn(1L);
        lenient().when(bookSession.resolveBookId(anyLong())).thenReturn(null);
        lenient().when(messageSource.getMessage(anyString(), any(), any())).thenReturn("test message");
        lenient().when(session.getAttribute("userId")).thenReturn(1L);
    }

    @Test
    void testAnswerQuestion_Success() {
        when(bookSession.getBook()).thenReturn(Optional.of(testBook));
        when(bookSession.hasBook()).thenReturn(true);
        when(bookSession.getDetectedLanguage()).thenReturn("ar");
        when(aiService.getAnswerFromAi(anyString(), anyString(), anyString())).thenReturn("إجابة السؤال");
        when(markdownService.convertMarkdownToHtml(anyString())).thenReturn("<p>إجابة السؤال</p>");
        when(textDirectionService.createFormattedResponse(anyString(), anyString())).thenReturn(formattedResponse);
        when(formattedResponse.getDirection()).thenReturn("rtl");
        when(formattedResponse.getLanguage()).thenReturn("ar");
        when(formattedResponse.getHtmlAttributes()).thenReturn("dir=\"rtl\" lang=\"ar\"");
        when(formattedResponse.getInlineStyle()).thenReturn("direction: rtl; text-align: right;");
        when(model.addAttribute(anyString(), any())).thenReturn(model);

        String view = qaController.answerQuestion("ما هو الموضوع الرئيسي؟", null, false, session, model);

        assertEquals("index", view);
        verify(aiService).getAnswerFromAi(anyString(), anyString(), eq("ar"));
        verify(model).addAttribute("qa", "<p>إجابة السؤال</p>");
        verify(model).addAttribute("hasBook", true);
        verify(model).addAttribute("bookLanguage", "ar");
    }

    @Test
    void testAnswerQuestion_EnglishOutput() {
        when(bookSession.getBook()).thenReturn(Optional.of(testBook));
        when(bookSession.hasBook()).thenReturn(true);
        when(bookSession.getDetectedLanguage()).thenReturn("ar");
        when(aiService.getAnswerFromAi(anyString(), anyString(), anyString())).thenReturn("Answer to the question");
        when(markdownService.convertMarkdownToHtml(anyString())).thenReturn("<p>Answer</p>");
        when(textDirectionService.createFormattedResponse(anyString(), anyString())).thenReturn(formattedResponse);
        when(formattedResponse.getDirection()).thenReturn("ltr");
        when(formattedResponse.getLanguage()).thenReturn("en");
        when(formattedResponse.getHtmlAttributes()).thenReturn("dir=\"ltr\" lang=\"en\"");
        when(formattedResponse.getInlineStyle()).thenReturn("direction: ltr; text-align: left;");
        when(model.addAttribute(anyString(), any())).thenReturn(model);

        String view = qaController.answerQuestion("What is the main topic?", "en", false, session, model);

        assertEquals("index", view);
        verify(aiService).getAnswerFromAi(anyString(), anyString(), eq("en"));
    }

    @Test
    void testAnswerQuestion_NoBook() {
        lenient().when(bookSession.getBook()).thenReturn(Optional.empty());
        lenient().when(bookSession.hasBook()).thenReturn(false);
        lenient().when(bookSession.getDetectedLanguage()).thenReturn("ar");
        when(model.addAttribute(anyString(), any())).thenReturn(model);

        String view = qaController.answerQuestion("Any question", null, false, session, model);

        assertEquals("index", view);
        verify(aiService, never()).getAnswerFromAi(anyString(), anyString(), anyString());
        verify(model, atLeastOnce()).addAttribute(eq("error"), anyString());
    }

    @Test
    void testAnswerQuestion_WithComplexQuestion() {
        when(bookSession.getBook()).thenReturn(Optional.of(testBook));
        when(bookSession.hasBook()).thenReturn(true);
        when(bookSession.getDetectedLanguage()).thenReturn("ar");
        String complexQuestion = "ما هي الفصول الرئيسية المذكورة في الكتاب؟ وما هي الأفكار الأساسية؟";
        when(aiService.getAnswerFromAi(anyString(), anyString(), anyString())).thenReturn("إجابة مفصلة");
        when(markdownService.convertMarkdownToHtml(anyString())).thenReturn("<p>إجابة مفصلة</p>");
        when(textDirectionService.createFormattedResponse(anyString(), anyString())).thenReturn(formattedResponse);
        when(formattedResponse.getDirection()).thenReturn("rtl");
        when(formattedResponse.getLanguage()).thenReturn("ar");
        when(formattedResponse.getHtmlAttributes()).thenReturn("dir=\"rtl\" lang=\"ar\"");
        when(formattedResponse.getInlineStyle()).thenReturn("direction: rtl; text-align: right;");
        when(model.addAttribute(anyString(), any())).thenReturn(model);

        String view = qaController.answerQuestion(complexQuestion, "ar", false, session, model);

        assertEquals("index", view);
        verify(aiService).getAnswerFromAi(anyString(), argThat(s -> s.contains(complexQuestion)), eq("ar"));
    }

    @Test
    void testAnswerQuestion_ModelAttributes() {
        when(bookSession.getBook()).thenReturn(Optional.of(testBook));
        when(bookSession.hasBook()).thenReturn(true);
        when(bookSession.getDetectedLanguage()).thenReturn("en");
        when(aiService.getAnswerFromAi(anyString(), anyString(), anyString())).thenReturn("Answer");
        when(markdownService.convertMarkdownToHtml(anyString())).thenReturn("<p>Answer</p>");
        when(textDirectionService.createFormattedResponse(anyString(), anyString())).thenReturn(formattedResponse);
        when(formattedResponse.getDirection()).thenReturn("ltr");
        when(formattedResponse.getLanguage()).thenReturn("en");
        when(formattedResponse.getHtmlAttributes()).thenReturn("dir=\"ltr\" lang=\"en\"");
        when(formattedResponse.getInlineStyle()).thenReturn("direction: ltr; text-align: left;");
        when(model.addAttribute(anyString(), any())).thenReturn(model);

        qaController.answerQuestion("Test question", "en", false, session, model);

        verify(model).addAttribute("qa", "<p>Answer</p>");
        verify(model).addAttribute("hasBook", true);
        verify(model).addAttribute("bookLanguage", "en");
    }

    @Test
    void testAnswerQuestion_CrossPlatformMode() {
        when(bookSession.getBook()).thenReturn(Optional.of(testBook));
        when(bookSession.hasBook()).thenReturn(true);
        when(bookSession.getDetectedLanguage()).thenReturn("ar");
        when(crossPlatformQaService.answerWithContext(anyString(), anyString(), anyString(), anyString())).thenReturn("إجابة عبر المنصات");
        when(markdownService.convertMarkdownToHtml(anyString())).thenReturn("<p>إجابة عبر المنصات</p>");
        when(textDirectionService.createFormattedResponse(anyString(), anyString())).thenReturn(formattedResponse);
        when(formattedResponse.getDirection()).thenReturn("rtl");
        when(formattedResponse.getLanguage()).thenReturn("ar");
        when(formattedResponse.getHtmlAttributes()).thenReturn("dir=\"rtl\" lang=\"ar\"");
        when(formattedResponse.getInlineStyle()).thenReturn("direction: rtl; text-align: right;");
        when(model.addAttribute(anyString(), any())).thenReturn(model);
        when(externalNoteService.findByUserId(anyLong())).thenReturn(Collections.emptyList());
        when(session.getAttribute("userId")).thenReturn(1L);

        String view = qaController.answerQuestion("سؤال متعدد المنصات", null, true, session, model);

        assertEquals("index", view);
        verify(crossPlatformQaService).answerWithContext(eq("سؤال متعدد المنصات"), anyString(), anyString(), eq("ar"));
        verify(aiService, never()).getAnswerFromAi(anyString(), anyString(), anyString());
    }

    @Test
    void testControllerCreation() {
        assertNotNull(qaController);
    }
}
