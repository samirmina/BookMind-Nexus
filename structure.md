# BookMind Nexus — Project Structure

## Root Directory

```
/mnt/d/book-assistant-lite/
├── docker-compose.yml          # Multi-service Docker orchestration
├── .gitignore                  # Git ignore rules
├── structure.md                # This file
├── PROJECT_PROFILE.md          # CV/portfolio project profile
├── project/                    # Spring Boot application
│   ├── Dockerfile              # App container image
│   ├── Taskfile.yml            # go-task commands for Docker Compose
│   ├── .env.example            # Environment variable template
│   ├── .gitignore              # Project-level git ignore
│   ├── AGENTS.md               # Agent instructions & project rules
│   ├── README.md               # Project overview & quick start
│   ├── PROJECT-KNOWLEDGE.md    # Architecture & technical reference
│   ├── LICENSE                 # MIT License
│   ├── pom.xml                 # Maven build file
│   ├── package.json            # Node.js (frontend tooling)
│   ├── package-lock.json
│   ├── extraction-service/     # Pix2Text OCR microservice
│   │   ├── Dockerfile          # Pix2Text container image
│   │   ├── main.py             # FastAPI service entry point
│   │   ├── requirements.txt    # Python dependencies
│   │   └── README.md           # Extraction service docs
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/bookassistant/
│   │   │   │   ├── aop/              # Telemetry aspects
│   │   │   │   ├── config/           # Spring configuration
│   │   │   │   ├── controller/       # MVC controllers
│   │   │   │   │   ├── admin/
│   │   │   │   │   ├── ai/
│   │   │   │   │   ├── book/
│   │   │   │   │   ├── study/
│   │   │   │   │   ├── ui/
│   │   │   │   │   └── web/
│   │   │   │   ├── dto/              # Data transfer objects
│   │   │   │   ├── entity/           # JPA entities
│   │   │   │   │   └── telemetry/
│   │   │   │   │   └── ui/
│   │   │   │   ├── model/            # Domain models
│   │   │   │   ├── repository/       # Spring Data JPA repositories
│   │   │   │   │   ├── telemetry/
│   │   │   │   │   └── ui/
│   │   │   │   ├── service/          # Business logic
│   │   │   │   │   ├── ai/
│   │   │   │   │   ├── auth/
│   │   │   │   │   ├── config/
│   │   │   │   │   ├── file/
│   │   │   │   │   ├── integration/
│   │   │   │   │   ├── infrastructure/
│   │   │   │   │   ├── rag/
│   │   │   │   │   ├── telemetry/
│   │   │   │   │   ├── text/
│   │   │   │   │   └── ui/
│   │   │   │   ├── session/          # BookSession
│   │   │   │   ├── tool/             # ImageExtractor
│   │   │   │   └── util/             # Utilities
│   │   │   └── resources/
│   │   │       ├── application.properties      # Local (WSL) config
│   │   │       ├── application-docker.properties # Docker config
│   │   │       ├── data.sql                    # Seed data
│   │   │       ├── messages.properties         # English messages
│   │   │       ├── messages_XX.properties      # 12 additional locales
│   │   │       ├── schema.sql                  # Database schema
│   │   │       ├── schema-migration.sql
│   │   │       ├── schema-telemetry.sql
│   │   │       ├── schema-telemetry-triggers.sql
│   │   │       ├── schema-ui-validation.sql
│   │   │       ├── static/
│   │   │       │   ├── styles.css          # Modern CSS design system
│   │   │       │   ├── js/
│   │   │       │   │   ├── collapsible.js
│   │   │       │   │   └── ui-audit.js
│   │   │       └── templates/
│   │   │           ├── about.html
│   │   │           ├── contact.html
│   │   │           ├── home.html
│   │   │           ├── index.html
│   │   │           ├── accounts/index.html
│   │   │           ├── admin/console.html
│   │   │           ├── auth/login.html
│   │   │           ├── auth/register.html
│   │   │           └── outputs/index.html
│   │   └── test/
│   │       └── java/com/bookassistant/  # JUnit tests
│   ├── cache/                  # Runtime cache directory
│   ├── completion/             # go-task shell completions
│   ├── external/               # External data (cache, tmp, logs, pdfs)
│   ├── storage/                # Book storage (user uploads)
│   ├── target/                 # Maven build output (gitignored)
│   ├── task.exe                # go-task Windows binary
│   └── ui-screenshots/         # UI screenshot directory
```

## Key Configuration Files

| File | Purpose |
|------|---------|
| `project/src/main/resources/application.properties` | Local WSL configuration |
| `project/src/main/resources/application-docker.properties` | Docker deployment configuration |
| `docker-compose.yml` | Docker Compose service definitions |
| `project/Taskfile.yml` | go-task command definitions |
| `project/.env.example` | Environment variable template |

## Runtime Directories (Gitignored)

- `project/target/` — Maven build output
- `project/external/` — Cache, temp files, logs, PDFs
- `project/storage/books/` — User book uploads
- `project/ui-screenshots/` — UI screenshots
