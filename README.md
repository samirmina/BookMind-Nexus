# BookMind Nexus

**Smart Book Extractor** — AI-powered PDF book extraction for study and research.

BookMind Nexus is not an AI summarizer. It extracts verbatim passages from your books so you read the author's actual words, not an AI's interpretation. Every extracted passage is traceable to its exact page number.

## Features

- **Extraction-first AI**: Pulls exact sentences and paragraphs from uploaded PDFs, preserving original wording and page citations
- **Verbatim Q&A**: Ask questions about your book; get back the exact passages that answer them
- **Guided Summary**: Curates passages into Core Content, Key Insights, and Notable Passages
- **Concept Maps & Risk Flags**: Structure extracted content without inventing new material
- **Reality Checks**: Validate extracted claims against the source text
- **Comprehension Tests**: Generate study questions from book content
- **Learning Paths**: Build study plans from book content
- **Multi-language UI**: 13 languages supported (book content stays in its original language)
- **LaTeX math rendering**: Mathematical notation preserved exactly as written
- **Bring Your Own Key (BYOK) / Use Your Own LLM**: Configure your own AI provider per account (Ollama local, OpenAI, Anthropic, Google Vertex AI, Cohere, or any OpenAI-compatible endpoint)
- **Per-user AI provider settings**: Each user configures their own AI credentials; keys are stored encrypted and never shared
- **OAuth Integration**: Notion account linking for external platform sync; Google OAuth for authentication
- **Passwordless authentication**: Email magic links and OAuth — no passwords stored
- **Telemetry & Admin Console**: System health monitoring, error tracking, and runtime logs
- **Docker-ready**: One-command setup with PostgreSQL, Ollama, and optional Pix2Text OCR

## Architecture

| Component | Technology |
|-----------|-----------|
| Backend | Spring Boot 3.5, Java 21 |
| Frontend | Thymeleaf + Bootstrap 5 (dark theme) |
| Database | PostgreSQL 16 + pgvector (RAG embeddings) |
| AI Providers | Ollama (local), OpenAI, Anthropic (Claude), Google Vertex AI / Gemini, Cohere, any OpenAI-compatible endpoint (vLLM, LM Studio, LocalAI, TGI) |
| OCR (optional) | Pix2Text (CUDA 12.4, Python 3.12) |
| Container | Docker Compose (4 services) |
| Ports | App: 8083, Postgres: 5432, Ollama: 11434, Pix2Text: 8080 |

## Quick Start (Docker)

```bash
# Clone the repo
git clone https://github.com/samirmina/BookMind-Nexus.git
cd BookMind-Nexus

# Copy environment config
cp .env.example .env
# Edit .env if needed (defaults work for most setups)

# Install go-task (task runner) if not already installed
# https://taskfile.dev/installation/

# Build, start, and pull AI models
task setup

# Access the app at http://localhost:8083
```

### Individual Task Commands

| Command | Description |
|---------|-------------|
| `task build` | Build all Docker images |
| `task start` | Start all services (postgres, ollama, app, pix2text) |
| `task start-app` | Start Spring Boot app only |
| `task start-ollama` | Start Ollama only |
| `task start-postgres` | Start PostgreSQL only |
| `task start-pix2text` | Start Pix2Text extraction service only |
| `task stop` | Stop all services |
| `task logs` | View all service logs |
| `task logs-app` | View app logs only |
| `task clean` | Remove all containers and volumes |
| `task pull-models` | Pull Ollama models (llama3.2:3b, nomic-embed-text) |

## Extraction Modes

| Mode | Description | Best For |
|------|-------------|----------|
| **model-only** | Native PDFBox text → LLM (no OCR, fastest) | Digital/OCR-ready PDFs |
| **pix2text** | OCR + LaTeX math via Pix2Text service | Scanned books, heavy math |
| **text-layer** | PDFBox direct text extraction | Simple digital PDFs |

Set `app.extraction.mode` in `application.properties` (local) or `application-docker.properties` (Docker).

## Project Structure

