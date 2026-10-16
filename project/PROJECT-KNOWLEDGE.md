# PROJECT-KNOWLEDGE.md — BookMind Nexus

## Architecture Overview

- **Frontend**: Thymeleaf + JavaScript on Spring Boot 3.5 (Java 21), port 8083
- **Backend**: Spring Boot layered architecture: Controller → Service → Repository → Entity
- **Database**: PostgreSQL at `D:\book-assistant-lite\external\pgsql\data`, port 5432, database `book_assistant`
- **Extraction Service**: Python FastAPI microservice (port 8080) wrapping Pix2Text only
- **AI**: Ollama local API (port 11434) with configurable models
- **Storage**: Local filesystem under `upload-dir`, `cache-dir`, `tmp`

### PDF Processing Pipeline

1. User uploads PDF → `UploadController` → `FileParserService`
2. Upload path stores the raw PDF bytes via `BookStorageService`; no full-text extraction at upload time.
3. When the user requests summary, QA, search, or analysis for a page range:
   - `PdfSliceService` slices the original PDF to the exact requested page range.
   - The sliced PDF is sent to the extraction service (`/extract/pix2text`).
   - The extraction service returns structured page chunks with LaTeX math notation.
   - `OnDemandExtractionService` orchestrates slicing → extraction → sanitization.
4. Extracted text is passed to the AI pipeline. No in-memory RAG chunk splitters are used for page-range requests.

### Extraction Modes

- **pix2text** (default): `/extract/pix2text` — OCR + math notation via Pix2Text
- **text-layer** (legacy): PDFBox direct text extraction, no OCR
- **model-only**: Native PDFBox text extraction fed directly to the LLM, bypassing Pix2Text entirely. Best for digital/OCR-ready PDFs where maximum speed is needed and the LLM can process raw text directly.

**Note:** Marker extraction has been removed. The Python extraction service now processes only Pix2Text requests.

## Extraction Pipeline

### Pix2Text (Primary)

- Endpoint: `extraction-service/main.py` `/extract/pix2text`
- Models: DocLayout-YOLO (`mfd-1.5`), cnocr OCR, LatexOCR (`mfr-1.5`)
- Output: Structured text with `<math>` tags for LaTeX
- GPU: `Pix2Text.from_config(device='cuda')`
- Input: Page-range-sliced PDF from `PdfSliceService`
- Default mode: `pix2text`; runtime override no longer applicable since Marker is removed

### PDFBox / text-layer (Legacy)

- Direct text extraction from digital PDFs
- Fastest, no OCR, no LaTeX math
- Use only for simple digital PDFs without equations

### Model Only (New)

- Native PDFBox text extraction fed directly to the LLM
- Bypasses Pix2Text entirely for maximum speed
- No OCR, no math correction, no empty-page skipping
- Raw text is passed directly to `qwen2.5-coder:14b`
- Best for digital/OCR-ready PDFs where the model can process raw text directly
- Configure via `app.extraction.mode=model-only`

## Pix2Text Configuration & Optimization

- Models: `mfd-1.5` and `mfr-1.5` (already cached locally)
- `resized_shape`: `1280` for maximum equation detection quality
- `mfr_batch_size`: `8` on GPU for better throughput
- MFD confidence threshold: `0.25` (already the default)
- Optimized for RTX 3070 with GPU acceleration

**Implementation:**
- Initialize with `Pix2Text.from_config(device='cuda')`
- Pass `resized_shape=1280` and `mfr_batch_size=8` to every call of `recognize_page_single_call()`. These parameters are passed internally by the wrapper.

## Chunking Strategy

### PDF Range Slicing (Current Default)

- `PdfSliceService` slices the original PDF byte array to the exact requested page range (`startPage`–`endPage`).
- The sliced PDF is sent directly to the Pix2Text extraction service.
- No in-memory RAG chunk splitters or text-chunking mechanisms are used for page-range requests.
- This preserves page boundaries, layout, and math context intact.

### RAG Chunking (Embedding/Retrieval Only)

- `DEFAULT_CHUNK_SIZE = 500` characters, `DEFAULT_CHUNK_OVERLAP = 100`
- Used exclusively for embedding and retrieval in pgvector
- Extraction prompts operate on sliced PDF page ranges, not 500-character text chunks

## AI Models & Prompt Strategy

### Models

- **Default**: `qwen2.5-coder:14b`
- **Embedding**: `nomic-embed-text`
- **Endpoint**: `http://localhost:11434/api/generate`
- Configurable via `AI_MODEL`, `AI_EMBEDDING_MODEL`

