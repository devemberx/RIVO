#!/usr/bin/env python3
"""Build the fixed NE1 corpus from a locally supplied official PDF; never download at app/build time."""
import argparse
import hashlib
import json
import math
import re
from pathlib import Path

import pymupdf

CORPUS = 'ioniq5_2027_ko'
SOURCE_SHA256 = '4525f59eb85737ca87c3e0e0b43de9f36cb48ad2d5456f708f5861a0d6de9b1c'
SOURCE_URL = 'https://ownersmanual.hyundai.com/full_pdf/NE1/2027/ko_KR'
EXCLUDED_PAGES = {4: 'blank', 5: 'table of contents', 6: 'table of contents', 7: 'table of contents', 8: 'table of contents'}
EXCLUDED_PAGES.update({62: 'blank except footer', 72: 'blank except footer',
                       362: 'blank except footer', 516: 'blank except footer',
                       531: 'product label image only; not transcribed'})
EXTRACTOR = 'ne1-pdf-outline-v2'


def clean(text):
    # These broken ToUnicode banner glyphs were checked against rendered PDF pages 2, 11 and 21.
    text = re.sub(r'҃\s*Ҋ', '\n[경고]\n', text)
    text = text.replace('҃', '\n[경고]\n').replace('Ҋ', '')
    text = text.replace('઱੄', '\n[주의]\n').replace('ঌইفӝ', '\n[알아두기]\n')
    text = re.sub(r'[12]C_[A-Za-z0-9_]+', ' ', text)
    text = re.sub(r'[ \t]+', ' ', text)
    return re.sub(r'\n{3,}', '\n\n', text).strip()


def printed_label(footer):
    numbers = re.findall(r'\d+', footer)
    if len(numbers) > 1:
        raise ValueError('Ambiguous printed page label')
    return numbers[0] if numbers else None


def icon_glyphs(page):
    # This PDF's OWNS font encodes pictures as ordinary letters (Auto Hold is "N").
    # Their Unicode values are not readable labels; never turn them into vehicle facts.
    return [(pymupdf.Rect(char['bbox']), char['c'])
            for block in page.get_text('rawdict')['blocks'] if 'lines' in block
            for line in block['lines'] for span in line['spans']
            if span['font'].startswith('HYUNDAI_OWNS')
            for char in span['chars'] if char['c'].strip()]


def read_text(page, clip, icons):
    words = page.get_text('words', clip=clip, sort=False)
    safe_words = []
    for word in words:
        box, text = pymupdf.Rect(word[:4]), word[4]
        for glyph_box, glyph in icons:
            if abs(box & glyph_box) >= 0.5 * abs(glyph_box) and glyph in text:
                text = text.replace(glyph, '\ufff0', 1)
        safe_words.append((*word[:4], text.replace('\ufff0', '[그림]'), *word[5:]))

    class WordSource:
        # The pinned library's layout helper uses only parent and extractWORDS.
        # Supplying filtered words keeps its verified list/table reading order intact.
        parent = page

        def extractWORDS(self, delimiters=None):
            return list(safe_words)

    return pymupdf.utils.get_sorted_text(page, textpage=WordSource())


def expand_table(page, table, icons):
    """Expand merged cells using their actual PDF geometry, never guess missing values by forward-fill."""
    boxes = [pymupdf.Rect(c) for c in table.cells if c]
    xs = sorted({round(v, 2) for b in boxes for v in (b.x0, b.x1)})
    ys = sorted({round(v, 2) for b in boxes for v in (b.y0, b.y1)})
    rows = []
    for y0, y1 in zip(ys, ys[1:]):
        row = []
        for x0, x1 in zip(xs, xs[1:]):
            point = pymupdf.Point((x0 + x1) / 2, (y0 + y1) / 2)
            cell = next((b for b in boxes if b.contains(point)), None)
            # Internal warning rules can prevent rectangle detection in the rightmost column.
            # Read the source grid region in that case; never forward-fill an absent value.
            if cell is None:
                cell = pymupdf.Rect(x0, y0, x1, y1)
            value = clean(read_text(page, cell, icons)).replace('\n', ' ')
            row.append(value)
        rows.append(row)
    return '[표: 병합 셀의 표기를 해당 행과 열에 반복함]\n' + '\n'.join(' | '.join(row) for row in rows)


def extract_region(page, top, bottom, tables, icons):
    pieces = []
    cursor = top
    for box, text in tables:
        if box.y1 <= top or box.y0 >= bottom:
            continue
        if box.y0 > cursor:
            pieces.append(clean(read_text(page, pymupdf.Rect(0, cursor, page.rect.width, box.y0), icons)))
        pieces.append(text)
        cursor = max(cursor, box.y1)
    if cursor < bottom:
        pieces.append(clean(read_text(page, pymupdf.Rect(0, cursor, page.rect.width, bottom), icons)))
    return '\n\n'.join(p for p in pieces if p)


