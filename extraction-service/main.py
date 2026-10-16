"""
BookMind Nexus Extraction Service
====================================
Accepts PDF uploads via HTTP and returns extracted text with LaTeX math notation.
Uses Pix2Text exclusively for all page/document processing.

Usage:
    python main.py

Endpoints:
    POST /extract/pix2text
        Upload a PDF file, receive extracted text chunks in response using Pix2Text.

    GET /health
        Health check endpoint.
"""

import tempfile
import os
import re
import logging
from pathlib import Path
from typing import List, Optional

from fastapi import FastAPI, UploadFile, File, HTTPException, Query
from fastapi.responses import JSONResponse
from pydantic import BaseModel

logger = logging.getLogger("uvicorn")
logging.basicConfig(level=logging.INFO)

app = FastAPI(
    title="BookMind Nexus Extraction Service",
    description="Extracts text and math notation from PDFs using Pix2Text.",
    version="2.0.0",
)


class ExtractionResponse(BaseModel):
    """Single extracted text chunk."""
    page: int
    text: str
    metadata: Optional[dict] = None


class ExtractionResult(BaseModel):
    """Full extraction result."""
    filename: str
    pages: int
    chunks: List[ExtractionResponse]
    raw_markdown: Optional[str] = None


def _pdf_to_images(pdf_path: str) -> List:
    """Convert PDF pages to PIL Images using pypdfium2 (no poppler required)."""
    import pypdfium2
    pdf = pypdfium2.PdfDocument(pdf_path)
    images = []
    for page in pdf:
        bitmap = page.render(scale=2)
        images.append(bitmap.to_pil())
    pdf.close()
    return images


def _extract_text_from_pix2text_result(result) -> str:
    """
    Extract readable text from Pix2Text result object.
    Pix2Text returns markdown-like output with LaTeX math blocks.
    """
    text = None
    if hasattr(result, 'markdown') and result.markdown:
        text = result.markdown
    elif hasattr(result, 'text') and result.text:
        text = result.text

    if text is None:
        text = str(result)
        # Strip any DTO/metadata leakage from stringified objects
        text = re.sub(r'\s*box\s*:\s*\[[^\]]*\]', '', text)
        text = re.sub(r'\s*score\s*:\s*[^,\}\s]+', '', text)
        text = re.sub(r'\s*position\s*:\s*[^,\}\s]+', '', text)
        text = re.sub(r'\s*id\s*:\s*[^,\}\s]+', '', text)

    # Clean up excessive newlines while preserving structure
    text = re.sub(r'\n{3,}', '\n\n', text)
    return text.strip()


@app.post("/extract/pix2text", response_model=ExtractionResult)
async def extract_pix2text(
    file: UploadFile = File(...),
    start_page: int = Query(0, ge=0),
    end_page: int = Query(0, ge=0)
):
    """
    Accepts a PDF file upload, extracts text and math notation using Pix2Text,
    and returns structured page chunks with LaTeX math notation.
    
    Optional query parameters:
    - start_page: 1-based start page (inclusive). 0 means start from first page.
    - end_page: 1-based end page (inclusive). 0 means process all pages.
    """
    if not file.filename.lower().endswith(".pdf"):
        raise HTTPException(status_code=400, detail="Only PDF files are supported.")

    tmp_path = None
    try:
        # Save uploaded file to temp location
        suffix = Path(file.filename).suffix
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
            content = await file.read()
            tmp.write(content)
            tmp_path = tmp.name

        logger.info("Processing PDF with Pix2Text: %s", file.filename)

        # Initialize Pix2Text with GPU support and more sensitive math/table detection
        from pix2text import Pix2Text
        p2t = Pix2Text.from_config(
            device='cuda',
            enable_formula=True,
            enable_table=True,
            mfd_config={"confidence_threshold": 0.25},
        )

        def _recognize_page(image, page_number):
            return p2t.recognize_page_single_call(
                image,
                page_number=page_number,
            )

        # Convert PDF pages to images
        images = _pdf_to_images(tmp_path)
        logger.info("Converted PDF to %d images", len(images))

        chunks: List[ExtractionResponse] = []

        for page_idx, image in enumerate(images, start=1):
            if start_page > 0 and page_idx < start_page:
                continue
            if end_page > 0 and page_idx > end_page:
                break
            
            try:
                page_text = _recognize_page(image, page_idx)
                
                if page_text.strip():
                    chunks.append(
                        ExtractionResponse(
                            page=page_idx,
                            text=page_text.strip(),
                            metadata={
                                "chars": len(page_text),
                                "backend": "pix2text",
                            },
                        )
                    )
            except Exception as page_exc:
                logger.warning(
                    "Pix2Text failed on page %d: %s", page_idx, page_exc
                )
                continue

        logger.info(
            "Pix2Text extraction complete | pages=%d | range=%d-%d | file=%s",
            len(chunks), start_page, end_page, file.filename,
        )

        if not chunks:
            raise HTTPException(
                status_code=500,
                detail="Pix2Text produced no output. Check that the models are downloaded.",
            )

        # Combine all page texts for raw_markdown
        full_text = '\n\n'.join(c.text for c in chunks)

        return ExtractionResult(
            filename=file.filename,
            pages=len(chunks),
            chunks=chunks,
            raw_markdown=full_text,
        )

    except HTTPException:
        raise
    except Exception as exc:
        logger.exception("Failed to extract PDF with Pix2Text: %s", exc)
        raise HTTPException(status_code=500, detail=str(exc)) from exc

    finally:
        # Clean up temp file
        if tmp_path and os.path.exists(tmp_path):
            try:
                os.unlink(tmp_path)
            except OSError:
                pass


@app.get("/health")
def health():
    """Health check."""
    return {"status": "ok"}
