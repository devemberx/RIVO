#!/usr/bin/env python3
"""Package a trusted local Canvas renderer into a portable comparison review."""

import argparse
import base64
import hashlib
import io
import json
import math
from pathlib import Path

from PIL import Image


def prepare(spec_path, output):
    spec_path, output = Path(spec_path).resolve(), Path(output)
    raw = spec_path.read_bytes()
    spec = json.loads(raw)
    base = spec_path.parent

    def read_local(relative):
        path = (base / relative).resolve()
        if Path(relative).is_absolute() or not path.is_relative_to(base):
            raise ValueError("Inputs must stay within the spec directory")
        return path.read_bytes()

    duration = spec["duration_ms"]
    if type(duration) not in (int, float) or not math.isfinite(duration) or duration <= 0:
        raise ValueError("duration_ms must be finite and positive")
    if spec.get("loop") is not True:
        raise ValueError("Comparison supports looping clips; use prepare_review.py for one-shots")
    for key in ("canvas_px", "display_sizes_css_px"):
        values = spec[key]
        if not isinstance(values, list) or not values or any(type(v) is not int or v <= 0 for v in values):
            raise ValueError(f"Invalid {key}")
    if len(spec["canvas_px"]) != 2:
        raise ValueError("canvas_px must contain width and height")
    if not spec["variants"] or any(not v.get("id") or not v.get("label") for v in spec["variants"]):
        raise ValueError("Each variant needs an id and label")
    if len({v["id"] for v in spec["variants"]}) != len(spec["variants"]):
        raise ValueError("Variant ids must be unique")
    for sample in spec["samples"]:
        ms = sample["ms"]
        if type(ms) not in (int, float) or not math.isfinite(ms) or not 0 <= ms <= duration:
            raise ValueError("Samples must be within the loop")
    renderer = read_local(spec["renderer_js"])
    source = renderer.decode("utf-8")
    if "</script" in source.lower():
        raise ValueError("Renderer must not contain an HTML script terminator")
    inventory = {"status": "prepared_unreviewed", "spec_sha256": hashlib.sha256(raw).hexdigest(),
                 "renderer_sha256": hashlib.sha256(renderer).hexdigest(), "assets": {}}
    images = {}
    for name, relative in spec["assets"].items():
        data = read_local(relative)
        with Image.open(io.BytesIO(data)) as image:
            if getattr(image, "n_frames", 1) != 1:
                raise ValueError("Use static images or atlases, not implicitly timed animated images")
            image.load()
            mime = Image.MIME[image.format]
            inventory["assets"][name] = {"path": relative, "sha256": hashlib.sha256(data).hexdigest(), "size": list(image.size)}
        images[name] = f"data:{mime};base64," + base64.b64encode(data).decode("ascii")
    payload = json.dumps({**spec, "images": images}, ensure_ascii=False).replace("<", "\\u003c")
    template = (Path(__file__).parent.parent / "assets/comparison.html").read_text()
    # Substitute simultaneously: user text cannot introduce another template marker.
    import re
    html = re.sub(r"__REVIEW_SPEC__|__RENDERER__", lambda m: payload if m[0] == "__REVIEW_SPEC__" else source, template)
    output.mkdir(parents=True, exist_ok=False)
    (output / "preview.html").write_text(html)
    inventory["preview_sha256"] = hashlib.sha256(html.encode()).hexdigest()
    inventory["limits"] = "Unreviewed. Browser preview does not prove Android rendering or device performance."
    (output / "inventory.json").write_text(json.dumps(inventory, indent=2) + "\n")
    return inventory


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--spec", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    prepare(args.spec, args.out)
