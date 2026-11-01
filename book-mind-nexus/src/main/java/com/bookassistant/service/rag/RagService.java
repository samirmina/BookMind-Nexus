package com.bookassistant.service.rag;

import com.bookassistant.config.AiProperties;
import com.bookassistant.entity.Book;
import com.bookassistant.entity.BookChunk;
import com.bookassistant.repository.BookChunkRepository;
import com.bookassistant.util.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * RAG service implementing the three essential components:
 * Chunking, Embedding, and Vector Database retrieval.
 *
 * <p><b>What:</b> Splits book text into chunks, generates embeddings via Ollama,
 * stores them in PostgreSQL with pgvector, and retrieves relevant chunks for queries.</p>
 *
 * <p><b>Why:</b> Provides the foundation for all AI features to retrieve relevant
 * book passages instead of sending the entire book text to the model.</p>
 *
 * <p><b>Where:</b> Used by all AI controllers (Q&A, summary, analysis, etc.).</p>
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int DEFAULT_CHUNK_OVERLAP = 100;
    private static final int DEFAULT_TOP_K = 5;
    private static final Duration EMBEDDING_TIMEOUT = Duration.ofSeconds(15);

    private final BookChunkRepository chunkRepository;
    private final AiProperties aiProperties;
    private final HttpClient httpClient;

    public RagService(BookChunkRepository chunkRepository, AiProperties aiProperties) {
        this.chunkRepository = chunkRepository;
        this.aiProperties = aiProperties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        log.info("RagService initialized | embeddingModel={}", aiProperties.getEmbeddingModel());
    }

    // --- Component 1: Chunking ---

    /**
     * Splits book text into overlapping chunks.
     *
     * @param fullText the complete book text
     * @param chunkSize target chunk size in characters
     * @param overlap overlap between chunks in characters
     * @return list of text chunks
     */
    public List<String> chunkText(String fullText, int chunkSize, int overlap) {
        if (fullText == null || fullText.isBlank()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        int length = fullText.length();

        while (start < length) {
            int end = Math.min(start + chunkSize, length);
            String chunk = fullText.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            start += (chunkSize - overlap);
            if (start >= length) break;
        }

        log.info("Chunked text | totalChars={} | chunks={} | chunkSize={} | overlap={}",
                length, chunks.size(), chunkSize, overlap);
        return chunks;
    }

    /**
     * Splits book text into overlapping chunks with default settings.
     */
    public List<String> chunkText(String fullText) {
        return chunkText(fullText, DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP);
    }

    // --- Component 2: Embedding ---

    /**
     * Generates an embedding vector for the given text using Ollama's embedding API.
     *
     * @param text the text to embed
     * @return embedding as a comma-separated string of floats
     */
    public String generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        try {
            String embeddingModel = aiProperties.getEmbeddingModel();
            String baseUrl = aiProperties.getBaseUrl();
            String embeddingsUrl = baseUrl.replace("/api/generate", "/api/embeddings");

            String requestBody = String.format("""
                {
                    "model": "%s",
                    "prompt": "%s"
                }
                """, embeddingModel, escapeJson(text));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(embeddingsUrl))
                    .timeout(EMBEDDING_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Embedding API error | status={} | body={}", response.statusCode(), response.body());
                return "";
            }

            // Parse Ollama embedding response: {"embedding": [0.1, 0.2, ...]}
            String body = response.body();
            int embeddingIdx = body.indexOf("\"embedding\"");
            if (embeddingIdx < 0) {
                log.error("No embedding field in response | body={}", body.substring(0, Math.min(200, body.length())));
                return "";
            }

            int arrayStart = body.indexOf('[', embeddingIdx);
            int arrayEnd = body.indexOf(']', arrayStart);
            if (arrayStart < 0 || arrayEnd < 0) {
                return "";
            }

            String vectorStr = body.substring(arrayStart + 1, arrayEnd);
            return vectorStr;

        } catch (Exception e) {
            log.error("Failed to generate embedding", e);
            return "";
        }
    }

    // --- Component 3: Vector Database ---

    /**
     * Indexes a book's text into the vector database.
     * Chunks the text, generates embeddings, and stores them.
     *
     * @param book the book to index
     */
    public void indexBook(Book book) {
        if (book == null || book.getFullText() == null || book.getFullText().isBlank()) {
            log.warn("Cannot index book: empty text");
            return;
        }

        long startTime = System.currentTimeMillis();
        log.info("Indexing book | bookId={} | userId={} | textLen={}", book.getId(), book.getUserId(), book.getFullText().length());

        // Delete existing chunks for this book
        chunkRepository.deleteByBookId(book.getId());

        // Chunk the text
        List<String> chunks = chunkText(book.getFullText());
        if (chunks.isEmpty()) {
            log.warn("No chunks generated for book {}", book.getId());
            return;
        }

        // Estimate page ranges (rough: divide chunks evenly across pages)
        int totalPages = book.getPageCount() != null ? book.getPageCount() : chunks.size();
        int pagesPerChunk = Math.max(1, totalPages / chunks.size());

        // Generate embeddings and save chunks
        List<BookChunk> entities = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);
            String embedding = generateEmbedding(chunkText);

            BookChunk chunk = new BookChunk();
            chunk.setBookId(book.getId());
            chunk.setUserId(book.getUserId());
            chunk.setChunkIndex(i);
            chunk.setChunkText(chunkText);
            chunk.setEmbedding(embedding);
            chunk.setPageStart(i * pagesPerChunk + 1);
            chunk.setPageEnd(Math.min((i + 1) * pagesPerChunk, totalPages));

            entities.add(chunk);

            if ((i + 1) % 10 == 0) {
                log.info("Embedding progress | bookId={} | processed={}/{}", book.getId(), i + 1, chunks.size());
            }
        }

        chunkRepository.saveAll(entities);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Book indexed | bookId={} | chunks={} | duration={}ms", book.getId(), entities.size(), duration);
    }

    /**
     * Retrieves the top-k most relevant chunks for a query.
     *
     * @param bookId the book to search in
     * @param query the user's query
     * @param topK number of chunks to retrieve
     * @return list of relevant chunks ordered by similarity
     */
    public List<BookChunk> retrieveRelevantChunks(Long bookId, String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String queryEmbedding = generateEmbedding(query);
        if (queryEmbedding.isEmpty()) {
            log.warn("Failed to generate embedding for query");
            return List.of();
        }

        List<BookChunk> chunks = chunkRepository.findTopKByBookIdOrderByCosineSimilarity(
                bookId, queryEmbedding, topK);

        log.info("Retrieved {} chunks for query | bookId={}", chunks.size(), bookId);
        return chunks;
    }

    /**
     * Retrieves the top-k most relevant chunks across all books for a user.
     *
     * @param userId the user whose books to search
     * @param query the user's query
     * @param topK number of chunks to retrieve
     * @return list of relevant chunks ordered by similarity
     */
    public List<BookChunk> retrieveRelevantChunksForUser(Long userId, String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String queryEmbedding = generateEmbedding(query);
        if (queryEmbedding.isEmpty()) {
            log.warn("Failed to generate embedding for query");
            return List.of();
        }

        List<BookChunk> chunks = chunkRepository.findTopKByUserIdOrderByCosineSimilarity(
                userId, queryEmbedding, topK);

        log.info("Retrieved {} chunks for user | userId={}", chunks.size(), userId);
        return chunks;
    }

    /**
     * Retrieves relevant chunks and formats them as context for the AI model.
     *
     * @param bookId the book to search
     * @param query the user's query
     * @return formatted context string with relevant passages
     */
    public String getRagContext(Long bookId, String query) {
        List<BookChunk> chunks = retrieveRelevantChunks(bookId, query, DEFAULT_TOP_K);
        if (chunks.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        context.append("=== RELEVANT PASSAGES ===\n\n");

        for (int i = 0; i < chunks.size(); i++) {
            BookChunk chunk = chunks.get(i);
            context.append(String.format("[Passage %d - Pages %d-%d]\n%s\n\n",
                    i + 1,
                    chunk.getPageStart() != null ? chunk.getPageStart() : "?",
                    chunk.getPageEnd() != null ? chunk.getPageEnd() : "?",
                    chunk.getChunkText()));
        }

        return context.toString();
    }

    /**
     * Deletes all chunks for a book.
     */
    public void deleteBookChunks(Long bookId) {
        chunkRepository.deleteByBookId(bookId);
        log.info("Deleted chunks for book | bookId={}", bookId);
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
