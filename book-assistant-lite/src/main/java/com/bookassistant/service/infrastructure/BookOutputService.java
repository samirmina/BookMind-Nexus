package com.bookassistant.service.infrastructure;

import com.bookassistant.entity.BookOutput;
import com.bookassistant.repository.BookOutputRepository;
import com.bookassistant.util.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Logs AI-generated outputs to the database per book and tab.
 * <p>
 * <b>What:</b> Persists every AI interaction with metadata about which tab
 * produced it, which linked account was used, and the full output text.
 * <p>
 * <b>Why:</b> Required by the database organization rules to maintain a complete
 * history of AI outputs scoped to each user's books.
 * <p>
 * <b>Where:</b> Implements the output-logging service for all study tabs.
 */
@Service
public class BookOutputService {

    private static final Logger log = LoggerFactory.getLogger(BookOutputService.class);

    private final BookOutputRepository bookOutputRepository;

    public BookOutputService(BookOutputRepository bookOutputRepository) {
        this.bookOutputRepository = bookOutputRepository;
    }

    /**
     * Saves an AI output entry.
     *
     * @param userId          the user ID
     * @param bookId          the book ID
     * @param tabUsed         the tab that produced the output (qa, summary, etc.)
     * @param outputText      the AI-generated text
     * @param linkedAccountId optional linked account ID
     * @param linkedPlatform  optional linked platform name
     */
    public void saveOutput(Long userId, Long bookId, String tabUsed, String outputText,
                           Long linkedAccountId, String linkedPlatform) {
        LoggingUtils.enter(log, "saveOutput", "tab", tabUsed, "bookId", bookId);

        if (userId == null || bookId == null || tabUsed == null || outputText == null) {
            log.warn("Cannot save output: missing required fields");
            return;
        }

        outputText = outputText.replace("\0", "");

        BookOutput output = new BookOutput();
        output.setUserId(userId);
        output.setBookId(bookId);
        output.setTabUsed(tabUsed);
        output.setOutputText(outputText);
        output.setLinkedAccountId(linkedAccountId);
        output.setLinkedPlatform(linkedPlatform);

        bookOutputRepository.save(output);
        LoggingUtils.exit(log, "saveOutput", "id", output.getId());
    }

    public void deleteOutput(Long outputId, Long userId) {
        LoggingUtils.enter(log, "deleteOutput", "id", outputId, "userId", userId);

        if (outputId == null || userId == null) {
            log.warn("Cannot delete output: missing required fields");
            return;
        }

        com.bookassistant.entity.BookOutput output = bookOutputRepository.findById(outputId).orElse(null);
        if (output == null) {
            log.warn("Output not found for deletion: id={}", outputId);
            return;
        }

        if (!output.getUserId().equals(userId)) {
            log.warn("User {} attempted to delete output {} belonging to another user", userId, outputId);
            return;
        }

        bookOutputRepository.deleteById(outputId);
        LoggingUtils.exit(log, "deleteOutput", "id", outputId);
    }

    public List<BookOutput> findByUserId(Long userId) {
        if (userId == null) return List.of();
        return bookOutputRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<BookOutput> findByUserIdAndTabUsed(Long userId, String tabUsed) {
        if (userId == null || tabUsed == null) return List.of();
        return bookOutputRepository.findByUserIdAndTabUsedOrderByCreatedAtDesc(userId, tabUsed);
    }
}
