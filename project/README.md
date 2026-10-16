# BookMind Nexus

**Smart Book Extractor** — AI-powered PDF book extraction for study and research.

BookMind Nexus is not an AI summarizer. It extracts verbatim passages from your books so you read the author's actual words, not an AI's interpretation. Every extracted passage is traceable to its exact page number.

## Features

- **Extraction-first AI**: Pulls exact sentences and paragraphs from uploaded PDFs, preserving original wording and page citations
- **Verbatim Q&A**: Ask questions about your book; get back the exact passages that answer them
- **Guided Summary**: Curates passages into Core Content, Key Insights, and Notable Passages
- **Concept Maps & Risk Flags**: Structure extracted content without inventing new material
- **Learning Paths**: Build study plans from book content
- **Multi-language UI**: 13 languages supported (book content stays in its original language)
- **LaTeX math rendering**: Mathematical notation preserved exactly as written
- **Docker-ready**: One-command setup with PostgreSQL, Ollama, and optional Pix2Text OCR

## Architecture

| Component | Technology |
|-----------|-----------|
| Backend | Spring Boot 3.5, Java 21 |
| Frontend | Thymeleaf + Bootstrap 5 (dark theme) |
| Database | PostgreSQL 16 + pgvector (RAG embeddings) |
| AI | Ollama — Llama 3.2:3b, nomic-embed-text |
| OCR (optional) | Pix2Text (CUDA 12.4) |
| Container | Docker Compose (4 services) |
| Ports | App: 8083, Postgres: 5432, Ollama: 11434, Pix2Text: 8080 |

## Quick Start (Docker)

```bash
# Clone the repo
git clone https://github.com/samirmina/BookMind-Nexus.git
cd BookMind-Nexus/project

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

Set `app.extraction.mode` in `application.properties` or `application-docker.properties`.

## Project Structure

```
project/
├── Dockerfile                 # Spring Boot app image
├── extraction-service/
│   ├── Dockerfile             # Pix2Text OCR service image
│   ├── main.py                # FastAPI extraction service
│   └── requirements.txt       # Python dependencies
├── src/
│   ├── main/
│   │   ├── java/com/bookassistant/  # 140+ Java classes
│   │   │   ├── controller/          # MVC controllers
│   │   │   ├── service/             # Business logic (AI, extraction, RAG, etc.)
│   │   │   ├── repository/          # Spring Data JPA repositories
│   │   │   ├── entity/              # JPA entities
│   │   │   └── config/              # Spring configuration
│   │   └── resources/
│   │       ├── application.properties   # Local (WSL) config
│   │       ├── application-docker.properties  # Docker config
│   │       ├── messages_*.properties    # 13 language bundles
│   │       ├── schema.sql               # Database schema
│   │       ├── static/styles.css        # Modern CSS design system
│   │       └── templates/               # 9 Thymeleaf templates
│   └── test/                    # JUnit tests
├── docker-compose.yml         # Multi-service orchestration
├── Taskfile.yml               # Task runner commands
├── .env.example               # Environment variable template
└── README.md                  # This file
```

## Configuration

### Local (WSL)

Edit `project/src/main/resources/application.properties`:
- `app.ai.model` — Ollama model (default: `phi3.5:latest`, change to `llama3.2:3b`)
- `app.extraction.mode` — `pix2text`, `text-layer`, or `model-only`
- `spring.datasource.url` — PostgreSQL connection

### Docker

Edit `project/src/main/resources/application-docker.properties`:
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

The app uses PostgreSQL with the `pgvector` extension for semantic search. Schema is initialized automatically on startup via `schema.sql`.

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

## Design Principles

1. **Extraction, not summarization** — AI copies exact book text; never paraphrases
2. **Page citations** — Every passage references its source page
3. **No translation of book content** — Books stay in their original language
4. **Verbatim math** — LaTeX notation preserved exactly
5. **No hallucinated structure** — Concept maps and risk flags only use text that exists in the book

## License

MIT License. See [LICENSE](LICENSE).