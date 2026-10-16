package com.bookassistant.service.integration;

import com.bookassistant.session.BookSession;
import com.bookassistant.model.BookData;
import com.bookassistant.model.PageData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LearningPathServiceTest {

@Mock
    private BookSession bookSession;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private LearningPathService learningPathService;

    private BookData testBook;

@BeforeEach
    void setUp() {
        List<PageData> pages = List.of(
            new PageData(1, "Introduction to machine learning basics"),
            new PageData(2, "Supervised learning algorithms"),
            new PageData(3, "Neural networks and deep learning"),
            new PageData(4, "Natural language processing"),
            new PageData(5, "Computer vision applications"),
            new PageData(6, "Reinforcement learning"),
            new PageData(7, "Machine learning deployment"),
            new PageData(8, "Model evaluation metrics"),
            new PageData(9, "Feature engineering techniques"),
            new PageData(10, "Ethics in AI")
        );
        testBook = new BookData("ml-guide.pdf", pages, pages.stream().map(PageData::text).reduce("", String::concat));

        lenient().when(messageSource.getMessage(anyString(), isNull(), any())).thenReturn("default");
    }

    @Test
    void testGenerateLearningPath_NoBook() {
        when(messageSource.getMessage(eq("learning.path.no.book"), isNull(), any())).thenReturn("لا يوجد كتاب محمل");
        String result = learningPathService.generateLearningPath("", "ar");
        assertEquals("لا يوجد كتاب محمل", result);
    }

    @Test
    void testGenerateLearningPath_ShortBook() {
        List<PageData> pages = List.of(new PageData(1, "Short book content"));
        BookData shortBook = new BookData("short.pdf", pages, "Short book content");
        when(bookSession.getBook()).thenReturn(Optional.of(shortBook));
        when(bookSession.hasBook()).thenReturn(true);
        when(bookSession.getPageCount()).thenReturn(1);
        when(messageSource.getMessage(eq("learning.path.short.book"), isNull(), any())).thenReturn("الكتاب قصير. اقرأه بالكامل من البداية إلى النهاية.");

        String result = learningPathService.generateLearningPath("", "ar");
        assertEquals("الكتاب قصير. اقرأه بالكامل من البداية إلى النهاية.", result);
    }

    @Test
    void testGenerateLearningPath_WithExternalNotes() {
        when(bookSession.getBook()).thenReturn(Optional.of(testBook));
        when(bookSession.hasBook()).thenReturn(true);
        when(bookSession.getPageCount()).thenReturn(10);
        when(messageSource.getMessage(eq("learning.path.title"), isNull(), any())).thenReturn("Learning Path");
        when(messageSource.getMessage(eq("learning.path.step"), isNull(), any())).thenReturn("Step %d: pages %d-%d");
        when(messageSource.getMessage(eq("learning.path.reason"), isNull(), any())).thenReturn("Reason: %s");
        when(messageSource.getMessage(eq("learning.path.importance"), isNull(), any())).thenReturn(" (%.0f%%)");

        String externalNotes = "I already know machine learning basics and neural networks and deep learning basics";

        String result = learningPathService.generateLearningPath(externalNotes, "en");

        assertNotNull(result);
        assertTrue(result.contains("Learning Path"));
        assertTrue(result.contains("Step"));
    }

    @Test
    void testGenerateLearningPath_Arabic() {
        when(bookSession.getBook()).thenReturn(Optional.of(testBook));
        when(bookSession.hasBook()).thenReturn(true);
        when(bookSession.getPageCount()).thenReturn(10);
        when(messageSource.getMessage(eq("learning.path.title"), isNull(), any())).thenReturn("خطة التعلم");
        when(messageSource.getMessage(eq("learning.path.step"), isNull(), any())).thenReturn("الخطوة %d: الصفحات %d-%d");
        when(messageSource.getMessage(eq("learning.path.reason"), isNull(), any())).thenReturn("السبب: %s");
        when(messageSource.getMessage(eq("learning.path.importance"), isNull(), any())).thenReturn(" (%.0f%%)");

        String result = learningPathService.generateLearningPath("", "ar");

        assertNotNull(result);
        assertTrue(result.contains("خطة التعلم"));
        assertTrue(result.contains("الخطوة"));
    }
}


