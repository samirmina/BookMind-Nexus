# Pix2Text Extraction Service

Python microservice that extracts text and LaTeX math notation from PDFs using Pix2Text.

> **Note:** This service is only used when `app.extraction.mode=pix2text`. When using `model-only` mode (the Docker default), this service is bypassed entirely and native PDFBox text is fed directly to the LLM.

## Setup

```powershell
task setup
```

This creates the virtual environment and installs dependencies.

## Commands

All commands are run from the project root via Task:

```powershell
task build-pix2text    # Build the Pix2Text Docker image
task start-pix2text    # Start service on port 8080
task stop-pix2text     # Stop service
task restart-pix2text  # Restart service
task logs-pix2text     # View service logs
task status            # Check if running
```

## API

```
POST http://localhost:8080/extract/pix2text
Content-Type: application/pdf

Response:
{
  "filename": "book.pdf",
  "pages": 10,
  "chunks": [
    {"page": 1, "text": "..."},
    ...
  ]
}
```

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `app.extraction.mode` | `model-only` | Extraction backend (`pix2text`, `text-layer`, or `model-only`) |
| `app.extraction.pix2text.url` | `http://pix2text:8080` | Extraction service URL (Docker) / `http://localhost:8080` (local) |
| `app.extraction.pix2text.timeout` | `86400s` | Request timeout |