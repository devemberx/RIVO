"""Portable comparison provenance, safe packaging and immutable evidence."""
import base64
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from PIL import Image

SCRIPT = Path(__file__).resolve().parents[1] / 'prepare_comparison.py'
SPEC = importlib.util.spec_from_file_location('prepare_comparison', SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ComparisonTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.path = self.root / 'comparison.json'
        self.out = self.root / 'review'
        Image.new('RGBA', (16, 16), (80, 90, 100, 127)).save(self.root / 'body.png')
        (self.root / 'renderer.js').write_text('function createAnimationReview(images){return {drawBefore(){},drawAfter(){}};}')
        self.spec = dict(title='</script>__RENDERER__', canvas_px=[16,16], display_sizes_css_px=[16,32], duration_ms=1000,
                         loop=True, variants=[dict(id='normal',label='Normal')], samples=[dict(ms=0,label='Start')],
                         renderer_js='renderer.js', assets=dict(body='body.png'))

    def prepare(self):
        self.path.write_text(json.dumps(self.spec))
        return MODULE.prepare(self.path, self.out)

    def test_portable_exact_images_and_unreviewed_provenance(self):
        result = self.prepare()
        self.assertEqual(result['status'], 'prepared_unreviewed')
        html = (self.out / 'preview.html').read_text()
        payload = json.loads(html.split('const spec=',1)[1].split(';\n',1)[0])
        self.assertEqual(payload['title'], self.spec['title'])
        self.assertEqual(base64.b64decode(payload['images']['body'].split(',')[1]), (self.root/'body.png').read_bytes())
        self.assertEqual(result['assets']['body']['size'], [16,16])
        self.assertEqual(len(result['renderer_sha256']),64)

    def test_cannot_overwrite_previous_evidence(self):
        self.prepare()
        original = (self.out/'preview.html').read_bytes()
        with self.assertRaises(FileExistsError): self.prepare()
        self.assertEqual((self.out/'preview.html').read_bytes(),original)

    def test_missing_inputs_and_path_escape_leave_no_output(self):
        for path in ('../body.png','/tmp/body.png','missing.png'):
            self.spec['assets']['body']=path
            with self.assertRaises((ValueError,FileNotFoundError)): self.prepare()
            self.assertFalse(self.out.exists())

    def test_invalid_timing_or_one_shot_is_rejected(self):
        for duration in (0,-1,True,float('nan')):
            self.spec['duration_ms']=duration
            with self.assertRaises(ValueError): self.prepare()
        self.spec['duration_ms']=1000
        self.spec['loop']=False
        with self.assertRaises(ValueError): self.prepare()

    def test_invalid_samples_and_unsafe_renderer_are_rejected(self):
        self.spec['samples'][0]['ms']=1001
        with self.assertRaises(ValueError): self.prepare()
        self.spec['samples'][0]['ms']=0
        (self.root/'renderer.js').write_text('// </SCRIPT>')
        with self.assertRaises(ValueError): self.prepare()


if __name__ == '__main__': unittest.main()
