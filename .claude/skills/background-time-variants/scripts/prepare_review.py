#!/usr/bin/env python3
"""Check image integrity and prepare review boards; never approve scene geometry."""

import argparse
import hashlib
import io
import json
import math
import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageOps

PERIODS = ("midnight", "sunrise", "morning", "day", "afternoon", "sunset", "night")
VISUAL_CHECKS = (
    "framing_and_perspective",
    "building_and_terrain_geometry",
    "objects_and_infrastructure",
    "window_and_fixture_topology",
    "time_and_light_coherence",
    "art_quality_and_series_continuity",
)


def read_image(path):
    """Decode and hash the same byte snapshot, without editing the input file."""
    data = path.read_bytes()
    with Image.open(io.BytesIO(data)) as source:
        if getattr(source, "n_frames", 1) != 1:
            raise ValueError("expected a single still image")
        if source.getexif().get(274, 1) != 1:
            raise ValueError("orientation metadata must be normalized before review")
        source.load()
        rgba = source.convert("RGBA")
        metadata = {
            "path": str(path.resolve()),
            "sha256": hashlib.sha256(data).hexdigest(),
            "size": list(source.size),
            "format": source.format,
            "has_transparency": rgba.getchannel("A").getextrema()[0] < 255,
        }
    return rgba, metadata


