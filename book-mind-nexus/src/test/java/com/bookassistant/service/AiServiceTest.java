package com.bookassistant.service;

import com.bookassistant.config.AiProperties;
import com.bookassistant.service.ai.AiService;
import com.bookassistant.service.rag.RagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private AiProperties aiProperties;

    @Mock
    private RagService ragService;

    @InjectMocks
    private AiService aiService;

    @Test
    void testServiceCreation() {
        assertNotNull(aiService);
    }

    @Test
    void testGetAnswerFromAiMethodExists() throws NoSuchMethodException {
        Method method = AiService.class.getMethod("getAnswerFromAi", String.class, String.class, String.class);
        assertNotNull(method);
    }

    @Test
    void testGetAnswerFromAiWithLanguageMethodExists() throws NoSuchMethodException {
        Method method = AiService.class.getMethod("getAnswerFromAi", String.class, String.class);
        assertNotNull(method);
    }

    @Test
    void testCreateComprehensionTestMethodExists() throws NoSuchMethodException {
        Method method = AiService.class.getMethod("createComprehensionTest", String.class, int.class, int.class, String.class);
        assertNotNull(method);
    }

    @Test
    void testCreateComprehensionTestWithLanguageMethodExists() throws NoSuchMethodException {
        Method method = AiService.class.getMethod("createComprehensionTest", String.class, int.class, int.class);
        assertNotNull(method);
    }
}
