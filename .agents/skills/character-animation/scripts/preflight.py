#!/usr/bin/env python3
"""Check reference files and optionally match a prior, explicit input review."""

import argparse
import hashlib
import json
import math
import sys
from pathlib import Path

from PIL import Image


def require(condition, message):
    if not condition:
        raise ValueError(message)


class Gate:
    def __init__(self, repo):
        self.repo = Path(repo).resolve()
        self.checked = {}

    def file(self, relative):
        require(isinstance(relative, str) and relative, "Missing repository-relative path")
        require(not Path(relative).is_absolute(), f"Absolute path: {relative}")
        path = (self.repo / relative).resolve()
        require(path.is_relative_to(self.repo), f"Path outside repository: {relative}")
        require(path.is_file(), f"Missing file: {relative}")
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        self.checked[relative] = digest
        return path, digest

    def document(self, relative):
        path, _ = self.file(relative)
        value = json.loads(path.read_text())
        require(isinstance(value, dict), f"Expected JSON object: {relative}")
        require(value.get("schema_version") == 2, f"Unsupported schema: {relative}")
        return value

    def image(self, record):
        path, digest = self.file(record["path"])
        require(digest == record["sha256"], f"Image hash mismatch: {record['path']}")
        with Image.open(path) as image:
            require(list(image.size) == record["size_px"], f"Image size mismatch: {record['path']}")
            require(image.mode == record["mode"], f"Image mode mismatch: {record['path']}")
            image.verify()
        with Image.open(path) as image:
            image.load()
        return digest

    def canonical(self, record):
        approval = record["approval"]
        require(approval in ("existing_project_canonical", "reviewed_reference_revision"),
                "Unsupported canonical approval")
        digest = self.image(record)
        if approval == "existing_project_canonical":
            require("revision_review" not in record, "Canonical/source mismatch: revised master claims unchanged")
            if "source" in record:
                require(digest == self.image(record["source"]), f"Canonical/source mismatch: {record['path']}")
            return
        source_digest = self.image(record["source"]) if "source" in record else None
        revision = record.get("revision_review", {})
        require(revision.get("status") == "accepted_for_reference", "Unreviewed canonical revision")
        for key in ("reviewer", "authority", "date", "reason", "evidence"):
            require(isinstance(revision.get(key), str) and bool(revision[key].strip()),
                    f"Missing canonical revision {key}")
        require(isinstance(revision.get("separate_user_visual_signoff"), bool),
                "Missing canonical revision signoff provenance")
        source_hash = revision.get("source_sha256")
        require(isinstance(source_hash, str) and len(source_hash) == 64
                and all(value in "0123456789abcdef" for value in source_hash),
                "Missing canonical revision source hash")
        if source_digest is not None:
            require(source_hash == source_digest, "Canonical revision source hash mismatch")
        require(revision.get("revised_sha256") == digest,
                "Canonical revision review hash mismatch")
        require(revision.get("scope") == "reference_only",
                "Canonical revision must declare reference-only scope")

    def sheet(self, record, item=False):
        self.image(record)
        review = record["review"]
        require(review["status"] == "accepted_for_reference", f"Unreviewed sheet: {record['path']}")
        for key in ("reviewer", "authority", "checks", "evidence"):
            require(bool(review.get(key)), f"Missing review {key}: {record['path']}")
        require(bool(review.get("date")), "Missing review date")
        require(isinstance(review.get("separate_user_visual_signoff"), bool), "Missing signoff provenance")
        width, height = record["size_px"]
        views = {}
        for view in record["views"]:
            name = view["id"]
            require(name not in views, f"Duplicate view: {name}")
            rect = view["rect_px" if item else "cell_rect_px"]
            require(len(rect) == 4 and all(type(v) is int for v in rect), f"Invalid rectangle: {name}")
            left, top, right, bottom = rect
            require(0 <= left < right <= width and 0 <= top < bottom <= height,
                    f"Out-of-bounds rectangle: {name}")
            views[name] = rect
        required = {f"{pose}_{view}" for pose in ("seated", "standing")
                    for view in ("front", "side_left", "back")}
        if item:
            required |= {f"item_{view}" for view in ("front", "side_left", "back")}
        require(required <= views.keys(), f"Missing views: {sorted(required - views.keys())}")
        return review


def head_dimensions(box):
    require(isinstance(box, list) and len(box) == 4
            and all(type(v) in (int, float) and math.isfinite(v) for v in box),
            "Invalid head box")
    width, height = box[2] - box[0], box[3] - box[1]
    require(width > 0 and height > 0, "Invalid head box")
    return width, height


def item_geometry(record, base):
    # The cap master has a different canvas/scale; use its fitted skull when recorded.
    box = record.get("fitted_head_core_box_px_estimate", base["head_core_box_px_estimate"])
    width, height = head_dimensions(box)
    points = record["attachment_points_px_estimate"]
    require(bool(points), "Missing fitted measurement: attachment_points_px_estimate")
    normalized = {}
    for name, point in points.items():
        require(isinstance(point, list) and len(point) == 2
                and all(type(v) in (int, float) and math.isfinite(v) for v in point),
                f"Invalid attachment point: {name}")
        normalized[name] = [(point[0] - box[0]) / width, (point[1] - box[1]) / height]
    bounds = record["item_bounds_px_estimate"]
    require(isinstance(bounds, list) and len(bounds) == 4
            and all(type(v) in (int, float) and math.isfinite(v) for v in bounds)
            and bounds[2] > bounds[0] and bounds[3] > bounds[1], "Invalid item bounds")
    return {"head_size_px": [width, height], "attachment_points_head_uv": normalized,
            "item_width_over_head_width": (bounds[2] - bounds[0]) / width}