def write_board(items, path, box=None):
    """Thumbnail/crop only comparison copies; source artwork remains untouched."""
    cell_width, picture_height, header_height = 480, 270, 34
    rows = math.ceil(len(items) / 2)
    board = Image.new("RGB", (cell_width * 2, rows * (picture_height + header_height)), "#171c24")
    draw = ImageDraw.Draw(board)
    for index, (label, picture) in enumerate(items):
        x = (index % 2) * cell_width
        y = (index // 2) * (picture_height + header_height)
        draw.text((x + 8, y + 10), label, fill="white")
        if picture is None:
            draw.text((x + 8, y + 80), "MISSING / UNREADABLE", fill="#ffaaa0")
            continue
        if box:
            width, height = picture.size
            crop = tuple(
                (math.floor if i < 2 else math.ceil)(value * (width if i % 2 == 0 else height))
                for i, value in enumerate(box)
            )
            picture = picture.crop(crop)
        background = Image.new("RGBA", picture.size, "#171c24")
        background.alpha_composite(picture)
        preview = ImageOps.contain(background.convert("RGB"), (cell_width - 16, picture_height - 8))
        board.paste(preview, (x + (cell_width - preview.width) // 2, y + header_height))
    board.save(path)


def prepare_review(master, variants, periods, regions, output_dir, scene_id):
    master = master.resolve()
    variants = {period: path.resolve() for period, path in variants.items()}
    output_dir = output_dir.resolve()
    for source in [master, *variants.values()]:
        if source.is_relative_to(output_dir):
            raise ValueError("review directory must not contain any source image")
    if output_dir.exists():
        raise ValueError("use a new review directory; existing review evidence is never overwritten")
    master_image, master_metadata = read_image(master)
    results, items = {}, [("MASTER", master_image)]
    known_hashes = {master_metadata["sha256"]: "master"}
    for period in periods:
        errors, warnings, metadata, picture = [], [], None, None
        path = variants.get(period)
        if path is None:
            errors.append("missing requested variant")
        else:
            try:
                picture, metadata = read_image(path)
                if metadata["size"] != master_metadata["size"]:
                    errors.append("canvas dimensions differ from master")
                if metadata["has_transparency"] != master_metadata["has_transparency"]:
                    errors.append("transparency presence differs from master")
                previous = known_hashes.get(metadata["sha256"])
                if previous:
                    warnings.append("identical file content to " + previous + "; review time appropriateness")
                known_hashes[metadata["sha256"]] = period
            except (OSError, ValueError, Image.DecompressionBombError) as error:
                errors.append("unreadable image: " + str(error))
        results[period] = {
            "input": str(path) if path else None,
            "metadata": metadata,
            "automatic_checks": "fail" if errors else "pass",
            "errors": errors,
            "warnings": warnings,
            "visual_checks": {check: "pending" for check in VISUAL_CHECKS},
            "verdict": "rejected" if errors else "pending_visual_review",
        }
        items.append((period.upper() + (" [AUTO FAIL]" if errors else " [REVIEW REQUIRED]"), picture))
    automatic_pass = all(result["automatic_checks"] == "pass" for result in results.values())
    report = {
        "scene_id": scene_id,
        "master": master_metadata,
        "periods": list(periods),
        "regions": regions,
        "variants": results,
        "automatic_checks": "pass" if automatic_pass else "fail",
        "verdict": "pending_visual_review" if automatic_pass else "rejected",
        "limitation": "Image integrity checks do not verify geometry, object identity or depicted time.",
    }
    output_dir.mkdir(parents=True)
    write_board(items, output_dir / "overview.png")
    for name, box in regions.items():
        write_board(items, output_dir / ("focus_" + name + ".png"), box)
    (output_dir / "review.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    lines = [
        "# " + scene_id + " visual review", "",
        "Automatic checks: **" + report["automatic_checks"] + "**. Final verdict: **" + report["verdict"] + "**.", "",
        "Check every original image and focused board. Record pass/fail/uncertain with landmark evidence.",
        "Do not accept until automatic checks and every visual check pass. Recheck hashes before export.", "",
        "Master: `" + master_metadata["path"] + "`",
        "Master SHA256: `" + master_metadata["sha256"] + "`", "",
    ]
    for period, result in results.items():
        lines += ["## " + period, "", "Automatic checks: **" + result["automatic_checks"] + "**."]
        if result["metadata"]:
            lines += ["Input SHA256: `" + result["metadata"]["sha256"] + "`"]
        lines += ["- Failure: " + error for error in result["errors"]]
        lines += ["- Warning: " + warning for warning in result["warnings"]]
        lines += ["", "| Visual check | Status | Landmark evidence |", "| --- | --- | --- |"]
        lines += ["| " + check + " | pending | |" for check in VISUAL_CHECKS]
        lines += ["", "Variant verdict: " + result["verdict"], ""]
    (output_dir / "review.md").write_text("\n".join(lines), encoding="utf-8")
    return report


def parse_regions(values):
    regions = {}
    for value in values:
        name, separator, coordinates = value.partition("=")
        if not separator or not re.fullmatch(r"[a-z][a-z0-9_]*", name) or name in regions:
            raise ValueError("each region needs a unique safe name: name=left,top,right,bottom")
        box = [float(coordinate) for coordinate in coordinates.split(",")]
        if len(box) != 4 or not all(math.isfinite(v) and 0 <= v <= 1 for v in box):
            raise ValueError("region needs four finite coordinates between 0 and 1")
        if box[0] >= box[2] or box[1] >= box[3]:
            raise ValueError("region must have positive width and height")
        regions[name] = box
    return regions


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scene-id", required=True)
    parser.add_argument("--master", required=True, type=Path)
    parser.add_argument("--variant", action="append", required=True, metavar="PERIOD=PATH")
    parser.add_argument("--periods", nargs="+", choices=PERIODS, default=PERIODS)
    parser.add_argument("--region", action="append", default=[], metavar="NAME=LEFT,TOP,RIGHT,BOTTOM")
    parser.add_argument("--output-dir", required=True, type=Path)
    args = parser.parse_args()
    try:
        if not re.fullmatch(r"[a-z][a-z0-9_]*", args.scene_id):
            raise ValueError("scene ID must use lowercase ASCII letters, digits and underscores")
        if len(set(args.periods)) != len(args.periods):
            raise ValueError("requested periods must be unique")
        variants = {}
        for value in args.variant:
            period, separator, path = value.partition("=")
            if not separator or not path or period not in args.periods or period in variants:
                raise ValueError("variant labels must be unique requested periods with nonempty paths")
            variants[period] = Path(path)
        report = prepare_review(args.master, variants, args.periods, parse_regions(args.region), args.output_dir, args.scene_id)
    except (OSError, ValueError, Image.DecompressionBombError) as error:
        parser.error(str(error))
    print("Automatic checks: " + report["automatic_checks"] + "; visual review remains required.")
    print("Review artifacts: " + str(args.output_dir.resolve()))
    return 0 if report["automatic_checks"] == "pass" else 1


if __name__ == "__main__":
    raise SystemExit(main())
