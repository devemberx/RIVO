"""Curation must never silently detach a retained procedure from required safety context."""
import unittest

import pymupdf

from prepare_manual import SOURCE_SHA256, curate, printed_label, read_text


class CurationTest(unittest.TestCase):
    def test_footers_on_both_sides_with_or_without_word_spacing(self):
        self.assertEqual(printed_label('330 시동 및 주행'), '330')
        self.assertEqual(printed_label('시동 및 주행331'), '331')
        self.assertEqual(printed_label('전기차 시작하기 1'), '1')
        self.assertIsNone(printed_label(''))
        with self.assertRaises(ValueError):
            printed_label('2 시동 및 주행 331')

    def test_icon_letter_is_removed_without_changing_normal_gear_letter(self):
        pdf = pymupdf.open()
        page = pdf.new_page()
        page.insert_text((20, 30), 'N N')
        first = page.get_text('words')[0]
        text = read_text(page, page.rect, [(pymupdf.Rect(first[:4]), 'N')])
        self.assertEqual(text.split(), ['[그림]', 'N'])
        pdf.close()

    def test_preserves_ids_text_and_required_links(self):
        chunks = [dict(id='ne1-0001', headingPath=['Notice'], text='metadata',
                       pdfPageStart=1, pdfPageEnd=1, contextIds=[], linkedWarningIds=[]),
                  dict(id='ne1-0002', headingPath=['Operation'], text='Full procedure and warning',
                       pdfPageStart=9, pdfPageEnd=10, contextIds=[], linkedWarningIds=[])]
        config = dict(sourceSha256=SOURCE_SHA256, excludeSubtrees=[
            dict(headingPath=['Notice'], expectedCount=1, reason='metadata')])
        included, excluded = curate(chunks, config)
        self.assertEqual(included, [chunks[1]])
        self.assertEqual(excluded[0]['id'], 'ne1-0001')
        chunks[1]['linkedWarningIds'] = ['ne1-0001']
        with self.assertRaisesRegex(ValueError, 'required context'):
            curate(chunks, config)
        config['excludeSubtrees'][0]['expectedCount'] = 2
        with self.assertRaisesRegex(ValueError, 'no longer matches'):
            curate(chunks, config)


if __name__ == '__main__':
    unittest.main()
