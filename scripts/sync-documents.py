"""Import the ISP PDFs and their accessible, page-by-page text.

Usage: python scripts/sync-documents.py [--source PATH_TO_PDFS]
Requires: python -m pip install pypdf
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import shutil

from pypdf import PdfReader


LAB_IDS = ["isp-secrecy", "isp-integrity", "isp-ae-pke", "isp-keyagreement"]
DOCUMENTS = [
    {
        "id": "quick-reference",
        "source": "Cryptography_Algorithms_Quick_Reference.pdf",
        "title": "Cryptography Algorithms at a Glance",
        "description": "A compact reference to symmetric encryption, hashes, authentication, public-key cryptography, signatures and key derivation.",
        "category": "script",
        "labIds": LAB_IDS,
    },
    {
        "id": "exam-study-script",
        "source": "Cryptography_Complete_Exam_Study_Script.pdf",
        "title": "Complete Exam Study Script",
        "description": "A complete revision guide following all four lecture decks, with formulas, worked explanations and oral-exam questions.",
        "category": "script",
        "labIds": LAB_IDS,
    },
    {
        "id": "01-communication-secrecy",
        "source": "1.pdf",
        "title": "Communication Secrecy",
        "description": "Perfect secrecy, one-time pads, pseudorandom generators, stream ciphers, block ciphers and modes of operation.",
        "category": "slides",
        "labIds": ["isp-secrecy"],
    },
    {
        "id": "02-integrity-authenticated-encryption",
        "source": "2.pdf",
        "title": "Integrity & Authenticated Encryption",
        "description": "Message authentication codes, ECBC-MAC, collision-resistant hashes, HMAC and authenticated encryption.",
        "category": "slides",
        "labIds": ["isp-integrity"],
    },
    {
        "id": "03-authenticated-public-key-encryption",
        "source": "3.pdf",
        "title": "Authenticated & Public-key Encryption",
        "description": "Ciphertext integrity, chosen-ciphertext security, authenticated encryption, trapdoor functions, RSA and OAEP.",
        "category": "slides",
        "labIds": ["isp-ae-pke"],
    },
    {
        "id": "04-key-exchange",
        "source": "4.pdf",
        "title": "Key Exchange",
        "description": "Trusted third parties, Diffie-Hellman, digital signatures, RSA-PSS and key derivation.",
        "category": "slides",
        "labIds": ["isp-keyagreement"],
    },
]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=Path.home() / "Downloads")
    args = parser.parse_args()
    missing = [entry["source"] for entry in DOCUMENTS if not (args.source / entry["source"]).is_file()]
    if missing:
        raise SystemExit("Missing PDF files: " + ", ".join(missing))

    root = Path(__file__).resolve().parents[1]
    output = root / "content" / "documents"
    output.mkdir(parents=True, exist_ok=True)
    documents = []
    for entry in DOCUMENTS:
        source = args.source / entry["source"]
        filename = entry["id"] + ".pdf"
        destination = output / filename
        reader = PdfReader(source)
        page_text = [(page.extract_text() or "").strip() for page in reader.pages]

        shutil.copy2(source, destination)
        if sha256(source) != sha256(destination):
            raise RuntimeError(f"PDF copy verification failed: {filename}")

        text_filename = entry["id"] + ".text.json"
        (output / text_filename).write_text(
            json.dumps({"pages": page_text}, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        documents.append({
            "id": entry["id"],
            "title": entry["title"],
            "description": entry["description"],
            "category": entry["category"],
            "url": "content/documents/" + filename,
            "textUrl": "content/documents/" + text_filename,
            "pages": len(reader.pages),
            "size": destination.stat().st_size,
            "labIds": entry["labIds"],
        })
        print(f"Imported {filename}: {len(reader.pages)} pages (SHA-256 verified)")

    (root / "content" / "documents.json").write_text(
        json.dumps({"documents": documents}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
