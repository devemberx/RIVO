#!/usr/bin/env python3
"""Export a still candidate to lossless WebP, preserving decoded pixels and canvas."""

import argparse
import hashlib
import io
from pathlib import Path

from PIL import Image


def export_webp(source, destination):
    source, destination = Path(source), Path(destination)
    if destination.suffix.lower() != ".webp":
        raise ValueError("destination must use .webp")
    data = source.read_bytes()
    with Image.open(io.BytesIO(data)) as image:
        if getattr(image, "n_frames", 1) != 1:
            raise ValueError("expected a single still image")
        if image.getexif().get(274, 1) != 1:
            raise ValueError("orientation metadata must be normalized before export")
        pixels = image.convert("RGBA")
    buffer = io.BytesIO()
    pixels.save(buffer, format="WEBP", lossless=True, method=6, exact=True)
    encoded = buffer.getvalue()
    with Image.open(io.BytesIO(encoded)) as result:
        if result.size != pixels.size or result.convert("RGBA").tobytes() != pixels.tobytes():
            raise ValueError("WebP export changed canvas or decoded pixels")
    destination.parent.mkdir(parents=True, exist_ok=True)
    with destination.open("xb") as output:
        output.write(encoded)
    return {"source_sha256": hashlib.sha256(data).hexdigest(),
            "webp_sha256": hashlib.sha256(encoded).hexdigest(),
            "source_bytes": len(data), "webp_bytes": len(encoded)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()
    try:
        print(export_webp(args.source, args.destination))
    except (OSError, ValueError) as error:
        parser.exit(1, str(error) + "\n")


if __name__ == "__main__":
    main()
