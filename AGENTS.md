# AGENTS.md — BookMind Nexus

## Core Identity & Project Philosophy

BookMind Nexus is a **Smart Book Extractor**, not an AI summarizer.

**The goal is not for the AI to summarize the book for you. The goal is for the AI to extract the important paragraphs and sentences from the book itself so you can read them yourself.**

When a user uploads a PDF, they want to engage with the book's actual content — not a condensed version rewritten by an AI. The AI's role is purely **extraction and retrieval**: scanning the full text, identifying the passages that matter most for the user's question or reading goal, and surfacing those exact passages so the user reads the author's original words.

This distinction is fundamental. Every feature in the system is built around this extraction-first principle. Where the AI does generate structure (concept maps, risk flags, learning paths), it does so by **organizing extracted passages**, not by inventing new content.

### How This Differs from Regular AI Summarization Tools

| | BookMind Nexus | Regular AI Summarizer |
|---|---|---|
| **Output** | Verbatim passages from the book | AI-generated summary in the model's own words |
| **User experience** | You read the author's actual text | You read the model's interpretation |
| **Accuracy** | Exact, citable, traceable to page numbers | Approximate, potentially hallucinated |
| **Trust** | High — nothing is added or removed | Lower — model may omit or distort content |

### Why Users Upload Books

- To save time finding the right passages in long books.
- To study key concepts without reading every page.
- To compare their own notes against the book's actual content.
- To prepare for exams, meetings, or research by extracting targeted evidence.

### The Role of AI

- **Primary role**: Extraction. The AI scans book text and copies the passages most relevant to the user's need, preserving exact wording and page citations.
- **Secondary role**: Structuring. For features like concept maps and risk flags, the AI organizes extracted passages into structured formats. It does not invent content.
- **Forbidden role**: Summarization in the model's own words. The prompts explicitly forbid paraphrasing, interpreting, or condensing.

## Non-Negotiable Rules

1. **No Destructive Fixes**: Before applying any mass replacement, backup the affected files. Edit only the specific keys that are defective. Any command that modifies or deletes project files must be reversible.
2. **Verify Before Declaring Success**: Unit tests passing is necessary but NOT sufficient. The app must start and the affected page/flow must load without exceptions. Always do a runtime smoke test.
3. **Database Safety**: Never alter the live database schema without first checking the corresponding JPA entity field type. If `hibernate.hbm2ddl.auto=validate` is set, schema must match entities exactly.
4. **No Translation Services — AI Native Multilingualism**: Translation services are forbidden. **Book content must remain in the book's original language.** AI-generated structural content must use the requested UI language. No post-processing translation is allowed.
5. **All-Languages-Required for New Text**: Every new user-facing string MUST be added to ALL `messages_XX.properties` bundles simultaneously. This applies to UI text only, not to AI-extracted book content.
6. **No Hardcoded Language Text**: No user-facing text may ever be hardcoded in any template. Every string must come from a `messages_XX.properties` bundle. Note: This applies to UI text only. AI-extracted book content remains in the book's original language.
7. **No Builds Unless Explicitly Requested**: Do NOT run `mvn package`, `mvn install`, or any command that produces a build artifact unless explicitly requested. Development is done via `mvn spring-boot:run` or the IDE directly.
8. **Ask Before Long-Running or Test Waits**: If a task requires leaving a server or background process running for the user to test, ask first. Do not start or keep services running speculatively.

## How This Agent Should Work

- Always think step by step before answering.
- Provide the solution first, then the explanation (Code First, Explanation Second).
- Be concise and direct; don't be unnecessarily long-winded.
- If you need clarification or additional information, ask very briefly.
- Your response should be organized and easy to read, using appropriate formatting.
- Use the latest best practices in programming.
- Your personality: Professional, confident, and patient.
- You have access to powerful code execution tools. Use them whenever you need to run, test, or verify code.

### Core Principle: No Incomplete Circles

Every task must be closed end-to-end before moving on. A feature is not "done" when code is written — it is done when the user can actually use it and observe it working. Stubs, TODO comments, and database migrations that break startup are not acceptable as final state.

### Working with This Project

- **Task boundaries**: Stay within the project directory unless explicitly asked to access external resources. Do not browse the web or access external systems unless the task requires it.
- **Verification**: After making changes, verify they work. Run the app, check the logs, confirm the UI renders correctly. Never stop after the first green light.
- **Communication**: Report what you changed, why you changed it, and how you verified it. Keep updates concise and technical.
- **Safety**: Never expose secrets, keys, or tokens. Never commit unless explicitly asked. Track what was changed and why.

## Current Project State (Reference)

- **AI Model**: `llama3.2:3b` (Ollama), embedding: `nomic-embed-text`
- **Default Extraction Mode**: `model-only` in Docker, `pix2text` in local WSL
- **Database**: PostgreSQL 16 + pgvector via Docker Compose
- **Ports**: App 8083, Postgres 5432, Ollama 11434, Pix2Text 8080
- **WSL Path**: `/mnt/d/book-assistant-lite/`
- **Docker Path**: `/app/` (inside containers)
- **GitHub**: `https://github.com/samirmina/BookMind-Nexus`
- **Task Runner**: go-task (`task` command), Taskfile.yml at project root
- **Docker Compose**: `docker-compose.yml` at project root (`/mnt/d/book-assistant-lite/`)
