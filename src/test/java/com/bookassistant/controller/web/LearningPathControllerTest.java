package com.bookassistant.controller.web;

import com.bookassistant.service.infrastructure.ExternalNoteService;
import com.bookassistant.service.integration.LearningPathService;
import com.bookassistant.service.infrastructure.BookOutputService;
import com.bookassistant.session.BookSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.Model;

import jakarta.servlet.http.HttpSession;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LearningPathControllerTest {

    @Mock
    private LearningPathService learningPathService;

    @Mock
    private ExternalNoteService externalNoteService;

    @Mock
    private BookOutputService bookOutputService;

    @Mock
    private BookSession bookSession;

    @Mock
    private Model model;

    @Mock
    private HttpSession session;

    @InjectMocks
    private LearningPathController learningPathController;

    @Test
    void testGenerateLearningPath() {
        when(session.getAttribute("userId")).thenReturn(1L);
        lenient().when(bookSession.getUserId()).thenReturn(1L);
        lenient().when(bookSession.getCurrentBookId()).thenReturn(1L);
        lenient().when(bookSession.getDetectedLanguage()).thenReturn("en");
        when(externalNoteService.findByUserId(1L)).thenReturn(Collections.emptyList());
        when(learningPathService.generateLearningPath(anyString(), eq("en"))).thenReturn("خطة التعلم");

        String view = learningPathController.generateLearningPath(null, session, model);

        assertEquals("index", view);
        verify(learningPathService).generateLearningPath(eq(""), eq("en"));
        verify(model).addAttribute("learningPath", "خطة التعلم");
        verify(model).addAttribute("hasBook", true);
        verify(model).addAttribute("bookLanguage", "en");
    }
}