def write_json(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    return hashlib.sha256(path.read_bytes()).hexdigest()


def curate(chunks, config):
    """Apply reviewed section exclusions without renumbering or trimming retained safety text."""
    if config['sourceSha256'] != SOURCE_SHA256:
        raise ValueError('Curation belongs to a different source revision')
    excluded = {}
    for rule in config['excludeSubtrees']:
        path = rule['headingPath']
        matches = [c for c in chunks if c['headingPath'][:len(path)] == path]
        if len(matches) != rule['expectedCount']:
            raise ValueError(f'Curation no longer matches source: {path}')
        for chunk in matches:
            if chunk['id'] in excluded:
                raise ValueError('Overlapping curation rules')
            excluded[chunk['id']] = dict(id=chunk['id'], headingPath=chunk['headingPath'],
                                        pdfPageStart=chunk['pdfPageStart'], pdfPageEnd=chunk['pdfPageEnd'],
                                        reason=rule['reason'])
    included = [c for c in chunks if c['id'] not in excluded]
    for chunk in included:
        # Never silently sever an operating section's warning or applicability context.
        required = chunk['contextIds'] + chunk['linkedWarningIds']
        if any(ref in excluded for ref in required):
            raise ValueError(f'Curation would remove required context for {chunk["id"]}')
    return included, list(excluded.values())


def main():
    args = argparse.ArgumentParser(description=__doc__)
    args.add_argument('pdf', type=Path)
    args.add_argument('--output', type=Path, default=Path('app/src/debug/assets/manuals') / CORPUS)
    args.add_argument('--download-date', required=True, help='Actual source acquisition date, YYYY-MM-DD')
    opts = args.parse_args()
    if pymupdf.VersionBind != '1.28.2':
        raise ValueError('Use the pinned scripts/manual/requirements.txt environment')
    if hashlib.sha256(opts.pdf.read_bytes()).hexdigest() != SOURCE_SHA256:
        raise ValueError('Unexpected PDF revision; inspect and explicitly update the pinned corpus first')
    pdf = pymupdf.open(opts.pdf)
    if len(pdf) != 565 or 'NE1,2027' not in pdf.metadata['keywords']:
        raise ValueError('Wrong manual')
    outline = pdf.get_toc(simple=False)
    entries = []
    ancestry = []
    for index, (level, title, page, destination) in enumerate(outline):
        while ancestry and entries[ancestry[-1]]['level'] >= level:
            ancestry.pop()
        y = float(destination['to'].y)
        if not math.isfinite(y):
            raise ValueError('Invalid outline location')
        entries.append(dict(id=f'ne1-{index + 1:04d}', level=level, title=title,
                            start=(page, y), ancestors=list(ancestry)))
        ancestry.append(index)
    tables_by_page, labels, table_pages, image_pages = {}, {}, [], []
    icons_by_page = {}
    for i, page in enumerate(pdf):
        if i % 100 == 0:
            print(f'Extracting page {i + 1}/{len(pdf)}', flush=True)
        footer = page.get_text(clip=pymupdf.Rect(0, 552, page.rect.width, page.rect.height), sort=True).strip()
        labels[i + 1] = printed_label(footer) if i + 1 >= 9 else None
        if i + 1 >= 9 and labels[i + 1] != str(i + 1 - 8):
            raise ValueError(f'Unexpected printed page label on PDF page {i + 1}')
        icons_by_page[i + 1] = icon_glyphs(page)
        found = page.find_tables().tables
        tables = [(pymupdf.Rect(t.bbox), expand_table(page, t, icons_by_page[i + 1])) for t in found
                  if t.row_count >= 2 and t.col_count >= 2 and t.bbox[3] <= 552]
        tables_by_page[i + 1] = sorted(tables, key=lambda item: item[0].y0)
        if tables:
            table_pages.append(i + 1)
        if page.get_images():
            image_pages.append(i + 1)
    chunks = []
    covered_pages = set()
    for index, entry in enumerate(entries):
        start_page, start_y = entry['start']
        end_page, end_y = entries[index + 1]['start'] if index + 1 < len(entries) else (len(pdf), 552)
        parts, pages = [], []
        for number in range(start_page, end_page + 1):
            if number in EXCLUDED_PAGES:
                continue
            top = start_y if number == start_page else 0
            bottom = min(552, end_y if number == end_page else 552)
            if top >= bottom:
                continue
            text = extract_region(pdf[number - 1], top, bottom, tables_by_page[number], icons_by_page[number])
            if text:
                parts.append(text)
                pages.append(number)
                covered_pages.add(number)
        text = '\n\n'.join(parts)
        if not pages or not text:
            raise ValueError(f'Empty outline section: {entry["title"]}')
        path = [entries[i]['title'] for i in entry['ancestors']] + [entry['title']]
        chunks.append(dict(id=entry['id'], parentSectionId=entries[entry['ancestors'][-1]]['id'] if entry['ancestors'] else None,
                           headingPath=path, text=text, pdfPageStart=pages[0], pdfPageEnd=pages[-1],
                           printedPages=[labels[n] for n in pages if labels[n]],
                           applicability=['사양 적용 시'] if '사양 적용 시' in text else [],
                           terms=[entry['title']], linkedWarningIds=[],
                           contextIds=[entries[i]['id'] for i in entry['ancestors']],
                           containsUninterpretedFigures=any(n in image_pages for n in pages)))
    by_title = {}
    for chunk in chunks:
        by_title.setdefault(chunk['headingPath'][-1], []).append(chunk)
    for chunk in chunks:
        links = set()
        for title, matches in by_title.items():
            if not re.search(r'주의|안전|경고|제한', title):
                continue
            if title in chunk['text']:
                for match in matches:
                    if match['headingPath'][0] == chunk['headingPath'][0]:
                        links.add(match['id'])
        # Safety introductions beneath the same parent also apply to its operating subsections.
        for sibling in chunks:
            if (chunk['parentSectionId'] is not None and
                    sibling['parentSectionId'] == chunk['parentSectionId'] and sibling['id'] != chunk['id']):
                if re.search(r'안전을 위한 주의사항|사용 시 주의사항|안전정보', sibling['headingPath'][-1]):
                    links.add(sibling['id'])
        links.discard(chunk['id'])
        chunk['linkedWarningIds'] = sorted(links)
    # Fail rather than silently dropping body pages or leaving the known banner font undecoded.
    missing = sorted(set(range(1, len(pdf) + 1)) - set(EXCLUDED_PAGES) - covered_pages)
    if missing:
        raise ValueError(f'Uncovered body pages: {missing}')
    bad = [(c['id'], re.findall(r'[\u0400-\u0fff]+', c['text'])) for c in chunks
           if re.search(r'[\u0400-\u0fff]', c['text'])]
    if bad:
        raise ValueError(f'Unreviewed font mapping: {bad[:10]}')
    curation_bytes = Path(__file__).with_name('curation.json').read_bytes()
    curation = json.loads(curation_bytes)
    extracted_count = len(chunks)
    chunks, excluded_sections = curate(chunks, curation)
    opts.output.mkdir(parents=True, exist_ok=True)
    chunks_sha = write_json(opts.output / 'chunks.json', chunks)
    aliases = [
        ['급속충전', '급속 충전', 'DC 충전'], ['완속충전', '완속 충전', 'AC 충전'],
        ['충전선', '충전케이블', '충전 케이블', '충전커넥터', '충전 커넥터'],
        ['차박', '유틸리티 모드', '유틸리티모드'], ['V2L', '브이투엘', '전기 사용'],
        ['앞트렁크', '프렁크', '프론트 트렁크'], ['뒷트렁크', '테일게이트', '트렁크'],
        ['타이어 바람', '공기압', '타이어 압력', 'TPMS'], ['회생제동', '회생 제동', '아이페달', 'i-PEDAL'],
        ['스마트키', '스마트 키'], ['디지털키', '디지털 키'], ['오토홀드', '자동 정차', 'AUTO HOLD'],
        ['12V', '12 V', '보조 배터리'], ['주행가능거리', '주행 가능 거리'],
        ['성에', '김서림', '서리'], ['워셔액', '와셔액'], ['주차브레이크', '주차 브레이크', 'EPB'],
    ]
    aliases_sha = write_json(opts.output / 'aliases.json', aliases)
    write_json(opts.output / 'manifest.json', dict(
        schemaVersion=1, corpusId=CORPUS, modelYear=2027, market='A99', language='ko_KR', projectCode='NE1',
        title='2027 한국형 아이오닉 5 취급설명서', sourceUrl=SOURCE_URL, sourceSha256=SOURCE_SHA256,
        downloadedAt=opts.download_date, documentRevision=None, publicationDate='2026-09-17',
        pdfCreationDate=pdf.metadata['creationDate'], pdfPageCount=len(pdf),
        extractorVersion=EXTRACTOR, extractorLibrary='PyMuPDF==1.28.2', chunkCount=len(chunks),
        chunksSha256=chunks_sha, aliasesSha256=aliases_sha,
        curationVersion=curation['version'], curationSha256=hashlib.sha256(curation_bytes).hexdigest(),
    ))
    write_json(opts.output / 'coverage.json', dict(
        bodyPagesExtracted=sorted(covered_pages), extractionExcludedPages=EXCLUDED_PAGES,
        extractedChunkCount=extracted_count, includedChunkCount=len(chunks),
        curationVersion=curation['version'], excludedSections=excluded_sections,
        includedSectionPages=sorted({p for c in chunks for p in range(c['pdfPageStart'], c['pdfPageEnd'] + 1)}),
        tablePages=table_pages, uninterpretedFigurePages=image_pages,
        uninterpretedIconPages=[n for n, icons in icons_by_page.items() if icons],
        limitations=['Graphic-only information is not interpreted. Answer only from extracted text.',
                     'OWNS picture-font characters are replaced with [그림]; their encoded letters are not icon labels.',
                     'Table merged cells are repeated by their actual PDF cell bounds; no value is inferred.',
                     'PDF printed page labels come from the footer; front matter has no printed page label.'],
    ))
    print(json.dumps(dict(chunks=len(chunks), coveredPages=len(covered_pages), tablePages=len(table_pages),
                          characters=sum(len(c['text']) for c in chunks)), ensure_ascii=False))


if __name__ == '__main__':
    main()
