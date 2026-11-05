# BookMind Nexus

AI-powered PDF book extraction and study platform. Pure Java 21 backend built with Spring Boot 3, backed by PostgreSQL 16 + pgvector, with an optional AI Gateway microservice supporting multiple LLM providers.

BookMind Nexus extracts verbatim passages from uploaded books so you read the author's actual words with page citations, not an AI interpretation. Every extracted passage is traceable to its exact source page.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 3.5 |
| Build | Maven |
| Database | PostgreSQL 16 + pgvector |
| AI Gateway | Spring Boot 3.5 (Ollama, OpenAI, Anthropic, Google Vertex) |
| Container | Docker / Docker Compose |
| PDF parsing | Apache PDFBox 3.0.2 |

## Architecture

```
BookMind-Nexus/
├── docker-compose.yml              # Multi-service orchestration
├── pom.xml                         # Maven parent (multi-module)
├── README.md                       # This file
├── book-mind-nexus/                # Main web application
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/bookassistant/
│       ├── BookAssistantLiteApplication.java
│       ├── aop/                    # Aspect-oriented cross-cutting concerns
│       ├── config/                 # Security, AI props, headers, rate limiting, async
│       ├── controller/             # MVC controllers (web, AI, study, admin)
│       │   ├── admin/              # Admin console endpoints
│       │   ├── ai/                 # QA, search, summary, analysis
│       │   ├── book/               # Upload, extraction progress (SSE)
│       │   ├── study/              # Comprehension, action plan
│       │   ├── ui/                 # (reserved)
│       │   └── web/                # Auth, OAuth, account, settings, outputs
│       ├── dto/                    # Data transfer objects
│       ├── entity/                 # JPA domain entities (User, Book, BookChunk, …)
│       ├── model/                  # BookData, cache, page models
│       ├── repository/             # Spring Data JPA repositories
│       ├── security/               # Token encryption, encrypted string converter
│       ├── service/                # Business logic (AI, auth, file, infra, integration, rag, text)
│       ├── session/                # Session-scoped book state
│       ├── tool/                   # (reserved)
│       ├── util/                   # Logging, sanitization, string utils
│       └── web/                    # (reserved)
│       src/main/resources/
│           ├── application*.properties
│           ├── data.sql            # Seed data (development only)
│           └── messages_*.properties
├── ai-gateway/                     # AI microservice
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/bookmind/gateway/
│       ├── core/                   # Gateway models & orchestration service
│       ├── controller/             # Gateway REST endpoints
│       ├── providers/              # Ollama, OpenAI, Anthropic, Google Vertex adapters
│       ├── telemetry/              # Gateway telemetry recording
├── src/                            # Root resources (config, SQL schemas, messages)
├── Dockerfile.ollama               # Ollama LLM service Dockerfile
├── .env                            # Environment variables (gitignored)
└── extraction-service/             # Pix2Text PDF extraction microservice
    ├── Dockerfile
    ├── main.py
    └── requirements.txt
```

## Prerequisites

- Java 21 (JDK) for local builds
- Maven 3.9+
- Docker and Docker Compose (for containerized deployment)
- PostgreSQL 16 with the `pgvector` extension (production) — provisioned via Neon
- An AI provider: Ollama (local), OpenAI, Anthropic, or Google Vertex AI
- Render account (for cloud deployment)
- Neon account (for managed PostgreSQL)

## Configuration

Copy the environment template and fill in real values before running:

```bash
cp .env.example .env
# Edit .env with your values
```

Key environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `SERVER_PORT` | App HTTP port | `8083` |
| `SESSION_TIMEOUT_SECONDS` | Cookie/session timeout (s) | `1800` |
| `APP_ENCRYPTION_KEY` | **Required.** Base64-encoded 256-bit key for OAuth token encryption | (none) |
| `DB_USERNAME` / `DB_PASSWORD` / `DB_NAME` | PostgreSQL credentials | `postgres` / (none) / `book_assistant` |
| `AI_BASE_URL` | AI provider endpoint | `http://localhost:11434/api/generate` |
| `AI_MODEL` | Chat model | `phi3.5:latest` |
| `OLLAMA_API_KEY` | API key for non-local AI providers | (none) |
| `NOTION_OAUTH_CLIENT_ID` / `SECRET` / `REDIRECT_URI` | Notion OAuth credentials | (none) |
| `SPRING_PROFILES_ACTIVE` | Spring profile (`local`, `docker`, `prod`) | `docker` |

Generate the encryption key:

```bash
APP_ENCRYPTION_KEY=$(openssl rand -base64 32)
```

## Running with Docker Compose

```bash
docker compose up --build
```

Services:

| Service | Port |
|---------|------|
| BookMind Nexus App | 8083 |
| AI Gateway | 8081 |
| Ollama (local LLM) | 11434 |
| Pix2Text (PDF extraction) | 8080 |

The `postgres` service is intentionally **commented out** in `docker-compose.yml`. For production, provision PostgreSQL 16 + pgvector externally (e.g. Neon) and supply `DB_*` environment variables before the application can start. The app expects the `users`, `books`, `book_chunks`, `linked_accounts`, `external_notes`, `reading_progress`, and `book_outputs` tables to exist (see `data.sql` for the development seed only — it is not a schema).

Access the app at `http://localhost:8083`.

## Deploying on Render

BookMind Nexus is deployed on Render as four web services:

| Service | Render Name | URL |
|---------|-------------|-----|
| Main App | `bookmind-nexus-app` | `https://bookmind-nexus-app.onrender.com` |
| AI Gateway | `bookmind-nexus-ai-gateway` | `https://bookmind-nexus-ai-gateway.onrender.com` |
| Ollama LLM | `bookmind-nexus-ollama` | `https://bookmind-nexus-ollama.onrender.com` |
| Pix2Text Extraction | `bookmind-nexus-pix2text` | `https://bookmind-nexus-pix2text.onrender.com` |

