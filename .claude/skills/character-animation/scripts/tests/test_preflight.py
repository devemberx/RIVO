"""Exercise the live catalog and isolated invalid copies without editing source art."""

import importlib.util
import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "preflight.py"
SPEC = importlib.util.spec_from_file_location("preflight", SCRIPT)
PREFLIGHT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PREFLIGHT)
REPO = Path(__file__).resolve().parents[5]
ITEM_MANIFEST = "art/items/luna_cap/references/reference.json"


class ReferenceGateTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.baseline = PREFLIGHT.audit(REPO, "luna", "accessory:luna_cap")

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name)
        for relative in self.baseline["checked_sha256"]:
            target = self.repo / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(REPO / relative, target)

    def review_record(self, result):
        path = self.repo / "input-review.json"
        path.write_text(json.dumps({
            "schema_version": 1,
            "status": "accepted_for_reference",
            "reviewer": "Fixture reviewer",
            "authority": "Isolated test fixture only",
            "date": "2026-10-05",
            "evidence": "Fixture inspection record",
            "separate_user_visual_signoff": False,
            "checked_sha256": result["checked_sha256"],
        }))
        return path

    def update(self, relative, change):
        path = self.repo / relative
        data = json.loads(path.read_text())
        change(data)
        path.write_text(json.dumps(data))

    def check(self):
        return PREFLIGHT.audit(self.repo, "luna", "accessory:luna_cap")

    def add_source_record(self):
        base = json.loads((self.repo / "art/characters/luna/references/reference.json").read_text())
        source = {key: base["canonical"][key] for key in ("path", "sha256", "size_px", "mode")}
        original = self.repo / source["path"]
        source["path"] = "art/items/luna_cap/references/original_source_fixture.png"
        shutil.copyfile(original, self.repo / source["path"])
        self.update(ITEM_MANIFEST, lambda d: d["canonical_fitted"].update(source=source))
        self.update(ITEM_MANIFEST, lambda d: d["canonical_fitted"]["revision_review"].update(
            source_sha256=source["sha256"]))
        return source

    def test_current_catalog_targets(self):
        for character in ("mobi", "luna", "las"):
            with self.subTest(character=character):
                result = PREFLIGHT.audit(REPO, character)
                self.assertEqual(result["status"], "reference_checks_passed")
                self.assertFalse(result["generation_ready"])
        for character, item in (("mobi", "mobi_headphones"), ("mobi", "mobi_goggles"),
                                ("luna", "luna_cap"), ("luna", "luna_sunglasses")):
            with self.subTest(item=item):
                self.assertEqual(PREFLIGHT.audit(REPO, character, f"accessory:{item}")["status"],
                                 "reference_checks_passed")

    def test_matching_review_binds_current_inputs(self):
        review = self.review_record(self.check())
        result = PREFLIGHT.audit(self.repo, "luna", "accessory:luna_cap", review)
        self.assertTrue(result["generation_ready"])
        self.assertEqual(result["input_review_status"], "matched")
        self.assertEqual(result["status"], "reference_gate_passed")

    def test_unchanged_master_does_not_require_duplicate_source(self):
        self.update("art/characters/luna/references/reference.json",
                    lambda d: d["canonical"].pop("source", None))
        self.assertEqual(PREFLIGHT.audit(self.repo, "luna")["status"], "reference_checks_passed")

    def test_item_resolves_base_images_without_copied_records(self):
        self.update(ITEM_MANIFEST, lambda d: d.update(base_character={
            key: value for key, value in d["base_character"].items()
            if key in ("manifest_path", "manifest_sha256")
        }))
        self.assertEqual(self.check()["asset_variant"], "hat")

    def test_shared_contract_is_bound_to_input_review(self):
        contract = ".agents/skills/character-animation/references/production.md"
        result = self.check()
        self.assertIn(contract, result["checked_sha256"])
        review = self.review_record(result)
        path = self.repo / contract
        path.write_text(path.read_text() + "\nChanged shared rule.\n")
        with self.assertRaisesRegex(ValueError, "Reviewed input inventory mismatch"):
            PREFLIGHT.audit(self.repo, "luna", "accessory:luna_cap", review)

    def test_cap_geometry_uses_fitted_head_instead_of_base_canvas(self):
        geometry = self.check()["geometry"]["item"]
        self.assertAlmostEqual(geometry["attachment_points_head_uv"]["badge"][0], 0.6383, places=4)
        self.assertAlmostEqual(geometry["item_width_over_head_width"], 0.9082, places=4)

    def test_invalid_head_box_cannot_produce_scale(self):
        for box in ([175, 345, 175, 825], [175, 345, 925, 345], [175, 345, float("nan"), 825]):
            with self.subTest(box=box):
                self.update(ITEM_MANIFEST, lambda d: d["canonical_fitted"].update(
                    fitted_head_core_box_px_estimate=box))
                with self.assertRaisesRegex(ValueError, "Invalid head box"):
                    self.check()

    def test_revised_master_can_archive_original_source(self):
        source = self.add_source_record()
        self.update(ITEM_MANIFEST, lambda d: d["canonical_fitted"].pop("source"))
        (self.repo / source["path"]).unlink()
        master = json.loads((self.repo / ITEM_MANIFEST).read_text())["canonical_fitted"]
        result = self.check()
        self.assertEqual(result["status"], "reference_checks_passed")
        self.assertIn(master["path"], result["checked_sha256"])
        self.assertNotIn(source["path"], result["checked_sha256"])
        self.assertFalse(result["generation_ready"])

    def test_archived_source_requires_provenance_hash(self):
        self.update(ITEM_MANIFEST, lambda d: d["canonical_fitted"].pop("source", None))
        for value in (None, "", "not-a-hash", "g" * 64):
            with self.subTest(value=value):
                self.update(ITEM_MANIFEST, lambda d: d["canonical_fitted"]["revision_review"].update(
                    source_sha256=value))
                with self.assertRaisesRegex(ValueError, "Missing canonical revision source hash"):
                    self.check()

    def test_archived_source_still_requires_matching_master_hash(self):
        self.update(ITEM_MANIFEST, lambda d: d["canonical_fitted"].pop("source", None))
        self.update(ITEM_MANIFEST, lambda d: d["canonical_fitted"]["revision_review"].update(
            revised_sha256="0" * 64))
        with self.assertRaisesRegex(ValueError, "review hash mismatch"):
            self.check()

    def test_archiving_source_invalidates_previous_input_review(self):
        self.add_source_record()
        review = self.review_record(self.check())
        self.update(ITEM_MANIFEST, lambda d: d["canonical_fitted"].pop("source"))
        with self.assertRaisesRegex(ValueError, "Reviewed input inventory mismatch"):
            PREFLIGHT.audit(self.repo, "luna", "accessory:luna_cap", review)

    def test_unreviewed_base_contract_change_cannot_authorize_generation(self):
        baseline = PREFLIGHT.audit(self.repo, "luna")
        review = self.review_record(baseline)
        self.update("art/characters/luna/references/reference.json",
                    lambda d: d.update(identity_constraints=["Changed without review"]))
        result = PREFLIGHT.audit(self.repo, "luna")
        self.assertFalse(result["generation_ready"])
        self.assertEqual(result["input_review_status"], "pending")
        with self.assertRaisesRegex(ValueError, "Reviewed input inventory mismatch"):
            PREFLIGHT.audit(self.repo, "luna", reviewed_inputs=review)

    def test_item_contract_change_invalidates_review(self):
        review = self.review_record(self.check())
        self.update(ITEM_MANIFEST, lambda d: d["attachment_contract"].update(pivot="new pivot"))
        with self.assertRaisesRegex(ValueError, "Reviewed input inventory mismatch"):
            PREFLIGHT.audit(self.repo, "luna", "accessory:luna_cap", review)

    def test_incomplete_or_pending_review_cannot_authorize_generation(self):
        for field, value in (("status", "pending"), ("evidence", ""),
                             ("authority", " "), ("checked_sha256", {})):
            with self.subTest(field=field):
                review = self.review_record(self.check())
                record = json.loads(review.read_text())
                record[field] = value
                review.write_text(json.dumps(record))
                with self.assertRaises(ValueError):
                    PREFLIGHT.audit(self.repo, "luna", "accessory:luna_cap", review)

    def test_cli_distinguishes_pending_pass_and_blocked(self):
        args = [sys.executable, "-B", str(SCRIPT), "--repo", str(self.repo), "--character", "luna"]
        pending = subprocess.run(args, capture_output=True, text=True)
        self.assertEqual(pending.returncode, 2)
        self.assertFalse(json.loads(pending.stdout)["generation_ready"])
        review = self.review_record(json.loads(pending.stdout))
        passed = subprocess.run(args + ["--reviewed-inputs", str(review)], capture_output=True, text=True)
        self.assertEqual(passed.returncode, 0)
        self.assertTrue(json.loads(passed.stdout)["generation_ready"])
        (self.repo / self.baseline["images_to_inspect"][0]).unlink()
        blocked = subprocess.run(args, capture_output=True, text=True)
        self.assertEqual(blocked.returncode, 1)
        self.assertEqual(json.loads(blocked.stdout)["status"], "blocked")

    def test_delegated_review_and_variant_mapping(self):
        result = self.check()
        self.assertEqual(result["asset_variant"], "hat")
        self.assertFalse(result["reference_reviews"][ITEM_MANIFEST]["separate_user_visual_signoff"])

    def test_revised_master_preserves_independent_source_provenance(self):
        self.add_source_record()
        data = json.loads((self.repo / ITEM_MANIFEST).read_text())
        master = data["canonical_fitted"]
        self.assertEqual(master["approval"], "reviewed_reference_revision")
        self.assertNotEqual(master["sha256"], master["source"]["sha256"])
        result = self.check()
        self.assertIn(master["path"], result["checked_sha256"])
        self.assertIn(master["source"]["path"], result["checked_sha256"])
        self.assertFalse(result["generation_ready"])

    def test_revision_cannot_claim_unchanged_canonical(self):
        self.update(ITEM_MANIFEST, lambda d: d["canonical_fitted"].update(
            approval="existing_project_canonical"))
        with self.assertRaisesRegex(ValueError, "Canonical/source mismatch"):
            self.check()

    def test_revision_requires_review_provenance(self):
        for key in ("reviewer", "authority", "date", "reason", "evidence"):
            with self.subTest(key=key):
                original = (self.repo / ITEM_MANIFEST).read_text()
                self.update(ITEM_MANIFEST, lambda d: d["canonical_fitted"]["revision_review"].update(
                    {key: " "}))
                with self.assertRaisesRegex(ValueError, "Missing canonical revision"):
                    self.check()
                (self.repo / ITEM_MANIFEST).write_text(original)

    def test_revision_requires_matching_hashes_and_reference_scope(self):
        self.add_source_record()
        for key, value, message in (
            ("source_sha256", "0" * 64, "source hash mismatch"),
            ("revised_sha256", "0" * 64, "review hash mismatch"),
            ("scope", "runtime", "reference-only scope"),
            ("status", "pending", "Unreviewed canonical revision"),
            ("separate_user_visual_signoff", None, "signoff provenance"),
        ):
            with self.subTest(key=key):
                original = (self.repo / ITEM_MANIFEST).read_text()
                self.update(ITEM_MANIFEST, lambda d: d["canonical_fitted"]["revision_review"].update(
                    {key: value}))
                with self.assertRaisesRegex(ValueError, message):
                    self.check()
                (self.repo / ITEM_MANIFEST).write_text(original)

    def test_revised_source_still_requires_integrity(self):
        self.add_source_record()
        master = json.loads((self.repo / ITEM_MANIFEST).read_text())["canonical_fitted"]
        source = self.repo / master["source"]["path"]
        source.write_bytes(source.read_bytes() + b"changed")
        with self.assertRaisesRegex(ValueError, "Image hash mismatch"):
            self.check()

    def test_retained_source_missing_file_blocks(self):
        source = self.add_source_record()
        (self.repo / source["path"]).unlink()
        with self.assertRaisesRegex(ValueError, "Missing file"):
            self.check()

    def test_construction_reference_is_bound_and_hash_checked(self):
        baseline = PREFLIGHT.audit(REPO, "las")
        for relative in baseline["checked_sha256"]:
            target = self.repo / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(REPO / relative, target)
        base = json.loads((self.repo / "art/characters/las/references/reference.json").read_text())
        detail = "art/characters/las/references/construction_fixture.png"
        shutil.copyfile(self.repo / base["canonical"]["path"], self.repo / detail)
        record = {key: base["canonical"][key] for key in ("sha256", "size_px", "mode")}
        record.update(path=detail, purpose="Isolated construction-reference integrity fixture")
        self.update("art/characters/las/references/reference.json",
                    lambda d: d.update(construction_references=[record]))
        self.assertIn(detail, PREFLIGHT.audit(self.repo, "las")["images_to_inspect"])
        review = self.review_record(PREFLIGHT.audit(self.repo, "las"))
        self.assertTrue(PREFLIGHT.audit(self.repo, "las", reviewed_inputs=review)["generation_ready"])
        target = self.repo / detail
        target.write_bytes(target.read_bytes() + b"changed")
        with self.assertRaisesRegex(ValueError, "Image hash mismatch"):
            PREFLIGHT.audit(self.repo, "las", reviewed_inputs=review)

    def test_missing_image_blocks(self):
        (self.repo / self.baseline["images_to_inspect"][-1]).unlink()
        with self.assertRaisesRegex(ValueError, "Missing file"):
            self.check()

    def test_changed_image_blocks(self):
        path = self.repo / self.baseline["images_to_inspect"][-1]
        path.write_bytes(path.read_bytes() + b"changed")
        with self.assertRaisesRegex(ValueError, "hash mismatch"):
            self.check()

    def test_changed_base_manifest_blocks(self):
        path = self.repo / "art/characters/luna/references/reference.json"
        path.write_text(path.read_text() + "\n")
        with self.assertRaisesRegex(ValueError, "Base manifest hash mismatch"):
            self.check()

    def test_unreviewed_sheet_blocks(self):
        self.update(ITEM_MANIFEST, lambda d: d["turnaround"]["review"].update(status="pending"))
        with self.assertRaisesRegex(ValueError, "Unreviewed"):
            self.check()

    def test_missing_authority_blocks(self):
        self.update(ITEM_MANIFEST, lambda d: d["turnaround"]["review"].pop("authority"))
        with self.assertRaisesRegex(ValueError, "Missing review authority"):
            self.check()

    def test_missing_back_view_blocks(self):
        self.update(ITEM_MANIFEST, lambda d: d["turnaround"]["views"].pop())
        with self.assertRaisesRegex(ValueError, "Missing views"):
            self.check()

    def test_outside_sheet_rectangle_blocks(self):
        self.update(ITEM_MANIFEST, lambda d: d["turnaround"]["views"][0].update(rect_px=[0, 0, 9000, 300]))
        with self.assertRaisesRegex(ValueError, "Out-of-bounds"):
            self.check()

    def test_wrong_character_blocks(self):
        self.update(ITEM_MANIFEST, lambda d: d.update(character_id="friend:mobi"))
        with self.assertRaisesRegex(ValueError, "Item/character mismatch"):
            self.check()

    def test_missing_attachment_blocks(self):
        self.update(ITEM_MANIFEST, lambda d: d["attachment_contract"].pop("pivot"))
        with self.assertRaisesRegex(ValueError, "Missing attachment"):
            self.check()

    def test_unknown_schema_blocks(self):
        self.update(ITEM_MANIFEST, lambda d: d.update(schema_version=99))
        with self.assertRaisesRegex(ValueError, "Unsupported schema"):
            self.check()

    def test_path_escape_blocks(self):
        self.update(ITEM_MANIFEST, lambda d: d["turnaround"].update(path="../outside.png"))
        with self.assertRaisesRegex(ValueError, "outside repository"):
            self.check()


if __name__ == "__main__":
    unittest.main()
