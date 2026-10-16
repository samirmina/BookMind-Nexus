# PROJECT-KNOWLEDGE.md — BookMind Nexus

## Architecture Overview

- **Frontend**: Thymeleaf + JavaScript on Spring Boot 3.5 (Java 21), port 8083
- **Backend**: Spring Boot layered architecture: Controller → Service → Repository → Entity
- **Database**: PostgreSQL 16 + pgvector (Docker service: `postgres`), port 5432, database `book_assistant`
- **Extraction Service**: Python FastAPI microservice (port 8080) wrapping Pix2Text (optional, CUDA)
- **AI**: Ollama local API (port 11434) with Llama 3.2:3b and nomic-embed-text
- **Storage**: Local filesystem under cache/upload/tmp directories (WSL: `/mnt/d/book-assistant-lite/`, Docker: `/app/`)
- **Container Orchestration**: Docker Compose with 4 services (postgres, ollama, pix2text, app)

## PDF Processing Pipeline

1. User uploads PDF → `UploadController` → `FileParserService`
2. Upload path stores the raw PDF bytes via `BookStorageService`; no full-text extraction at upload time.
3. When the user requests summary, QA, search, or analysis for a page range:
   - `PdfSliceService` slices the original PDF to the exact requested page range.
   - In `model-only` mode: sliced PDF text is extracted via PDFBox and fed directly to the LLM.
   - In `pix2text` mode: the sliced PDF is sent to the extraction service (`/extract/pix2text`), which returns structured page chunks with LaTeX math notation.
   - `OnDemandExtractionService` orchestrates slicing → extraction → sanitization.
4. Extracted text is passed to the AI pipeline. No in-memory RAG chunk splitters are used for page-range requests.

## Extraction Modes

| Mode | Description | Default In |
|------|-------------|------------|
| **model-only** | Native PDFBox text extraction fed directly to the LLM, bypassing Pix2Text entirely. Fastest, no OCR, no math correction. | Docker |
| **pix2text** | OCR + math notation via Pix2Text service. Best for scanned books and heavy math. | Local (WSL) |
| **text-layer** (legacy) | PDFBox direct text extraction, no OCR. Fastest, no LaTeX math. | Optional |

**Note:** Marker extraction has been removed. The Python extraction service now processes only Pix2Text requests.

## Extraction Pipeline

### Model-Only (Primary)

- Native PDFBox text extraction fed directly to the LLM
- Bypasses Pix2Text entirely for maximum speed
- No OCR, no math correction, no empty-page skipping
- Raw text is passed directly to `llama3.2:3b`
- Best for digital/OCR-ready PDFs where the model can process raw text directly
- Configure via `app.extraction.mode=model-only`

### Pix2Text (OCR + Math)

- Endpoint: `extraction-service/main.py` `/extract/pix2text`
- Models: DocLayout-YOLO (`mfd-1.5`), cnocr OCR, LatexOCR (`mfr-1.5`)
- Output: Structured text with `<math>` tags for LaTeX
- GPU: `Pix2Text.from_config(device='cuda')`
- Input: Page-range-sliced PDF from `PdfSliceService`
- Configure via `app.extraction.mode=pix2text`

### PDFBox / text-layer (Legacy)

- Direct text extraction from digital PDFs
- Fastest, no OCR, no LaTeX math
- Use only for simple digital PDFs without equations

## Chunking Strategy

### PDF Range Slicing (Current Default)

- `PdfSliceService` slices the original PDF byte array to the exact requested page range (`startPage`–`endPage`).
- The sliced PDF is sent directly to the extraction service (or text extracted via PDFBox in model-only mode).
- No in-memory RAG chunk splitters or text-chunking mechanisms are used for page-range requests.
- This preserves page boundaries, layout, and math context intact.

### RAG Chunking (Embedding/Retrieval Only)

- `DEFAULT_CHUNK_SIZE = 500` characters, `DEFAULT_CHUNK_OVERLAP = 100`
- Used exclusively for embedding and retrieval in pgvector
- Extraction prompts operate on sliced PDF page ranges, not 500-character text chunks

## AI Models & Prompt Strategy

### Models

- **Default**: `llama3.2:3b`
- **Embedding**: `nomic-embed-text`
- **Endpoint**: `http://localhost:11434/api/generate` (local) or `http://ollama:11434/api/generate` (Docker)
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

## Docker Compose Services

| Service | Image | Port | Purpose |
|---------|-------|------|---------|
| postgres | `pgvector/pgvector:pg16` | 5432 | Database + pgvector extension |
| ollama | `ollama/ollama:latest` | 11434 | AI model server (Llama 3.2:3b, nomic-embed-text) |
| pix2text | Custom (CUDA 12.4) | 8080 | OCR + LaTeX math extraction (optional) |
| app | Custom Spring Boot | 8083 | Main application |

### Volume Mounts

- `postgres_data` — PostgreSQL data persistence
- `ollama_data` — Ollama model persistence
- `./project/external/cache` → `/app/cache` — Shared cache
- `./project/external/tmp` → `/app/tmp` — Upload temp files
- `./project/storage/books` → `/app/storage/books` — Book storage
- `./project/ui-screenshots` → `/app/ui-screenshots` — UI screenshots

## Task Commands (go-task)

| Command | Description |
|---------|-------------|
| `task setup` | Build, start, pull models |
| `task build` | Build all Docker images |
| `task start` | Start all services |
| `task start-app` | Start Spring Boot app |
| `task start-ollama` | Start Ollama |
| `task start-postgres` | Start PostgreSQL |
| `task start-pix2text` | Start Pix2Text service |
| `task stop` | Stop all services |
| `task logs` | View all logs |
| `task clean` | Remove containers and volumes |
| `task pull-models` | Pull Ollama models |

## Common Failure Modes

- **"Extraction service is not running"**: Start with `task start-pix2text`; verify `curl http://localhost:8080/health`
- **Ollama error / "API key not configured"**: Start Ollama locally; set `OLLAMA_API_KEY` for remote endpoints
- **Empty extraction**: PDF is scanned/image-based but `text-layer` mode selected, or PDF is corrupted
- **GPU/CUDA mismatch**: Ensure CUDA and cuDNN are available for Pix2Text container
- **Context overflow**: Chunked QA activates automatically when `bookText.length() > DIRECT_QA_THRESHOLD`
- **Timeout**: Retry logic built-in (2 retries); timeouts set to 86400s for AI + extraction
- **Summary token-limit errors**: Summary chunks truncated before calling the LLM
- **PDF slice out-of-bounds**: `PdfSliceService` validates page ranges against the original PDF page count

## Error Transparency & Diagnostics

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
- AI error skips: `"Skipped pages 10-14: AI error — HTTP 500"`
- UI renders detailed breakdown in warning alert alongside generic chunk count.

## Runtime Exception Instrumentation

### Illegal Group Reference Logging

To diagnose `java.lang.IllegalArgumentException: Illegal group reference`, the existing AOP telemetry aspects (`ServiceTelemetryAspect`, `ControllerTelemetryAspect`) will emit a dedicated `log.error(...)` whenever this specific exception is thrown anywhere in the application.

**Mechanism:**
- `@AfterThrowing` advice intercepts exceptions across all service and controller packages.
- When the thrown exception is an `IllegalArgumentException` whose message contains `"Illegal group reference"`, the aspect logs className, methodName, args, and stackTrace.

**Files involved:**
- `com.bookassistant.aop.ServiceTelemetryAspect`
- `com.bookassistant.aop.ControllerTelemetryAspect`