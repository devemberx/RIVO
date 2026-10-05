#!/usr/bin/env python3
"""Check reference files and optionally match a prior, explicit input review."""

import argparse
import hashlib
import json
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
        require(value.get("schema_version") == 1, f"Unsupported schema: {relative}")
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
        source_digest = self.image(record["source"])
        if approval == "existing_project_canonical":
            require(digest == source_digest, f"Canonical/source mismatch: {record['path']}")
            return
        revision = record.get("revision_review", {})
        require(revision.get("status") == "accepted_for_reference", "Unreviewed canonical revision")
        for key in ("reviewer", "authority", "date", "reason", "evidence"):
            require(isinstance(revision.get(key), str) and bool(revision[key].strip()),
                    f"Missing canonical revision {key}")
        require(isinstance(revision.get("separate_user_visual_signoff"), bool),
                "Missing canonical revision signoff provenance")
        require(revision.get("source_sha256") == source_digest,
                "Canonical revision source hash mismatch")
        require(revision.get("revised_sha256") == digest,
                "Canonical revision review hash mismatch")
        require(revision.get("scope") == "reference_only",
                "Canonical revision must declare reference-only scope")

    def sheet(self, record, item=False):
        self.image(record)
        review = record["review"]
        require(review["status"] == "accepted_for_reference", f"Unreviewed sheet: {record['path']}")
        for key in ("reviewer", "authority", "checks"):
            require(bool(review.get(key)), f"Missing review {key}: {record['path']}")
        require(bool(review.get("date") or review.get("review_date")), "Missing review date")
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
        if item:
            for view in record["views"]:
                if view["id"] in required:
                    prefix, direction = view["id"].split("_", 1)
                    subject = "item_only" if prefix == "item" else "equipped_character"
                    require(view["subject"] == subject and view["view"] == direction,
                            f"View metadata mismatch: {view['id']}")
                    require(view["pose"] == (None if prefix == "item" else prefix),
                            f"View pose mismatch: {view['id']}")
        return review


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
    character = character.removeprefix("friend:")
    require(character in catalog["characters"], f"Unknown character: {character}")
    base_path = catalog["characters"][character]
    base = gate.document(base_path)
    require(base["character_id"] == f"friend:{character}", "Catalog/character ID mismatch")
    require(base["variant"] == "normal", "Base reference must be normal")
    gate.canonical(base["canonical"])
    reviews = {base_path: gate.sheet(base["turnaround"])}
    require(bool(base["identity_constraints"]), "Missing base identity constraints")
    for key in ("scale", "anchors", "rendering", "validation", "pose_selection", "default_prop_policy"):
        require(bool(base["animation_contract"].get(key)), f"Missing animation contract: {key}")
    for key in ("root_anchor_px_estimate", "head_core_box_px_estimate", "ground_y_px", "proportions"):
        require(base["canonical"].get(key) is not None, f"Missing canonical measurement: {key}")
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
        for key in ("canonical", "turnaround"):
            gate.image(link[key])
            require(link[key]["sha256"] == base[key]["sha256"], f"Item/base {key} mismatch")
        gate.canonical(accessory["canonical_fitted"])
        require(accessory["canonical_fitted"]["equipped_item_ids"] == [item], "Wrong fitted item IDs")
        reviews[item_path] = gate.sheet(accessory["turnaround"], item=True)
        require(bool(accessory["identity_constraints"]), "Missing item identity constraints")
        for key in ("pivot", "fit", "occlusion", "transform", "character_invariants"):
            require(bool(accessory["attachment_contract"].get(key)), f"Missing attachment rule: {key}")
        for key in ("requires", "on_missing_or_hash_mismatch", "pose_selection", "runtime_boundary"):
            require(bool(accessory["generation_gate"].get(key)), f"Missing item gate: {key}")
        for key in ("attachment_points_px_estimate", "attachment_points_head_uv_estimate",
                    "item_width_over_base_head_width_estimate"):
            require(bool(accessory["canonical_fitted"].get(key)), f"Missing fitted measurement: {key}")
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
