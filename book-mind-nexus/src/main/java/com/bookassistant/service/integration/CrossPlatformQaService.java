package com.bookassistant.service.integration;

import com.bookassistant.service.ai.AiService;
import com.bookassistant.util.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Answers questions using both book content and external notes context.
 * <p>
 * <b>What:</b> Builds an enriched prompt combining the current book text and
 * synced external notes, then queries the AI model.
 * <p>
 * <b>Why:</b> Required for the cross-platform Q&A feature that understands
 * what the user already knows from Notion and how it relates
 * to the current book.
 * <p>
 * <b>Where:</b> Implements the cross-platform question-answering service.
 */
@Service
public class CrossPlatformQaService {

    private static final Logger log = LoggerFactory.getLogger(CrossPlatformQaService.class);

    private final AiService aiService;

    public CrossPlatformQaService(AiService aiService) {
        this.aiService = aiService;
    }

    /**
     * Answers a question using combined book and external context.
     *
     * @param question      the user's question
     * @param bookContext   the relevant book text
     * @param externalNotes the external notes context
     * @param language      the output language ("ar" or "en")
     * @return the AI-generated answer
     */
    private static final int MAX_BOOK_TOKENS = 8000;
    private static final int MAX_NOTES_TOKENS = 4000;
    private static final int MAX_NOTE_ITEMS = 50;

    public String answerWithContext(String question, String bookContext, String externalNotes, String language) {
        LoggingUtils.enter(log, "answerWithContext", "language", language, "notesLen", externalNotes == null ? 0 : externalNotes.length());

        boolean hasNotes = externalNotes != null && !externalNotes.isBlank();
        int notesCount = hasNotes ? externalNotes.split("\n").length : 0;

        String truncatedBook = aiService.truncateToTokens(bookContext, MAX_BOOK_TOKENS);
        String truncatedNotes = aiService.truncateToTokens(externalNotes, MAX_NOTES_TOKENS);

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a rigorous book analyst. Your job is to compare the user's Notion notes against the book content and produce a structured, evidence-based breakdown.\n\n");

        prompt.append("=== LINK STATUS ===\n");
        prompt.append("External notes linked: ").append(hasNotes ? "YES" : "NO").append("\n");
        if (hasNotes) {
            prompt.append("Notes loaded: ").append(notesCount).append(" entries\n");
            prompt.append("Notes source: Notion (parsed from linked account)\n");
            prompt.append("Notes content:\n").append(truncatedNotes).append("\n\n");
        } else {
            prompt.append("No external notes available. Answer using book content only.\n\n");
        }

        prompt.append("=== BOOK CONTENT ===\n");
        prompt.append("Book text:\n");
        prompt.append(truncatedBook).append("\n\n");

        prompt.append("=== QUESTION ===\n");
        prompt.append(question).append("\n\n");

        prompt.append("=== MANDATORY OUTPUT STRUCTURE ===\n");
        prompt.append("You MUST follow this exact format. Do not write paragraphs. Use bullet points, short labels, and direct references.\n\n");

        if (hasNotes) {
            prompt.append("HEADER: Linked notes: YES (X entries parsed from Notion)\n\n");
            
            prompt.append("--- CORE CONTENT ---\n");
            prompt.append("For each matched concept from the notes:\n");
            prompt.append("- What the notes say: [specific excerpt]\n");
            prompt.append("- What the book adds: [specific passage, page reference if possible, expanded explanation]\n");
            prompt.append("- Gap closed: [what new understanding the book provides]\n");
            prompt.append("Be specific. Quote both notes and book. Do not generalize.\n\n");
            
            prompt.append("--- KEY INSIGHTS ---\n");
            prompt.append("List only exact matches between notes and book. No explanations.\n");
            prompt.append("Format: [Concept] → Notes: [excerpt] → Book: [excerpt]\n");
            prompt.append("One line per match. Maximum 4 matches.\n\n");
            
            prompt.append("--- PART C: NEW COVERAGE ---\n");
            prompt.append("Bridge the gap between what the notes cover and what the book teaches.\n");
            prompt.append("- Missing concept: [what the notes don't mention]\n");
            prompt.append("- Book treatment: [specific chapter/section that covers it]\n");
            prompt.append("- Why it matters: [1 sentence connecting to the notes' topic]\n");
            prompt.append("Be methodical: show how the missing piece fits into the notes' framework.\n\n");
            
            prompt.append("--- COMPLEXITY ASSESSMENT ---\n");
            prompt.append("Level X: [name]\n");
            prompt.append("Reason: [1-2 sentences max]\n");
            prompt.append("Cognitive load: [what makes it hard/easy]\n\n");
            
            prompt.append("--- GAPS ---\n");
            prompt.append("- Gap: [specific missing knowledge]\n");
            prompt.append("- Impact: [why this matters for understanding]\n");
            prompt.append("- Source: [where in the book to find it]\n");
            prompt.append("Maximum 3 gaps. No filler.\n\n");
            
            prompt.append("--- NOTABLE PASSAGES ---\n");
            prompt.append("Extract 3-6 key data points, concepts, or claims from the notes. For each:\n");
            prompt.append("- Note excerpt: [quote the exact phrase or concept from the notes]\n");
            prompt.append("- Book match: [show exactly where and how the book addresses this]\n");
            prompt.append("- Verdict: Complementary / Direct / Not covered\n\n");
        } else {
            prompt.append("HEADER: Linked notes: NO (no Notion notes synced)\n\n");
            prompt.append("--- ANSWER ---\n");
            prompt.append("Direct answer from book content only.\n\n");
            prompt.append("--- COMPLEXITY ASSESSMENT ---\n");
            prompt.append("Level X: [name]\nReason: [1-2 sentences max]\n\n");
            prompt.append("--- SUGGESTED NOTES ---\n");
            prompt.append("What to capture in Notion for this topic: [bullet list]\n\n");
        }

        prompt.append("=== CRITICAL RULES ===\n");
        prompt.append("1. ALWAYS start with the HEADER line exactly as specified.\n");
        prompt.append("2. Use the exact section labels: CORE CONTENT, KEY INSIGHTS, PART C, COMPLEXITY ASSESSMENT, GAPS, NOTABLE PASSAGES.\n");
        prompt.append("3. Quote exact text from notes and book. Never paraphrase without showing the source.\n");
        prompt.append("4. Do not provide condensed summaries. Provide detailed analysis with explicit page citations [Page X] for all claims.\n");
        prompt.append("5. If notes are empty or irrelevant, say so explicitly.\n");
        prompt.append("6. CRITICAL LANGUAGE RULE: Respond entirely in ").append(language.equals("ar") ? "Arabic" : "English").append(". All text must be in ").append(language.equals("ar") ? "Arabic" : "English").append(". Do not mix languages.\n");
        prompt.append("7. PRESERVE MATH NOTATION: Keep all <math>...</math> tags and LaTeX notation exactly as they appear in the source text. Do not convert, simplify, or remove mathematical notation.\n");
        prompt.append("8. FORBIDDEN: Do NOT use HTML tags such as <sup>, <sub>, <em>, <strong>, <br>, or any other HTML inside mathematical notation. Use LaTeX only: superscripts as ^{}, subscripts as _{}, bold as \\mathbf{}, etc.\n");

        String result = aiService.getAnswerFromAi(prompt.toString(), question, language);

        LoggingUtils.exit(log, "answerWithContext", "resultLen", result == null ? 0 : result.length());
        return result;
    }

}