def verify_input_review(path, checked):
    """Match bytes to a review assertion; this cannot authenticate its author."""
    raw = Path(path).read_bytes()
    review = json.loads(raw)
    require(isinstance(review, dict) and review.get("schema_version") == 1,
            "Unsupported input review schema")
    require(review.get("status") == "accepted_for_reference", "Input review is not accepted")
    for key in ("reviewer", "authority", "date", "evidence"):
        require(isinstance(review.get(key), str) and bool(review[key].strip()),
                f"Missing input review {key}")
    require(isinstance(review.get("separate_user_visual_signoff"), bool),
            "Missing input review signoff provenance")
    require(review.get("checked_sha256") == checked,
            "Reviewed input inventory mismatch; review changed inputs before generation")
    return {"path": str(path), "sha256": hashlib.sha256(raw).hexdigest(), "record": review}


def audit(repo, character, item=None, reviewed_inputs=None):
    gate = Gate(repo)
    catalog = gate.document("art/characters/reference_catalog.json")
    gate.file(catalog["contract_path"])
    character = character.removeprefix("friend:")
    require(character in catalog["characters"], f"Unknown character: {character}")
    base_path = catalog["characters"][character]
    base = gate.document(base_path)
    require(base["character_id"] == f"friend:{character}", "Catalog/character ID mismatch")
    require(base["variant"] == "normal", "Base reference must be normal")
    gate.canonical(base["canonical"])
    reviews = {base_path: gate.sheet(base["turnaround"])}
    require(bool(base["identity_constraints"]), "Missing base identity constraints")
    for key in ("root_anchor_px_estimate", "head_core_box_px_estimate", "ground_y_px", "pose"):
        require(base["canonical"].get(key) is not None, f"Missing canonical measurement: {key}")
    require("default_pose_prop" in base["canonical"], "Missing default pose prop")
    geometry = {"character": {"head_size_px": list(head_dimensions(base["canonical"]["head_core_box_px_estimate"]))}}
    manifests = [base_path]
    inputs = [base["canonical"]["path"], base["turnaround"]["path"]]
    for detail in base.get("construction_references", []):
        require(isinstance(detail.get("purpose"), str) and bool(detail["purpose"].strip()),
                "Missing construction reference purpose")
        gate.image(detail)
        inputs.append(detail["path"])
    variant = "normal"
    if item:
        require(item in catalog["accessories"], f"Unknown item: {item}")
        item_path = catalog["accessories"][item]
        accessory = gate.document(item_path)
        require(accessory["item_id"] == item, "Catalog/item ID mismatch")
        require(accessory["character_id"] == base["character_id"], "Item/character mismatch")
        link = accessory["base_character"]
        require(link["manifest_path"] == base_path, "Item links to a different base manifest")
        require(link["manifest_sha256"] == gate.checked[base_path], "Base manifest hash mismatch")
        gate.canonical(accessory["canonical_fitted"])
        require(accessory["canonical_fitted"]["equipped_item_ids"] == [item], "Wrong fitted item IDs")
        reviews[item_path] = gate.sheet(accessory["turnaround"], item=True)
        require(bool(accessory["identity_constraints"]), "Missing item identity constraints")
        for key in ("pivot", "fit", "occlusion"):
            require(bool(accessory["attachment_contract"].get(key)), f"Missing attachment rule: {key}")
        for key in ("attachment_points_px_estimate", "item_bounds_px_estimate"):
            require(bool(accessory["canonical_fitted"].get(key)), f"Missing fitted measurement: {key}")
        geometry["item"] = item_geometry(accessory["canonical_fitted"], base["canonical"])
        variant = accessory["asset_variant"]
        require(isinstance(variant, str) and bool(variant), "Missing asset variant")
        manifests.append(item_path)
        inputs += [accessory["canonical_fitted"]["path"], accessory["turnaround"]["path"]]
    input_review = verify_input_review(reviewed_inputs, gate.checked) if reviewed_inputs else None
    return {
        "status": "reference_gate_passed" if input_review else "reference_checks_passed",
        "input_review_status": "matched" if input_review else "pending",
        "generation_ready": input_review is not None,
        "character_id": base["character_id"],
        "item_id": item,
        "asset_variant": variant,
        "geometry": geometry,
        "manifests_to_read": manifests,
        "images_to_inspect": inputs,
        "checked_sha256": gate.checked,
        "reference_reviews": reviews,
        "input_review": input_review,
        "limits": (
            "Manifest review fields are declarations, not proof of review of current bytes. "
            "A matched input review binds those bytes to a recorded assertion, not authenticated "
            "review authority. Verify its evidence and authority; motion review remains separate."
        ),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--character", required=True)
    parser.add_argument("--item")
    parser.add_argument("--reviewed-inputs", type=Path,
                        help="Prior input-review JSON with the exact checked_sha256 inventory")
    args = parser.parse_args()
    try:
        result = audit(args.repo, args.character, args.item, args.reviewed_inputs)
    except (OSError, ValueError, KeyError, TypeError, AttributeError, Image.DecompressionBombError) as error:
        print(json.dumps({"status": "blocked", "reason": str(error)}))
        return 1
    print(json.dumps(result, indent=2))
    return 0 if result["generation_ready"] else 2


if __name__ == "__main__":
    sys.exit(main())
