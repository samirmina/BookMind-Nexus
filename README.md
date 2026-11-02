# BookMind Nexus

**Smart Book Extractor** — AI-powered PDF book extraction for study and research.

BookMind Nexus extracts verbatim passages from uploaded books so you read the author's actual words with page citations, not an AI's interpretation. Every extracted passage is traceable to its exact source page.

## Tech Stack

Pure Java backend application built with:

| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 3 |
| Build | Maven |
| Database | PostgreSQL 16 + pgvector |
| Container | Docker |

## What It Does

- **Extraction-first**: Pulls exact sentences and paragraphs from uploaded PDFs, preserving original wording and page citations
- **Verbatim Q&A**: Ask questions about your book; get back the exact passages that answer them
- **Guided Summary**: Curates passages into Core Content, Key Insights, and Notable Passages
- **Concept Maps & Risk Flags**: Structure extracted content without inventing new material
- **Reality Checks**: Validate extracted claims against the source text
- **Comprehension Tests**: Generate study questions from book content
- **Learning Paths**: Build study plans from book content
- **Multi-language UI**: 13 languages supported (book content stays in its original language)
- **LaTeX math rendering**: Mathematical notation preserved exactly as written
- **Bring Your Own Key (BYOK)**: Configure your own AI provider per account (Ollama local, OpenAI, Anthropic, Google Vertex AI, Cohere, or any OpenAI-compatible endpoint)
- **Per-user AI provider settings**: Each user configures their own AI credentials; keys are stored encrypted and never shared
- **OAuth Integration**: Notion account linking for external platform sync; Google OAuth for authentication
- **Passwordless authentication**: Email magic links and OAuth — no passwords stored
- **Telemetry & Admin Console**: System health monitoring, error tracking, and runtime logs

## Architecture

```
BookMind-Nexus/
├── Dockerfile                 # Spring Boot app container image
├── docker-compose.yml         # Multi-service orchestration (PostgreSQL + app)
├── .gitignore                 # Git ignore rules
├── pom.xml                    # Maven build file
├── README.md                  # This file
├── LICENSE                    # MIT License
├── book-mind-nexus/           # Main web application module
│   ├── pom.xml
│   └── src/
│       └── main/java/com/bookassistant/
│           ├── controller/    # MVC controllers (web, AI, study, admin)
│           ├── service/       # Business logic layer
│           ├── entity/        # JPA domain entities
│           ├── repository/    # Spring Data JPA repositories
│           ├── web/renderer/  # Pure Java HTML renderer (HtmlBuilder, PageContext, etc.)
│           └── ...            # Additional packages
├── ai-gateway/                # AI microservice module (Spring Boot)
│   ├── pom.xml
│   └── src/
│       └── main/java/com/bookmind/gateway/
│           ├── core/          # Gateway models and service
│           ├── providers/     # AI provider adapters (Ollama, OpenAI, Anthropic, Google Vertex)
│           ├── controller/    # Gateway REST endpoints
│           └── telemetry/     # Telemetry recording
└── src/                       # Root resources (config, SQL schemas, messages)
```

## Quick Start (Docker)

```bash
# Clone the repo
git clone https://github.com/samirmina/BookMind-Nexus.git
cd BookMind-Nexus

# Copy environment config
cp .env.example .env

# Start all services
docker compose up --build

# Access the app at http://localhost:8083
```

### Docker Services

| Service | Port |
|---------|------|
| BookMind Nexus App | 8083 |
| AI Gateway | 8081 |
| PostgreSQL + pgvector | 5432 |
| Ollama (local AI) | 11434 |

## Configuration

Edit `book-mind-nexus/src/main/resources/application.properties` for local development or `application-docker.properties` for Docker deployment.

Key settings:
- `app.ai.model` — AI model (default: `llama3.2:3b`)
- `app.extraction.mode` — `pix2text`, `text-layer`, or `model-only`
- `spring.datasource.url` — PostgreSQL connection string

### Environment Variables (.env)

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

PostgreSQL with the `pgvector` extension for semantic search. Schema is initialized automatically on startup via `schema.sql` and migration scripts.

Key tables:
- `users` — User accounts
- `user_ai_config` — Per-user AI provider configuration
- `books` — Uploaded books
- `book_chunks` — RAG chunks with vector embeddings
- `linked_accounts` — OAuth linked accounts

## AI Providers

Each user can configure their own AI provider:

| Provider | Base URL | Example Models |
|----------|----------|----------------|
| Ollama (Local) | `http://localhost:11434` | `llama3.2:3b`, `nomic-embed-text` |
| OpenAI | `https://api.openai.com/v1` | `gpt-4o`, `gpt-4o-mini` |
| Anthropic (Claude) | `https://api.anthropic.com` | `claude-3-5-sonnet` |
| Google Vertex AI / Gemini | Vertex endpoint | `gemini-1.5-pro`, `gemini-1.5-flash` |
| OpenAI-Compatible | Your endpoint | Any OpenAI-compatible model |

## Authentication

- **Passwordless**: Email magic links and Google OAuth 2.0 — no passwords stored
- **Per-user API keys**: Stored encrypted in database, never logged
- **Session management**: HttpOnly cookies, 30-minute timeout

## License

MIT License. See [LICENSE](LICENSE).