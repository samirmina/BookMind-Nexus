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
- **OAuth Integration**: Notion account linking for external platform sync
- **Telemetry & Admin Console**: System health monitoring, error tracking, and runtime logs
- **Docker-ready**: One-command setup with PostgreSQL, Ollama, and optional Pix2Text OCR

## Architecture

| Component | Technology |
|-----------|-----------|
| Backend | Spring Boot 3.5, Java 21 |
| Frontend | Thymeleaf + Bootstrap 5 (dark theme) |
| Database | PostgreSQL 16 + pgvector (RAG embeddings) |
| AI | Ollama — Llama 3.2:3b, nomic-embed-text |
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
│   │   │   │   ├── ai/           # AiService (Ollama integration)
│   │   │   │   ├── auth/         # UserService
│   │   │   │   ├── config/       # RuntimeConfigService
│   │   │   │   ├── file/         # PDF parsing, extraction, slicing
│   │   │   │   ├── integration/  # Learning paths, sync, external platforms
│   │   │   │   ├── infrastructure/ # Cache, console, OAuth, storage
│   │   │   │   ├── rag/          # RAG service
│   │   │   │   ├── telemetry/    # Telemetry service
│   │   │   │   ├── text/         # Markdown, text direction
│   │   │   │   └── ui/           # HTML diff, screenshots
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
│   │       └── templates/              # 9 Thymeleaf templates
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

## Database

The app uses PostgreSQL with the `pgvector` extension for semantic search. Schema is initialized automatically on startup via `schema.sql` (and migration scripts).

Seed data: a demo user is inserted via `data.sql` (change the password hash in production).

## AI Models

- **Llama 3.2:3b** — Primary extraction model (recommended)
- **nomic-embed-text** — Embedding model for RAG/semantic search

Pull models before first use:
```bash
task pull-models
# Or manually:
docker compose exec ollama ollama pull llama3.2:3b
docker compose exec ollama ollama pull nomic-embed-text
```

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

## Design Principles

1. **Extraction, not summarization** — AI copies exact book text; never paraphrases
2. **Page citations** — Every passage references its source page
3. **No translation of book content** — Books stay in their original language
4. **Verbatim math** — LaTeX notation preserved exactly
5. **No hallucinated structure** — Concept maps and risk flags only use text that exists in the book

## License

MIT License. See [LICENSE](LICENSE).