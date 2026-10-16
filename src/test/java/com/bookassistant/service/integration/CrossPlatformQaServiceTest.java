package com.bookassistant.service.integration;

import com.bookassistant.entity.ExternalNote;
import com.bookassistant.entity.LinkedAccount;
import com.bookassistant.repository.ExternalNoteRepository;
import com.bookassistant.repository.LinkedAccountRepository;
import com.bookassistant.service.ai.AiService;
import com.bookassistant.session.BookSession;
import com.bookassistant.model.BookData;
import com.bookassistant.model.PageData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class CrossPlatformQaServiceTest {

    @Mock
    private AiService aiService;

    @InjectMocks
    private CrossPlatformQaService crossPlatformQaService;

    @Test
    void testAnswerWithContext_IncludesBookAndExternalNotes() {
        when(aiService.truncateToTokens(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(0));
        when(aiService.getAnswerFromAi(anyString(), anyString(), eq("ar"))).thenReturn("إجابة");

        String result = crossPlatformQaService.answerWithContext(
            "ما هو الذكاء الاصطناعي؟",
            "Book content about AI",
            "User notes about AI basics",
            "ar"
        );

        assertEquals("إجابة", result);
        verify(aiService).getAnswerFromAi(argThat(prompt -> 
            prompt.contains("=== LINK STATUS ===") &&
            prompt.contains("External notes linked: YES") &&
            prompt.contains("User notes about AI basics") &&
            prompt.contains("=== BOOK CONTENT ===") &&
            prompt.contains("Book content about AI") &&
            prompt.contains("=== QUESTION ===") &&
            prompt.contains("ما هو الذكاء الاصطناعي؟") &&
            prompt.contains("=== MANDATORY OUTPUT STRUCTURE ===") &&
            prompt.contains("--- CORE CONTENT ---") &&
            prompt.contains("--- KEY INSIGHTS ---") &&
            prompt.contains("--- PART C: NEW COVERAGE ---") &&
            prompt.contains("--- COMPLEXITY ASSESSMENT ---") &&
            prompt.contains("--- GAPS ---") &&
            prompt.contains("--- NOTABLE PASSAGES ---")
        ), anyString(), eq("ar"));
    }

    @Test
    void testAnswerWithContext_WithoutExternalNotes() {
        when(aiService.truncateToTokens(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(0));
        when(aiService.getAnswerFromAi(anyString(), anyString(), eq("en"))).thenReturn("Answer");

        String result = crossPlatformQaService.answerWithContext(
            "What is AI?",
            "Book content about AI",
            "",
            "en"
        );

        assertEquals("Answer", result);
        verify(aiService).getAnswerFromAi(argThat(prompt -> 
            prompt.contains("Linked notes: NO") &&
            prompt.contains("=== BOOK CONTENT ===") &&
            prompt.contains("Book content about AI") &&
            prompt.contains("--- COMPLEXITY ASSESSMENT ---") &&
            prompt.contains("--- SUGGESTED NOTES ---")
        ), anyString(), eq("en"));
    }
}