### Feature Classification

**Extraction** (verbatim text from the book):
- **Q&A**: Exact sentences answering the user's question
- **Search**: Most relevant verbatim chunks matching the query

**Summary** (guided curation):
- Curates passages into Core Content, Key Insights, Notable Passages
- Judgment task, not pure retrieval

**Analysis** (generates new structure from extracted content):
- Concept Map, Risk Flags, Reality Check, Comprehension Test, Learning Path

### How Important Paragraphs Are Selected

1. **Retrieval**: Find candidates
   - Small books (≤50k chars): full text injected directly
   - Large books: page-range chunks (30–60 pages each)
   - RAG mode: top-5 semantically similar chunks from pgvector

2. **Selection**: Model judgment determines importance
   - Q&A: relevance to the specific question
   - Summary: value categories (Core Content, Key Insights, Notable Passages)
   - Search: semantic similarity

3. **Synthesis**: Combine extracts; fall back to raw concatenation if synthesis fails

**Key point**: RAG finds candidates; the model's judgment selects what is actually important.

### The 500-Character Chunking Explained

- Used **only for RAG embedding/retrieval**, not for extraction prompts
- Book text split into 500-char chunks with 100-char overlap
- Embedded with `nomic-embed-text`, stored in pgvector
- Top-5 similar chunks retrieved per query
- Extraction prompts operate on page-range chunks: 10 pages for analysis, 30–60 pages for QA

### Context Window Management

- **Small books (≤50k chars)**: full text in one request
- **Large books (>50k chars)**: chunked QA with extraction + synthesis
- **RAG mode**: top-k chunks only
- **Cross-platform mode**: full book + external notes, truncated

### Multilingual Behavior

- Book content stays in its original language; the model never translates extracted passages
- UI language is used only for labels, structure, and explanations
- `addLanguageInstruction()` enforces this separation in the system prompt

### Prompt Strategy

Every prompt is an **extraction prompt**, not a generation prompt. The model is explicitly instructed to copy text exactly as written.

**System prompts enforce strict output constraints (NO COMMENTARY, NO PARAPHRASING) before context injection.**

> **Note:** MathPatchService, which previously stripped `[PAGE:X]` grounding tags from LLM responses, is temporarily disconnected while the model is validated with `qwen2.5-coder:14b`.

**Summary prompt**:
```
CRITICAL RULES — APPLY TO EVERY LINE OF OUTPUT:
1. NO COMMENTARY: Do NOT add any introductory phrases, explanations, or conversational filler.
   FORBIDDEN: "It seems like...", "Here's a summary...", "Based on the text...", etc.
2. NO PARAPHRASING: Copy EXACTLY word-for-word. Every sentence must match the source text.
3. MATH PRESERVATION: Preserve ALL mathematical notation in its EXACT original form:
   - Display math: \[...\] and $$...$$
   - Inline math: \(...\)
   - XML tags: <math>...</math>
   Do NOT convert, simplify, or remove any mathematical notation.
4. NO HTML IN MATH: Do NOT use HTML tags such as <sup>, <sub>, <em>, <strong>, <br> inside math.
   Use LaTeX only: superscripts as ^{}, subscripts as _{}, bold as \mathbf{}, etc.
5. PAGE CITATIONS: ONLY cite page numbers between Page X and Page Y. NEVER cite pages outside this range.
6. COVERAGE: You MUST cover the FULL page range provided; do not skip pages.
7. LANGUAGE: Keep the book's original language. Use the UI language for category labels only.

NOW COPY VERBATIM into these categories:

Core Content: Copy verbatim sentences that express theories, principles, definitions,
or conceptual frameworks. These are the foundational ideas the book is built on—
the "what is" and "why it matters" parts.

Key Insights: Copy verbatim sentences that express practical advice, actionable steps,
methods, techniques, warnings, or counter-intuitive observations. These are the
"how to" and "what to watch out for" parts—the actionable knowledge.

Notable Passages: Copy verbatim the most impactful quotes, memorable statements, or
powerful assertions made by the author. These are the passages a reader would want to
highlight, bookmark, or share—the "remember this" moments.
```