```
BookMind-Nexus/
├── Dockerfile                 # Spring Boot app container image
├── docker-compose.yml         # Multi-service orchestration
├── Taskfile.yml               # go-task command definitions
├── .env.example               # Environment variable template
├── .gitignore                 # Git ignore rules
├── AGENTS.md                  # Agent instructions & project rules
├── README.md                  # This file
├── PROJECT-KNOWLEDGE.md       # Architecture & technical reference
├── LICENSE                    # MIT License
├── pom.xml                    # Maven build file
├── package.json               # Node.js (frontend tooling)
├── package-lock.json
├── completion/                # go-task shell completions
├── extraction-service/        # Pix2Text OCR microservice
│   ├── Dockerfile             # Pix2Text container image
│   ├── main.py                # FastAPI extraction service
│   ├── requirements.txt       # Python dependencies
│   └── README.md              # Extraction service docs
├── src/
│   ├── main/
│   │   ├── java/com/bookassistant/
│   │   │   ├── aop/              # Telemetry aspects
│   │   │   ├── config/           # Spring configuration
│   │   │   ├── controller/       # MVC controllers
│   │   │   │   ├── admin/        # Admin console, runtime logs
│   │   │   │   ├── ai/           # Analysis, Q&A, Search, Summary
│   │   │   │   ├── book/         # Upload, extraction progress
│   │   │   │   ├── study/        # Comprehension, learning paths
│   │   │   │   ├── ui/           # UI audit
│   │   │   │   └── web/          # Auth, accounts, pages, settings
│   │   │   ├── dto/              # Data transfer objects
│   │   │   ├── entity/           # JPA entities
│   │   │   ├── model/            # Domain models
│   │   │   ├── repository/       # Spring Data JPA repositories
│   │   │   ├── service/          # Business logic
│   │   │   │   ├── ai/           # AiService, ProviderAdapterFactory, provider adapters
│   │   │   │   ├── auth/         # UserService, UserAiConfigService
│   │   │   │   ├── config/       # RuntimeConfigService
│   │   │   │   ├── file/         # PDF parsing, extraction, slicing
│   │   │   │   ├── integration/  # Learning paths, sync, external platforms
│   │   │   │   ├── infrastructure/ # Cache, console, OAuth, storage
│   │   │   │   ├── rag/          # RAG service
│   │   │   │   ├── telemetry/    # Telemetry service
│   │   │   │   ├── text/         # Markdown, text direction
│   │   │   │   └── ui/           # HTML diff, screenshots
│   │   │   ├── provider/         # AI Provider Adapters (OpenAI-compatible, Anthropic, Google Vertex, Cohere)
│   │   │   ├── session/          # BookSession
│   │   │   ├── tool/             # Image extraction
│   │   │   └── util/             # Utilities
│   │   └── resources/
│   │       ├── application.properties      # Local (WSL) config
│   │       ├── application-docker.properties # Docker config
│   │       ├── data.sql                    # Seed data
│   │       ├── messages.properties         # English messages
│   │       ├── messages_XX.properties      # 12 additional locales
│   │       ├── schema.sql                  # Database schema
│   │       ├── schema-migration.sql
│   │       ├── schema-telemetry.sql
│   │       ├── schema-telemetry-triggers.sql
│   │       ├── schema-ui-validation.sql
│   │       ├── static/
│   │       │   ├── styles.css          # Modern CSS design system
│   │       │   ├── js/
│   │       │   │   ├── collapsible.js
│   │       │   │   └── ui-audit.js
│   │       └── templates/              # 10 Thymeleaf templates (incl. guide/ai-setup.html)
│   └── test/                    # JUnit tests
├── cache/                  # Runtime cache directory
├── external/               # External data (cache, tmp, logs, pdfs)
├── storage/                # Book storage (user uploads)
└── ui-screenshots/         # UI screenshot directory
```

## Configuration

### Local (WSL/Linux)

Edit `src/main/resources/application.properties`:
- `app.ai.model` — Ollama model (default: `llama3.2:3b`)
- `app.extraction.mode` — `pix2text`, `text-layer`, or `model-only`
- `spring.datasource.url` — PostgreSQL connection
- `app.ai.base-url` — Ollama endpoint

### Docker

Edit `src/main/resources/application-docker.properties`:
- `app.ai.model` — defaults to `llama3.2:3b`
- `app.extraction.mode` — defaults to `model-only`
- Database uses service name `postgres` instead of `localhost`

### Environment Variables (.env)

Copy `.env.example` to `.env` and adjust:

```
SERVER_PORT=8083
DB_USERNAME=postgres
DB_PASSWORD=postgres
AI_BASE_URL=http://localhost:11434/api/generate
AI_MODEL=llama3.2:3b
AI_API_KEY=
AI_TIMEOUT_SECONDS=86400
```

### Bring Your Own Key (BYOK) / Use Your Own LLM

Each user can configure their own AI provider in **Settings → AI Provider**:

