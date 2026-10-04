import importlib.util
import tempfile
import unittest
from pathlib import Path

from PIL import Image

SCRIPT = Path(__file__).resolve().parents[1] / "export_webp.py"
SPEC = importlib.util.spec_from_file_location("export_webp", SCRIPT)
export = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(export)


class ExportWebpTest(unittest.TestCase):
    def test_preserves_canvas_pixels_alpha_and_source(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for mode, color in [("RGB", (23, 42, 81)), ("RGBA", (23, 42, 81, 0))]:
                source, target = root / (mode + ".png"), root / (mode + ".webp")
                Image.new(mode, (80, 45), color).save(source)
                before = source.read_bytes()
                result = export.export_webp(source, target)
                with Image.open(source) as original, Image.open(target) as converted:
                    self.assertEqual("WEBP", converted.format)
                    self.assertEqual(original.size, converted.size)
                    self.assertEqual(original.convert("RGBA").tobytes(), converted.convert("RGBA").tobytes())
                self.assertEqual(before, source.read_bytes())
                self.assertEqual(len(target.read_bytes()), result["webp_bytes"])
                with self.assertRaises(FileExistsError):
                    export.export_webp(source, target)

    def test_rejects_animation_and_non_webp_destination(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "animated.gif"
            Image.new("RGB", (10, 10), "red").save(
                source, save_all=True, append_images=[Image.new("RGB", (10, 10), "blue")], duration=100,
            )
            with self.assertRaisesRegex(ValueError, "single still image"):
                export.export_webp(source, root / "output.webp")
            with self.assertRaisesRegex(ValueError, ".webp"):
                export.export_webp(source, root / "output.png")


if __name__ == "__main__":
    unittest.main()
