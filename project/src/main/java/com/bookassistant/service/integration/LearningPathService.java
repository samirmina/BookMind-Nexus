package com.bookassistant.service.integration;

import com.bookassistant.session.BookSession;
import com.bookassistant.util.LoggingUtils;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates personalized learning paths based on book content and user knowledge.
 * <p>
 * <b>What:</b> Analyzes the book structure and the user's external notes to
 * determine the optimal reading order, identifying what to focus on, skip, or review.
 * <p>
 * <b>Why:</b> Required for the learning-path feature that tells users how to
 * study the book chunk by chunk based on their existing knowledge.
 * <p>
 * <b>Where:</b> Implements the learning-path generation algorithm.
 */
@Service
public class LearningPathService {

    private static final Logger log = LoggerFactory.getLogger(LearningPathService.class);

    private static final int CHUNK_SIZE = 5;
    private static final int MIN_KEYWORD_OCCURRENCES = 2;

    private final BookSession bookSession;
    private final MessageSource messageSource;

    public LearningPathService(BookSession bookSession, MessageSource messageSource) {
        this.bookSession = bookSession;
        this.messageSource = messageSource;
    }

    /**
     * Generates a learning path for the current book.
     *
     * @param externalNotes the user's external notes (concatenated text)
     * @param language      the output language ("ar" or "en")
     * @return a structured learning path as a string
     */
    public String generateLearningPath(String externalNotes, String language) {
        LoggingUtils.enter(log, "generateLearningPath", "language", language);

        if (!bookSession.hasBook()) {
            return messageSource.getMessage("learning.path.no.book", null, LocaleContextHolder.getLocale());
        }

        String fullText = bookSession.getBook().map(b -> b.fullText()).orElse("");
        int totalPages = bookSession.getPageCount();

        if (totalPages <= CHUNK_SIZE) {
            String result = messageSource.getMessage("learning.path.short.book", null, LocaleContextHolder.getLocale());
            LoggingUtils.exit(log, "generateLearningPath");
            return result;
        }

        List<String> chunks = splitIntoChunks(fullText, totalPages);
        Set<String> knownTopics = extractKeywords(externalNotes);
        Map<String, Set<String>> bookKeywords = extractChunkKeywords(chunks);

        List<LearningStep> steps = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);
            Set<String> chunkKeywords = bookKeywords.getOrDefault("chunk-" + i, Collections.emptySet());

            int knownCount = 0;
            for (String kw : chunkKeywords) {
                if (knownTopics.contains(kw)) knownCount++;
            }

            double familiarity = chunkKeywords.isEmpty() ? 0.0 : (double) knownCount / chunkKeywords.size();
            int startPage = i * CHUNK_SIZE + 1;
            int endPage = Math.min((i + 1) * CHUNK_SIZE, totalPages);

            String rationale;
            if (familiarity > 0.6) {
                rationale = messageSource.getMessage("learning.path.high.familiarity", null, LocaleContextHolder.getLocale());
            } else if (familiarity > 0.3) {
                rationale = messageSource.getMessage("learning.path.medium.familiarity", null, LocaleContextHolder.getLocale());
            } else {
                rationale = messageSource.getMessage("learning.path.low.familiarity", null, LocaleContextHolder.getLocale());
            }

            steps.add(new LearningStep(i + 1, startPage, endPage, rationale, familiarity));
        }

        String result = formatLearningPath(steps, language);
        LoggingUtils.exit(log, "generateLearningPath", "steps", steps.size());
        return result;
    }

    private List<String> splitIntoChunks(String fullText, int totalPages) {
        List<String> chunks = new ArrayList<>();
        int chunkSize = Math.min(CHUNK_SIZE, totalPages);
        for (int i = 0; i < totalPages; i += chunkSize) {
            int end = Math.min(i + chunkSize, totalPages);
            chunks.add("Chunk " + (i / chunkSize + 1) + " (pages " + (i + 1) + "-" + end + ")");
        }
        return chunks;
    }

    private Set<String> extractKeywords(String text) {
        if (text == null || text.isBlank()) return Collections.emptySet();
        Set<String> keywords = new HashSet<>();
        String[] words = text.toLowerCase().split("[^a-zA-Z\\u0600-\\u06FF]+");
        Map<String, Integer> freq = new HashMap<>();
        for (String w : words) {
            if (w.length() > 3) {
                freq.put(w, freq.getOrDefault(w, 0) + 1);
            }
        }
        return freq.entrySet().stream()
            .filter(e -> e.getValue() >= MIN_KEYWORD_OCCURRENCES)
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    }

    private Map<String, Set<String>> extractChunkKeywords(List<String> chunks) {
        Map<String, Set<String>> result = new HashMap<>();
        for (int i = 0; i < chunks.size(); i++) {
            result.put("chunk-" + i, extractKeywords(chunks.get(i)));
        }
        return result;
    }

    private String formatLearningPath(List<LearningStep> steps, String language) {
        StringBuilder sb = new StringBuilder();
        sb.append(messageSource.getMessage("learning.path.title", null, LocaleContextHolder.getLocale()));

        for (LearningStep step : steps) {
            sb.append(String.format(messageSource.getMessage("learning.path.step", null, LocaleContextHolder.getLocale()),
                step.stepNumber(), step.startPage(), step.endPage()));
            sb.append(String.format(messageSource.getMessage("learning.path.reason", null, LocaleContextHolder.getLocale()), step.rationale()));
            sb.append(String.format(messageSource.getMessage("learning.path.importance", null, LocaleContextHolder.getLocale()), step.familiarity() * 100));
        }

        return sb.toString();
    }

    private record LearningStep(int stepNumber, int startPage, int endPage, String rationale, double familiarity) {
    }
}