| Provider | Base URL | API Key Required | Example Models |
|----------|----------|------------------|----------------|
| **Ollama (Local)** | `http://localhost:11434` | No | `llama3.2:3b`, `nomic-embed-text` |
| **OpenAI** | `https://api.openai.com/v1` | Yes (`sk-...`) | `gpt-4o`, `gpt-4o-mini`, `text-embedding-3-small` |
| **Anthropic (Claude)** | `https://api.anthropic.com` | Yes | `claude-3-5-sonnet`, `claude-3-opus` |
| **Google Vertex AI / Gemini** | `https://generativelanguage.googleapis.com` or Vertex endpoint | Yes (OAuth2) | `gemini-1.5-pro`, `gemini-1.5-flash` |
| **Cohere** | `https://api.cohere.com` | Yes | `command-r-plus`, `embed-english-v3.0` |
| **OpenAI-Compatible** | Your endpoint (e.g., `http://localhost:8000/v1`) | Yes | Any model served by vLLM, LM Studio, LocalAI, TGI, etc. |

**Setup Guide**: Visit `/guide/ai-setup` in the app for step-by-step instructions with screenshots.

**Test Connection**: Each configuration can be tested before saving via the "Test Connection" button.

## AI Provider Adapter Architecture

The application uses a **Provider Adapter Pattern** to support multiple AI providers:

```
AiService → ProviderAdapterFactory → [OpenAiCompatibleAdapter, AnthropicAdapter, GoogleVertexAdapter, CohereAdapter, ...]
```

Each adapter implements:
- `buildRequest()` — Transforms internal config to provider-specific HTTP request
- `extractContent()` — Parses provider-specific response format
- `matchesBaseUrl()` — Auto-detects provider from base URL

Adding a new provider: implement `AiProviderAdapter` and register in `ProviderAdapterFactory`.

## Database

The app uses PostgreSQL with the `pgvector` extension for semantic search. Schema is initialized automatically on startup via `schema.sql` (and migration scripts).

Key tables:
- `users` — User accounts (email, name, timestamps)
- `user_ai_config` — Per-user AI provider configuration (provider, base_url, api_key, model, embedding_model)
- `books` — Uploaded books (title, file_path, page_count, full_text, thumbnails)
- `book_chunks` — RAG chunks with `vector(768)` embeddings for semantic search
- `linked_accounts` — OAuth linked accounts (Notion, Google)

Seed data: a demo user is inserted via `data.sql` (change the password hash in production).

## AI Models

Default models (used when no per-user config is set):
- **Llama 3.2:3b** — Primary extraction model (recommended)
- **nomic-embed-text** — Embedding model for RAG/semantic search (768 dimensions)

Pull models before first use:
```bash
task pull-models
# Or manually:
docker compose exec ollama ollama pull llama3.2:3b
docker compose exec ollama ollama pull nomic-embed-text
```

## Authentication & Security

- **Passwordless**: Email magic links and Google OAuth 2.0 — no passwords stored
- **Session management**: HttpOnly cookies, 30-minute timeout
- **Per-user API keys**: Stored encrypted in database, never logged
- **Prompt injection protection**: OWASP-compliant sanitization on all user content

### OAuth Integrations

- **Google OAuth 2.0**: For user authentication
- **Notion OAuth 2.0**: For external platform sync (configure via `NOTION_OAUTH_CLIENT_ID`, `NOTION_OAUTH_CLIENT_SECRET`, `NOTION_OAUTH_REDIRECT_URI`)

## OAuth (Notion)

The app supports OAuth 2.0 integration with Notion for external platform sync. Configure via environment variables:
- `NOTION_OAUTH_CLIENT_ID`
- `NOTION_OAUTH_CLIENT_SECRET`
- `NOTION_OAUTH_REDIRECT_URI`

## Telemetry & Admin Console

The app includes a telemetry system for monitoring:
- AI interaction logging
- System health checks
- Error tracking
- Test run/failure tracking
- UI validation snapshots
- Console entry capture

Access the admin console at `/admin/console` (requires authentication).

## AI Setup Guide

An interactive guide is available at **Settings → AI Provider → Setup Guide** (or directly at `/guide/ai-setup`):

- Provider comparison table (Ollama vs OpenAI vs Custom)
- Step-by-step setup instructions
- Troubleshooting accordion (connection failures, API key errors, model not found, embedding dimension mismatches)
- Link to open Settings directly

## Design Principles

1. **Extraction, not summarization** — AI copies exact book text; never paraphrases
2. **Page citations** — Every passage references its source page
3. **No translation of book content** — Books stay in their original language
4. **Verbatim math** — LaTeX notation preserved exactly
5. **No hallucinated structure** — Concept maps and risk flags only use text that exists in the book
6. **User-owned AI** — Each user brings their own model; no shared keys or shared inference

## License

MIT License. See [LICENSE](LICENSE).