Database: Neon (PostgreSQL 16 + pgvector), project `book-mind-nexus`.

All services are built from Dockerfiles and deployed from the `feat/client-side-book-tool-and-deployment-cleanup` branch. Environment variables are set in the Render dashboard (see `.env.example` for the full list; `APP_ENCRYPTION_KEY` and `DB_PASSWORD` are required).

## Running locally

```bash
./mvnw -pl book-mind-nexus spring-boot:run \
  -Dspring-boot.run.profiles=local \
  -DAPP_ENCRYPTION_KEY=<base64-256-bit-key>
```

Set `SPRING_PROFILES_ACTIVE=local` and provide `DB_*` and `APP_ENCRYPTION_KEY` via environment or `application-local.properties`.

## Security

- **OAuth tokens at rest**: `access_token` and `refresh_token` are encrypted with AES-GCM using `APP_ENCRYPTION_KEY`. The app refuses to start if the key is missing or not exactly 256 bits.
- **Passwords**: stored as BCrypt hashes; minimum length 6 characters.
- **Sessions**: `HttpOnly`, `Secure`, `SameSite=Strict` cookies; session fixation migration on login; max one session per user.
- **Security headers**: CSP, `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy`, `Permissions-Policy`, `Strict-Transport-Security`.
- **Rate limiting**: sliding-window limit on `/login` and `/register` (10 attempts per minute per client IP). Behind a trusted proxy, set `RATE_LIMIT_TRUST_FORWARDED=true` and ensure the proxy is the only source of `X-Forwarded-For`.
- **SSRF guard**: `app.ai.base-url` is validated; private/link-local/RFC1918 targets are rejected unless an explicit loopback (`localhost`, `127.0.0.1`, `::1`).
- **Prompt injection**: user-supplied content is scanned for instruction-override patterns before being sent to the AI model.
- **Uploads**: PDF filenames are sanitized; resolved write paths are confined to the configured PDF source directory. 50 MB upload cap.
- **XSS mitigation**: AI/book content rendered through an allow-listed Markdown-to-HTML pipeline; single quotes escaped; `javascript:` and `data:` links neutralized.
- **IDOR**: book, output, and linked-account operations verify ownership by the authenticated user.

## Known limitations / deployment notes

- Database is hosted on Neon (PostgreSQL 16 + pgvector); the `postgres` service remains commented out in `docker-compose.yml` for local dev.
- `data.sql` seeds a demo user (`demo@bookassistant.local`) and is intended for development only — do not run it against production.
- Sessions are in-memory; horizontal scaling requires external session storage (Spring Session + Redis).
- AI provider API keys passed in URLs (e.g. Google Vertex `?key=`) may appear in proxy/load-balancer access logs.
- There is no book-delete endpoint; uploaded PDF files are not automatically reclaimed.

## API endpoints

Auth (session-based):
- `GET/POST /register`, `GET/POST /login`, `POST /logout`

Books:
- `POST /upload` — multipart PDF upload; returns a job ID for SSE progress
- `GET /upload/progress/{jobId}` — Server-Sent Events progress stream
- `POST /books/{bookId}/load` — load a book into the session

AI / Study:
- `POST /qa` — question answering
- `POST /semantic-search`
- `POST /summary/general`, `POST /summary/pages`
- `POST /reality-check`, `POST /concept-map`, `POST /risk-flags`
- `POST /comprehension`, `POST /action-plan`, `POST /learning-path`

Accounts / OAuth:
- `GET/POST /accounts`, `POST /accounts/link`, `POST /accounts/unlink`, `POST /accounts/sync`
- `GET /oauth2/authorize?platform=notion`, `GET /oauth2/callback/{platform}`
- `GET/POST /settings/extraction-mode`

AI Gateway:
- `POST /api/gateway/execute`, `POST /api/gateway/execute/stream`
- `POST /api/gateway/embed`, `GET /api/gateway/providers`, `GET /api/gateway/health`

## Contributing

Conventional Commits are required (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`). Work on `feature` branches off `develop`; never commit directly to `main`. API keys and passwords must never appear in the code.

## License

MIT License.

## Client-Side Tool: BookClientTool

For large book files that are impractical to upload, `BookClientTool` runs entirely on the client
machine. It reads a PDF from the local filesystem, extracts text from a user-specified page range,
sends it to a chosen AI service (Ollama, OpenAI, Anthropic, or Google Vertex), and writes the
result to an output file. The web application invokes the tool and presents the result to the user
for saving — no book data is ever uploaded to the server.

```bash
# Build
./mvnw -pl book-mind-nexus package -DskipTests

# Run (example: pages 1-10 of a book via Ollama)
java -cp book-mind-nexus/target/classes:... \
  com.bookassistant.tool.BookClientTool \
  /path/to/book.pdf 1 10 ollama http://localhost:11434 /path/to/output.txt
```

CLI arguments:
| Position | Argument | Description |
|----------|----------|-------------|
| 1 | `book-path` | Absolute path to the source PDF |
| 2 | `start-page` | 1-based inclusive start page |
| 3 | `end-page` | 1-based inclusive end page |
| 4 | `ai-service` | `ollama`, `openai`, `anthropic`, or `google-vertex` |
| 5 | `api-key` | API key for the selected service (or Ollama base URL) |
| 6 | `output-file` | Path where the result will be written |

The tool requires PDFBox (already a project dependency) and Jackson for JSON parsing. It uses
`java.net.http.HttpClient` with bounded connect (10s) and read (300s) timeouts.

## License

MIT License.