**Q&A extraction prompt**:
```
ABSOLUTE RULES:
1. NO COMMENTARY: Zero conversational text. No "It seems...", "Here's...", "Based on...".
2. NO PARAPHRASING: Every word must match the source text exactly.
3. MATH PRESERVATION: Preserve ALL math notation exactly:
   - \[...\] (display math)
   - $$...$$ (display math)
   - \(...\) (inline math)
   - <math>...</math> (XML tags)
   Do NOT convert, simplify, or remove any mathematical notation.
4. NO HTML IN MATH: Do NOT use HTML tags such as <sup>, <sub>, <em>, <strong>, <br> inside math.
   Use LaTeX only: superscripts as ^{}, subscripts as _{}, bold as \mathbf{}, etc.
5. PAGE RANGES: Only cite pages within the provided range.
6. LANGUAGE: Respond entirely in English. Do not mix languages.

You are a verbatim passage extractor. You do NOT think, analyze, or summarize.
You ONLY copy exact passages from the provided text.

TASK: Copy verbatim the passages that directly answer the user's question.
- Copy passages EXACTLY as written in the text
- Only include content DIRECTLY relevant to the question
- If nothing is relevant, respond with "No relevant content found."
- Preserve the author's exact wording
- When referencing content from a specific page, include the page number
- Extract detailed, relevant passages with full context.
```

**Concept map prompt**:
```
Extract a concept map from the text ONLY:
- List 5-10 core concepts with brief definitions
- Describe relationships between concepts
- Organize from general to specific
- List key principles
- Describe practical applications

RULES:
- ONLY include concepts explicitly mentioned in the text
- Verify each concept label appears verbatim in the text
- Do NOT invent or hallucinate concepts, nodes, or edges
```

## Post-Extraction Math Patching (Lazy Correction)

### MathPatchService (Temporarily Disabled)

- **Location**: `com.bookassistant.service.file.MathPatchService`
- **Status**: Disconnected from controllers.
- **Previous Purpose**: Post-processed LLM responses to remove grounding metadata tags (`[PAGE:X]`, `[PAGE:34,LINE:12]`) before display. Did not overwrite or replace LLM output with raw extraction content.
- **Previous Trigger**: After `AiService.getAnswerFromAi()` returned, before response was sent to UI.
- **Previous Flow**:
  1. Receive LLM output
  2. Strip `[PAGE:34]` and `[PAGE:34,LINE:12]` tags
  3. Return pure LLM text

**Removed Grounding Marker Contract:**

The following instruction is no longer in system prompts:
```
GROUNDING REQUIRED: When summarizing or answering, you MUST append structural location tags to every major claim or equation block. Format required: [PAGE:34] or [PAGE:34,LINE:12].
```

## PDF Range Slicing Service

### PdfSliceService

- **Location**: `com.bookassistant.service.file.PdfSliceService` (new)
- **Purpose**: Slices the original uploaded PDF to an exact page range using Apache PDFBox, producing a new self-contained PDF byte array.
- **Trigger**: Before sending a PDF to the extraction service for summary, QA, search, or analysis.
- **Flow**:
  1. Load original PDF bytes from `BookStorageService`
  2. Validate `startPage` and `endPage` against the PDF's total page count
  3. Use PDFBox `PDDocument` + `PDFMergerUtility` or page-range extraction to create a new PDF containing only the requested pages
  4. Return the sliced PDF byte array
  5. `OnDemandExtractionService` sends the sliced PDF to `/extract/pix2text`

**Benefits:**
- Eliminates the need for in-memory text chunking and RAG chunk splitters for page-range requests
- Preserves page boundaries, layout, and math context intact
- Reduces extraction time and memory usage by processing only the requested pages

### OnDemandExtractionService Refactor

- **Old flow**: Load full PDF → extract all pages → split text into chunks → send text to AI
- **New flow**: Load full PDF → slice to requested range → send sliced PDF to extraction service → return structured `BookData`
- Marker dependency removed; only Pix2Text is used

## Common Failure Modes

