# 소스 안내
# 모델 비교 JSON/추가 진단을 모아 지표·원문·표 형식 보고서를 구성한다. 실패/빈 출력을 정상 답변 속도와 구분한다.
"""Summarize measured Android comparison reports; never infer factual accuracy.

Usage: python tools/summarize-model-comparison.py OUTPUT_DIR REPORT.json ...
"""
import csv
import hashlib
import json
import math
import statistics
import sys
from pathlib import Path


def percentile(values, fraction):
    values = sorted(values)
    if not values:
        return None
    position = (len(values) - 1) * fraction
    lower = math.floor(position)
    upper = math.ceil(position)
    return values[lower] + (values[upper] - values[lower]) * (position - lower)


def stats(values):
    return {
        "n": len(values),
        "mean": statistics.mean(values) if values else None,
        "median": statistics.median(values) if values else None,
        "p95": percentile(values, .95),
        "min": min(values) if values else None,
        "max": max(values) if values else None,
    }


def output_characters(row):
    # Match Android String.length, including UTF-16 surrogate pairs.
    return row.get("outputChars", len(row["rawAnswer"].encode("utf-16-le")) // 2)


def main():
    destination = Path(sys.argv[1])
    destination.mkdir(parents=True, exist_ok=True)
    inputs = [Path(argument) for argument in sys.argv[2:]]
    reports = [json.loads(path.read_text(encoding="utf-8-sig")) for path in inputs]
    if not reports or any(report["phase"] != "complete" for report in reports):
        raise ValueError("Only complete reports can form the final comparison")
    rows = [row for report in reports for row in report["rows"]]
    groups = {}
    for row in rows:
        groups.setdefault(row["modelId"], []).append(row)
    evidence = {}
    for row in rows:
        case = row.get("caseId", row["question"])
        packet = (row["question"], row["reference"])
        if case in evidence and evidence[case] != packet:
            raise ValueError("Question/reference mismatch between models: " + case)
        evidence[case] = packet
    conditions = {(report["suiteId"], report["contextTokens"], report["outputTokens"], report["requestedBackend"]) for report in reports}
    if len(conditions) != 1:
        raise ValueError("Reports have different comparison conditions")
    suite, context, output, backend = next(iter(conditions))
    summary = {
        "scope": "Measured physical Android native generation. Common precomputed retrieval is excluded. FirstSentenceMs means first displayable completed prefix and may be a heading. One run per question; not a repeated statistical benchmark or automatic correctness grade.",
        "suiteId": suite, "contextTokens": context, "maxOutputTokens": output,
        "requestedBackend": backend, "device": reports[0]["device"],
        "reports": [{"path": str(path), "sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "phase": report["phase"], "settingsUnchanged": report.get("settingsUnchanged")} for path, report in zip(inputs, reports)],
        "commonQuestionAndReferenceVerified": True,
        "models": {},
    }
    for model, group in groups.items():
        expected_cases = {case["id"] for case in reports[0]["caseSpecifications"]}
        if len(group) != len(expected_cases) or {row.get("caseId") for row in group} != expected_cases:
            raise ValueError("Missing or duplicate cases for " + model)
        valid = [row for row in group if not row.get("error") and row.get("rawAnswer", "").strip()]
        warm = [row for row in valid if row.get("reusedEngine")]
        summary["models"][model] = {
            "attempted": len(group), "nonEmptyTextResponses": len(valid), "runtimeErrorsOrEmptyOutputs": len(group) - len(valid),
            "actualBackends": sorted({row["backend"] for row in group}),
            "allAttemptSeconds": stats([row["totalMs"] / 1000 for row in group]),
            "failedAttemptSeconds": stats([row["totalMs"] / 1000 for row in group if row not in valid]),
            "totalSeconds": stats([row["totalMs"] / 1000 for row in valid]),
            "warmTotalSeconds": stats([row["totalMs"] / 1000 for row in warm]),
            "firstDisplayableSeconds": stats([row["firstSentenceMs"] / 1000 for row in valid if row.get("firstSentenceMs") is not None]),
            "initializationSeconds": stats([row["initializationMs"] / 1000 for row in valid]),
            "outputCharacters": stats([output_characters(row) for row in valid]),
            "sdkOutputTokens": stats([row["outputTokens"] for row in valid if row.get("outputTokens") is not None]),
            "sdkTokensPerSecond": stats([row["tokensPerSecond"] for row in valid if row.get("tokensPerSecond") is not None]),
            "availableSystemRamMbBefore": stats([row["availableRamMb"] for row in group]),
            "thermalStatusBefore": sorted({row["thermalStatus"] for row in group}),
            "casesWithIncompleteEvidence": [row.get("caseId") for row in group if row.get("missingEvidenceQueries")],
            "knownOutputLimitHits": sum(row.get("outputLimitReached") is True for row in valid),
            "unknownOutputLimitStatus": sum(row.get("outputLimitReached") is None for row in valid),
            "qualityGrade": "See separate human review; successful generation is not factual correctness.",
        }
    (destination / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    with (destination / "measurements.csv").open("w", encoding="utf-8-sig", newline="") as stream:
        fields = ["modelId", "caseId", "caseTitle", "backend", "firstDisplayableSeconds", "totalSeconds", "initializationSeconds", "outputCharacters", "outputTokens", "tokensPerSecond", "reusedEngine", "availableRamMb", "thermalStatus", "error"]
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        for row in rows:
            record = {name: row.get(name) for name in fields}
            record.update(firstDisplayableSeconds=row["firstSentenceMs"] / 1000 if row.get("firstSentenceMs") is not None else None,
                          totalSeconds=row["totalMs"] / 1000, initializationSeconds=row["initializationMs"] / 1000,
                          outputCharacters=output_characters(row))
            writer.writerow(record)
    answers = ["# 실제 휴대폰 모델별 현장 상세 답변", "", f"기기: {reports[0]['device']} · 요청 {backend} · 문맥 {context} · 최대 출력 {output}토큰", "", "직접 생성한 원문을 보존한다. 답변의 정확성과 안전성 검증은 별도 검토 문서를 참고한다.", ""]
    for case, (question, reference) in evidence.items():
        case_rows = [row for row in rows if row.get("caseId", row["question"]) == case]
        answers.extend([f"## {case_rows[0].get('caseTitle') or case}", "", question, "", "### 공통 문서 근거", "", reference, ""])
        for row in case_rows:
            first = "미측정" if row.get("firstSentenceMs") is None else f"{row['firstSentenceMs']/1000:.2f}초"
            answers.extend([f"### {row['modelId']}", "", f"실제 {row['backend']} · 첫 표시 {first} · 전체 {row['totalMs']/1000:.2f}초 · {output_characters(row)}자", ""])
            if row.get("error"):
                answers.extend([f"> 실행 오류: {row['error']}", ""])
            answers.extend([row["rawAnswer"] or "(생성 내용 없음)", ""])
    (destination / "answers.md").write_text("\n".join(answers), encoding="utf-8")
    print(json.dumps(summary["models"], ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
