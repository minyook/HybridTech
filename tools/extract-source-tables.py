# 소스 안내
# PDF 원본의 표/도식 셀을 대조해 구조 자료와 검증 근거를 만드는 PC 도구. 실행 모델의 답변을 학습/생성하지 않는다.
"""Extract source-linked table cells on the PC; Android never parses PDFs for answers.

Run with the bundled Python (pdfplumber). Cells come from PDF coordinates, not an
LLM or word association. Unknown/ambiguous grids are audited and left unchanged.
"""
from pathlib import Path
import argparse
import hashlib
import json
import re
import unicodedata
import pdfplumber

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--output-dir", type=Path, default=Path("tmp/source-tables"), help="Audit output directory, relative to the repository root unless absolute")
args = parser.parse_args()
OUT = ROOT / args.output_dir
OUT.mkdir(parents=True, exist_ok=True)


def canonical(text):
    result, positions = [], []
    for i, char in enumerate(text):
        for c in unicodedata.normalize("NFKC", char).lower():
            if c.isalnum():
                result.append(c)
                positions.append(i)
    return "".join(result), positions


def clean(text):
    # y_tolerance below keeps chemical subscripts in x order with their formula.
    return re.sub(r"\s+", " ", (text or "").replace("\x07", " ")).strip()


def kotlin(text):
    return json.dumps(text, ensure_ascii=False).replace("$", "\\$")


corpus = json.loads((ROOT / "app/src/main/assets/knowledge/seed_chunks_v1.json").read_text(encoding="utf-8"))["chunks"]
documents = [("confined_space_safety_guide_2022", "safety-guide-2022.pdf"), ("confined_space_checklist_2026", "checklist-2026.pdf")]
records, audit = [], []
for document, filename in documents:
    path = ROOT / "app/src/main/assets/source-pdfs" / filename
    pdf_hash = hashlib.sha256(path.read_bytes()).hexdigest()
    pages = {c["pageNumber"]: c for c in corpus if c["documentId"] == document}
    with pdfplumber.open(path) as pdf:
        for number, page in enumerate(pdf.pages, 1):
            chunk = pages.get(number)
            if chunk is None:
                continue
            body = chunk["body"]
            body_key, positions = canonical(body)
            # A labelled concentration diagram has no grid. Read the text below
            # each aligned label by x coordinates; never zip the flattened text.
            caption = re.search(r"│(산소농도별\s*인체\s*영향)│", body)
            if caption:
                words = page.extract_words(x_tolerance=2, y_tolerance=5)
                labels = sorted([w for w in words if re.fullmatch(r"산소농도\d+(?:\.\d+)?%", clean(w["text"]))], key=lambda w: w["x0"])
                if len(labels) >= 3 and max(w["top"] for w in labels) - min(w["top"] for w in labels) < 2:
                    centers = [(w["x0"] + w["x1"]) / 2 for w in labels]
                    xs = [centers[0] - (centers[1] - centers[0]) / 2] + [(a + b) / 2 for a, b in zip(centers, centers[1:])] + [centers[-1] + (centers[-1] - centers[-2]) / 2]
                    top = max(w["bottom"] for w in labels) + 3
                    following = [w["top"] for w in words if w["top"] > top and w["height"] > labels[0]["height"] + 1 and w["x1"] - w["x0"] > page.width / 3]
                    bottom = min(following) - 4 if following else top
                    def diagram_text(left, right):
                        text = clean(page.crop((left, top, right, bottom)).extract_text(x_tolerance=2, y_tolerance=5))
                        # Only repair spaces inside known words broken by column wrapping.
                        for word in ("증가", "메스꺼움", "근력저하", "불능", "의식불명"):
                            text = re.sub(r"\s*".join(word), word, text)
                        return re.sub(r"불능\s+으로", "불능으로", text)
                    rows = [[clean(label["text"]).removeprefix("산소농도"), diagram_text(left, right)] for label, left, right in zip(labels, xs, xs[1:])]
                    last_key = canonical(rows[-1][-1])[0]
                    start = caption.start()
                    end_key = body_key.find(last_key, len(canonical(body[:caption.end()])[0])) if last_key else -1
                    end = positions[end_key + len(last_key) - 1] + 1 if end_key >= 0 else start
                    if end > start and all(cell and canonical(cell)[0] in canonical(body[start:end])[0] for row in rows for cell in row):
                        record = dict(id=f"{document}-p{number}-t1", documentId=document, page=number, chunkIndex=chunk["chunkIndex"], sourceBodySha256=hashlib.sha256(body.encode()).hexdigest(), pdfSha256=pdf_hash, title=clean(caption.group(1)), rawText=body[start:end], headers=["산소농도", "인체 영향"], rows=rows, bbox=[xs[0], labels[0]["top"], xs[-1], bottom])
                        records.append(record)
                        audit.append(dict(documentId=document, page=number, bbox=record["bbox"], accepted=True, id=record["id"], method="aligned concentration labels and column crops", cells=[record["headers"]] + rows))
            for table in page.find_tables():
                if table.bbox[1] < 40 or table.bbox[0] < 40 or table.bbox[2] > page.width - 40 or len(table.rows) < 3:
                    continue  # Printing marks, diagrams and non-table margins.
                header_cells = table.rows[0].cells
                if any(c is None for c in header_cells) or not 2 <= len(header_cells) <= 14:
                    continue
                xs = sorted({round(x, 4) for c in header_cells for x in (c[0], c[2])})
                if len(xs) != len(header_cells) + 1:
                    continue
                rows = []
                for row in table.rows:
                    cells = [c for c in row.cells if c is not None]
                    top, bottom = min(c[1] for c in cells), max(c[3] for c in cells)
                    rows.append([clean(page.crop((left, top, right, bottom)).extract_text(x_tolerance=2, y_tolerance=5)) for left, right in zip(xs, xs[1:])])
                if any(not c for c in rows[0]) or any(not c for row in rows[1:] for c in row):
                    audit.append(dict(documentId=document, page=number, bbox=table.bbox, accepted=False, reason="empty/merged cells require separate manual review", cells=rows))
                    continue
                header_key = canonical("".join(rows[0]))[0]
                last_key = canonical("".join(rows[-1]))[0]
                start = body_key.find(header_key)
                end = body_key.find(last_key, max(0, start))
                if start < 0 or end < 0:
                    audit.append(dict(documentId=document, page=number, bbox=table.bbox, accepted=False, reason="PDF headers/last row do not match the current source chunk", cells=rows))
                    continue
                end += len(last_key)
                span_key = body_key[start:end]
                if any(canonical(c)[0] not in span_key for row in rows for c in row):
                    audit.append(dict(documentId=document, page=number, bbox=table.bbox, accepted=False, reason="some cell values missing from the exact source span", cells=rows))
                    continue
                raw_start, raw_end = positions[start], positions[end - 1] + 1
                # Include an existing caption and its original delimiters.
                caption = re.search(r"│([^│]+)│\s*$", body[:raw_start])
                title = clean(caption.group(1)) if caption else rows[0][0]
                if caption:
                    raw_start = caption.start()
                while raw_end < len(body) and body[raw_end] in ")]）】":
                    raw_end += 1
                record = dict(id=f"{document}-p{number}-t{sum(r['documentId']==document and r['page']==number for r in records)+1}", documentId=document, page=number, chunkIndex=chunk["chunkIndex"],
                              sourceBodySha256=hashlib.sha256(body.encode()).hexdigest(), pdfSha256=pdf_hash, title=title, rawText=body[raw_start:raw_end], headers=rows[0], rows=rows[1:], bbox=list(table.bbox))
                records.append(record)
                audit.append(dict(documentId=document, page=number, bbox=table.bbox, accepted=True, id=record["id"], cells=rows))

