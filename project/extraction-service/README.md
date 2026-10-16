# Pix2Text Extraction Service

Python microservice that extracts text and LaTeX math notation from PDFs using Pix2Text.

> **Note:** This service is only used when `app.extraction.mode=pix2text`. When using `model-only` mode, this service is bypassed entirely and native PDFBox text is fed directly to the LLM.

## Setup

```powershell
task setup
```

This creates the virtual environment and installs dependencies.

## Commands

All commands are run from the project root via Task:

```powershell
task start-extraction    # Start service on port 8080
task stop-extraction     # Stop service
task restart-extraction  # Restart service
task status-extraction   # Check if running
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
| `app.extraction.mode` | `pix2text` | Extraction backend |
| `app.extraction.pix2text.url` | `http://localhost:8080` | Extraction service URL |
| `app.extraction.pix2text.timeout` | `600s` | Request timeout |
