from __future__ import annotations

from pathlib import Path


def extract_text_from_image(path: Path) -> str:
    try:
        from PIL import Image
        import pytesseract

        with Image.open(path) as image:
            return pytesseract.image_to_string(image, lang="chi_sim+eng").strip()
    except Exception:
        return ""