manifest = dict(version=1, method="pdfplumber PDF cell coordinates; cell text checked against exact chunk span; no inference/forward-fill", records=records, audit=audit)
if not records:
    raise RuntimeError("No verified tables extracted; existing generated Kotlin was not changed")
(OUT / "source-tables.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
lines = ["package com.minyook.sllm2.data", "", "// Generated from PDF cell coordinates by tools/extract-source-tables.py. Do not edit cells manually.", "internal object SourceTableData {", "    val tables: List<SourceTable> = listOf("]
for r in records:
    lines += ["        SourceTable(", f"            {kotlin(r['id'])}, {kotlin(r['documentId'])}, {r['page']}, {r['chunkIndex']},", f"            {kotlin(r['sourceBodySha256'])}, {kotlin(r['pdfSha256'])},", f"            {kotlin(r['title'])},", f"            {kotlin(r['rawText'])},", "            listOf(" + ", ".join(kotlin(c) for c in r["headers"]) + "),", "            listOf("]
    lines += ["                listOf(" + ", ".join(kotlin(c) for c in row) + ")," for row in r["rows"]]
    lines += ["            ),", "        ),"]
lines += ["    )", "}", ""]
(ROOT / "app/src/main/java/com/minyook/sllm2/data/SourceTableData.kt").write_text("\n".join(lines), encoding="utf-8")
print(json.dumps(dict(accepted=len(records), pages=sorted({r['page'] for r in records}), rejected=sum(not a['accepted'] for a in audit)), ensure_ascii=False))
