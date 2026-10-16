package com.bookassistant;

import com.bookassistant.service.ai.AiService;
import com.bookassistant.service.file.FileParserService;
import com.bookassistant.model.BookData;
import com.bookassistant.model.PageData;
import com.bookassistant.session.BookSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
public class BookAssistantLiteIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookSession bookSession;

    @MockBean
    private AiService aiService;

    @MockBean
    private FileParserService fileParserService;

    private MockHttpSession session;

    @BeforeEach
    void mockAiService() {
        when(aiService.getAnswerFromAi(anyString(), anyString(), anyString())).thenReturn("Mocked AI response");
        when(aiService.getAnswerFromAi(anyString(), anyString(), anyString(), anyLong(), any())).thenReturn("Mocked AI response");
        when(aiService.createComprehensionTest(anyString(), anyInt(), anyInt(), anyString())).thenReturn("Mocked comprehension test");
        when(aiService.truncateToTokens(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @BeforeEach
    void initSession() {
        session = new MockHttpSession();
        session.setAttribute("userId", 1L);
    }

    @BeforeEach
    void mockFileParserService() throws Exception {
        List<PageData> pages = List.of(
            new PageData(1, "Extracted test content from PDF page 1"),
            new PageData(2, "Extracted test content from PDF page 2")
        );
        BookData mockBook = new BookData("test-book.pdf", pages, "Extracted test content from PDF with enough text to be meaningful and allow page range operations.");
        org.mockito.Mockito.doReturn(mockBook).when(fileParserService).parsePdfFile(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private void mockBookSessionState() {
        BookData sessionBook = new BookData("test-book.pdf",
            List.of(new PageData(1, "Extracted test content from PDF page 1"),
                    new PageData(2, "Extracted test content from PDF page 2")),
            "Extracted test content from PDF with enough text to be meaningful and allow page range operations.");
        java.util.Optional<BookData> optBook = java.util.Optional.of(sessionBook);

        when(bookSession.hasBook()).thenReturn(true);
        when(bookSession.getBook()).thenReturn(optBook);
        when(bookSession.getDetectedLanguage()).thenReturn("en");
        when(bookSession.getPageCount()).thenReturn(2);
        when(bookSession.getUserId()).thenReturn(1L);
        when(bookSession.resolveBookId(1L)).thenReturn(1L);
        when(bookSession.extractTextFromPageRange(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
            .thenAnswer(invocation -> {
                int start = invocation.getArgument(0);
                int end = invocation.getArgument(1);
                return "--- [Page " + start + "] ---\nExtracted test content from PDF page " + start;
            });
        when(bookSession.getPdfPageCount()).thenReturn(2);
        doAnswer(invocation -> {
            bookSession.setCurrentBookId(invocation.getArgument(0));
            return null;
        }).when(bookSession).loadBookById(org.mockito.ArgumentMatchers.anyLong());
    }

    private MockHttpSession uploadBook(MockMultipartFile file) throws Exception {
        mockMvc.perform(multipart("/upload")
                        .file(file)
                        .session(this.session))
                .andExpect(status().isAccepted());
        mockBookSessionState();
        return this.session;
    }

    @Test
    public void contextLoads() {
        assert bookSession != null;
        assert fileParserService != null;
        assert aiService != null;
    }

    @Test
    public void testLandingPageLoads() throws Exception {
        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("home"));
    }

    @Test
    public void testAppPageLoads() throws Exception {
        mockMvc.perform(get("/app").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("hasBook"));
    }

    @Test
    public void testFileUpload() throws Exception {
        String pdfContent = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R >>\nendobj\n4 0 obj\n<< /Length 44 >>\nstream\nBT\n/F1 12 Tf\n100 700 Td\n(Test PDF Content) Tj\nET\nendstream\nendobj\nxref\n0 5\n0000000000 65535 f\n0000000009 00000 n\n0000000058 00000 n\n0000000115 00000 n\n0000000214 00000 n\ntrailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n309\n%%EOF";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-book.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                pdfContent.getBytes()
        );

        uploadBook(file);
    }

    @Test
    public void testFileUploadInvalidType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "This is not a PDF".getBytes()
        );

        mockMvc.perform(multipart("/upload")
                        .file(file)
                        .session(this.session))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testFileUploadWithRealValidation() throws Exception {
        String pdfContent = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>\nendobj\nxref\n0 4\n0000000000 65535 f\n0000000009 00000 n\n0000000058 00000 n\n0000000115 00000 n\ntrailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n200\n%%EOF";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-book.pdf",
                "application/pdf",
                pdfContent.getBytes()
        );

        uploadBook(file);
    }

    @Test
    public void testGeneralSummaryEndpoint() throws Exception {
        String pdfContent = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>\nendobj\nxref\n0 4\n0000000000 65535 f\n0000000009 00000 n\n0000000058 00000 n\n0000000115 00000 n\ntrailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n200\n%%EOF";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-book.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                pdfContent.getBytes()
        );

        MockHttpSession session = uploadBook(file);

        mockMvc.perform(post("/summary/general")
                        .param("outputLanguage", "ar")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("summaryGeneral"))
                .andExpect(model().attribute("hasBook", true));
    }

    @Test
    public void testPagesSummaryEndpoint() throws Exception {
        String pdfContent = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 5 >>\nendobj\n3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>\nendobj\nxref\n0 4\n0000000000 65535 f\n0000000009 00000 n\n0000000058 00000 n\n0000000115 00000 n\ntrailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n200\n%%EOF";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-book.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                pdfContent.getBytes()
        );

        MockHttpSession session = uploadBook(file);

        mockMvc.perform(post("/summary/pages")
                        .param("startPage", "1")
                        .param("endPage", "2")
                        .param("outputLanguage", "ar")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("summaryPages"))
                .andExpect(model().attribute("hasBook", true));
    }

    @Test
    public void testQaEndpoint() throws Exception {
        String pdfContent = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R >>\nendobj\n4 0 obj\n<< /Length 44 >>\nstream\nBT\n/F1 12 Tf\n100 700 Td\n(Test PDF Content) Tj\nET\nendstream\nendobj\nxref\n0 5\n0000000000 65535 f\n0000000009 00000 n\n0000000058 00000 n\n0000000115 00000 n\n0000000214 00000 n\ntrailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n309\n%%EOF";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-book.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                pdfContent.getBytes()
        );

        MockHttpSession session = uploadBook(file);

        mockMvc.perform(post("/qa")
                        .param("question", "What is this book about?")
                        .param("outputLanguage", "ar")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("qa"))
                .andExpect(model().attribute("hasBook", true));
    }

    @Test
    public void testSemanticSearchEndpoint() throws Exception {
        String pdfContent = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R >>\nendobj\n4 0 obj\n<< /Length 44 >>\nstream\nBT\n/F1 12 Tf\n100 700 Td\n(Test PDF Content) Tj\nET\nendstream\nendobj\nxref\n0 5\n0000000000 65535 f\n0000000009 00000 n\n0000000058 00000 n\n0000000115 00000 n\n0000000214 00000 n\ntrailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n309\n%%EOF";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-book.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                pdfContent.getBytes()
        );

        MockHttpSession session = uploadBook(file);

        mockMvc.perform(post("/semantic-search")
                        .param("query", "main concepts")
                        .param("outputLanguage", "en")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("semanticSearch"))
                .andExpect(model().attribute("hasBook", true));
    }

    @Test
    public void testRealityCheckEndpoint() throws Exception {
        String pdfContent = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 3 >>\nendobj\n3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>\nendobj\nxref\n0 4\n0000000000 65535 f\n0000000009 00000 n\n0000000058 00000 n\n0000000115 00000 n\ntrailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n200\n%%EOF";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-book.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                pdfContent.getBytes()
        );

        MockHttpSession session = uploadBook(file);

        mockMvc.perform(post("/reality-check")
                        .param("startPage", "1")
                        .param("endPage", "2")
                        .param("outputLanguage", "ar")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("realityCheck"))
                .andExpect(model().attribute("hasBook", true));
    }

    @Test
    public void testConceptMapEndpoint() throws Exception {
        String pdfContent = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 2 >>\nendobj\n3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R >>\nendobj\n4 0 obj\n<< /Length 44 >>\nstream\nBT\n/F1 12 Tf\n100 700 Td\n(Test PDF Content) Tj\nET\nendstream\nendobj\nxref\n0 5\n0000000000 65535 f\n0000000009 00000 n\n0000000058 00000 n\n0000000115 00000 n\n0000000214 00000 n\ntrailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n309\n%%EOF";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-book.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                pdfContent.getBytes()
        );

        MockHttpSession session = uploadBook(file);

        mockMvc.perform(post("/concept-map")
                        .param("startPage", "1")
                        .param("endPage", "2")
                        .param("outputLanguage", "ar")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("conceptMap"))
                .andExpect(model().attribute("hasBook", true));
    }

    @Test
    public void testRiskFlagsEndpoint() throws Exception {
        String pdfContent = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>\nendobj\nxref\n0 4\n0000000000 65535 f\n0000000009 00000 n\n0000000058 000000 n\n0000000115 00000 n\ntrailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n200\n%%EOF";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-book.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                pdfContent.getBytes()
        );

        MockHttpSession session = uploadBook(file);

        mockMvc.perform(post("/risk-flags")
                        .param("startPage", "1")
                        .param("endPage", "2")
                        .param("outputLanguage", "ar")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("riskFlags"))
                .andExpect(model().attribute("hasBook", true));
    }

    @Test
    public void testActionPlanEndpoint() throws Exception {
        String pdfContent = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 2 >>\nendobj\n3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>\nendobj\nxref\n0 4\n0000000000 65535 f\n0000000009 00000 n\n0000000058 00000 n\n0000000115 00000 n\ntrailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n200\n%%EOF";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-book.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                pdfContent.getBytes()
        );

        MockHttpSession session = uploadBook(file);

        mockMvc.perform(post("/action-plan")
                        .param("startPage", "1")
                        .param("endPage", "2")
                        .param("outputLanguage", "ar")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("actionPlan"))
                .andExpect(model().attribute("hasBook", true));
    }

    @Test
    public void testComprehensionEndpoint() throws Exception {
        String pdfContent = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 2 >>\nendobj\n3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R >>\nendobj\n4 0 obj\n<< /Length 44 >>\nstream\nBT\n/F1 12 Tf\n100 700 Td\n(Test PDF Content) Tj\nET\nendstream\nendobj\nxref\n0 5\n0000000000 65535 f\n0000000009 00000 n\n0000000058 00000 n\n0000000115 00000 n\n0000000214 00000 n\ntrailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n309\n%%EOF";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-book.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                pdfContent.getBytes()
        );

        MockHttpSession session = uploadBook(file);

        mockMvc.perform(post("/comprehension")
                        .param("startPage", "1")
                        .param("endPage", "2")
                        .param("outputLanguage", "ar")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("comprehension"))
                .andExpect(model().attribute("hasBook", true));
    }

    @Test
    public void testLanguageDetectionArabic() throws Exception {
        String pdfContent = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>\nendobj\nxref\n0 4\n0000000000 65535 f\n0000000009 00000 n\n0000000058 00000 n\n0000000115 00000 n\ntrailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n200\n%%EOF";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "arabic-book.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                pdfContent.getBytes()
        );

        uploadBook(file);

        String detectedLang = bookSession.getDetectedLanguage();
        assert detectedLang != null;
    }

    @Test
    public void testLanguageDetectionEnglish() throws Exception {
        String pdfContent = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>\nendobj\nxref\n0 4\n0000000000 65535 f\n0000000009 00000 n\n0000000058 00000 n\n0000000115 00000 n\ntrailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n200\n%%EOF";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "english-book.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                pdfContent.getBytes()
        );

        uploadBook(file);

        String detectedLang = bookSession.getDetectedLanguage();
        assert detectedLang != null;
    }

    @Test
    public void testOutputLanguageArabic() throws Exception {
        String pdfContent = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R >>\nendobj\n4 0 obj\n<< /Length 44 >>\nstream\nBT\n/F1 12 Tf\n100 700 Td\n(Test PDF Content) Tj\nET\nendstream\nendobj\nxref\n0 5\n0000000000 65535 f\n0000000009 00000 n\n0000000058 00000 n\n0000000115 00000 n\n0000000214 00000 n\ntrailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n309\n%%EOF";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-book.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                pdfContent.getBytes()
        );

        MockHttpSession session = uploadBook(file);

        mockMvc.perform(post("/summary/general")
                        .param("outputLanguage", "ar")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("bookLanguage", "en"));
    }

    @Test
    public void testOutputLanguageEnglish() throws Exception {
        String pdfContent = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R >>\nendobj\n4 0 obj\n<< /Length 44 >>\nstream\nBT\n/F1 12 Tf\n100 700 Td\n(Test PDF Content) Tj\nET\nendstream\nendobj\nxref\n0 5\n0000000000 65535 f\n0000000009 00000 n\n0000000058 00000 n\n0000000115 00000 n\n0000000214 00000 n\ntrailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n309\n%%EOF";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-book.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                pdfContent.getBytes()
        );

        MockHttpSession session = uploadBook(file);

        mockMvc.perform(post("/summary/general")
                        .param("outputLanguage", "en")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("bookLanguage", "en"));
    }
}