- **"Extraction service is not running"**: Start with `task start-pix2text`; verify `curl http://localhost:8080/health`
- **Ollama error / "API key not configured"**: Start Ollama locally; set `OLLAMA_API_KEY` for remote endpoints
- **Empty extraction**: PDF is scanned/image-based but `text-layer` mode selected, or PDF is corrupted
- **GPU/CUDA mismatch**: Ensure `torch>=2.5.0` and `onnxruntime-gpu==1.29.0`; verify CUDA and cuDNN bin are on PATH
- **White-label 503 / server freeze**: Extraction is blocking the request thread; must be async or delegated to a background service
- **Prompt injection**: `sanitizeUserContent()` replaces injection patterns with `[REDACTED]`
- **Context overflow**: Chunked QA activates automatically when `bookText.length() > DIRECT_QA_THRESHOLD`
- **Timeout**: Retry logic built-in (2 retries, 500ms delay); timeouts neutralized for testing (86400s AI + extraction)
- **Missing math notation / corrupted LaTeX**: Ensure prompts enforce strict output constraints (no HTML tags, no preamble); verify `sanitizeText()` is applied after extraction; MathPatchService is temporarily disabled — `qwen2.5-coder:14b` handles math via inline LaTeX from Pix2Text directly
- **MathPatchService NPE / binding failure**: [RESOLVED] MathPatchService has been disconnected from all controllers; no longer invoked. LLM responses are returned directly without tag stripping.
- **Pix2Text slow page processing**: One-page extraction can take 18–20s; `Pix2TextExtractionClient` connect timeout is set to 60s, read timeout defaults to `86400s` via `app.extraction.pix2text.timeout`
- **Summary token-limit errors**: Summary chunks are truncated to `MAX_CHUNK_TOKENS = 4000` before calling the LLM; exception handlers append the underlying exception detail to the user-facing error message
- **PDF slice out-of-bounds**: `PdfSliceService` validates page ranges against the original PDF page count; throws `IllegalArgumentException` for invalid ranges

## Error Transparency & Diagnostics

### Extraction Pipeline Logging

**Pix2TextExtractionClient:**
- Logs raw JSON response from Python service before parsing (2000-char preview + total length).
- Each parsed page logs: page number, character count, and math-element detection flag.
- Empty/null page text triggers WARNING with page index and surrounding raw JSON payload.

**OnDemandExtractionService:**
- Tracks empty pages skipped per extraction and logs summary with page numbers and counts.
- Example log: `OnDemandExtractionService empty-page summary | emptyCount=3 | pages=[2,5,7] | range=1-10`

### AI Pipeline Logging

**AiService:**
- Every AI request gets a unique `requestId` (8-char UUID) included in all log entries.
- Full request JSON body logged at INFO before sending to Ollama.
- Full response body logged at ERROR on non-2xx responses.
- Retry attempts log: `Retrying in 1000ms (requestId=abc12345 attempt 1/2)`

### Controller Error Tracking

**SummaryController / QaController:**
- Skip reasons tracked per chunk/page in a `List<String>` passed to the UI model.
- Empty text skips: `"Skipped pages 5-9: no extracted text from Pix2Text"`
- AI error skips: `"Skipped pages 10-14: AI error — AI error: HTTP 500"`
- UI renders detailed breakdown in warning alert alongside generic chunk count.

### AI Endpoint Request Formats

**SummaryController:**
- Form endpoint: `POST /summary/pages` with `@RequestParam` parameters (`startPage`, `endPage`, `outputLanguage`, `chunkSize`).
- JSON endpoint: `POST /summary/pages` with `Content-Type: application/json` and `@RequestBody PageSummaryRequest`.
- `PageSummaryRequest` record: `Long bookId`, `int startPage`, `int endPage`, `String outputLanguage`, `String chunkSize`.
- `chunkSize` values: `auto` (automatic), `model-only`/`modelonly` (full range as single chunk), or numeric string (fixed chunk size).
- JSON responses return `ResponseEntity<Map<String, Object>>` with rendered summary HTML and metadata.
- Error responses: `401` for unauthorized, `400` for invalid range/missing book, `500` for processing failures.

## Runtime Exception Instrumentation

### Illegal Group Reference Logging

To diagnose `java.lang.IllegalArgumentException: Illegal group reference`, the existing AOP telemetry aspects (`ServiceTelemetryAspect`, `ControllerTelemetryAspect`) will emit a dedicated `log.error(...)` whenever this specific exception is thrown anywhere in the application.

**Mechanism:**
- `@AfterThrowing` advice intercepts exceptions across all service and controller packages.
- When the thrown exception is an `IllegalArgumentException` whose message contains `"Illegal group reference"`, the aspect logs:
  - `className` and `methodName`
  - `args` — the full input payload from `JoinPoint`
  - `stackTrace` — the full stack trace via `StringWriter`

**Files involved:**
- `com.bookassistant.aop.ServiceTelemetryAspect`
- `com.bookassistant.aop.ControllerTelemetryAspect`

**Purpose:** Capture the exact code path and input payload that triggers the regex failure, without changing application behavior.
