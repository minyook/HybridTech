# Field Guard · Gemma 4 E2B 학습 데이터 v1

작성일 2026-11-06. Android 앱의 Gemma 4 E2B가 질문 범위를 지키고, 필요한 설명을 읽기 쉽게 구성하고, 확인할 수 없는 기준이나 현장 상태를 추측하지 않는지 실험하기 위한 한국어 SFT 데이터다. 앱 코드 `995290b`의 프롬프트와 출력 형식을 기준으로 구성했다.

| 파일 | 역할 | 일반 답변 | 근거 보충 | 빈 보충 | 합계 |
| --- | --- | ---: | ---: | ---: | ---: |
| [train.jsonl](train.jsonl) | 학습 | 41 | 10 | 7 | 58 |
| [validation.jsonl](validation.jsonl) | 학습 설정 비교 | 8 | 2 | 2 | 12 |
| [test.jsonl](test.jsonl) | 설정 선택 후 최종 평가 | 8 | 2 | 2 | 12 |

[index.jsonl](index.jsonl)은 각 행의 ID, 파일 내 줄 번호, 시나리오 묶음, 질문, 답변 종류와 근거 쪽을 연결한다. 줄 번호는 1부터 센다. 시나리오 묶음 36개를 파일 간 겹치지 않게 나눴고, 기존 Android 150문항과 추가 24문항, 집중 검색·가스 범위 평가표에 있는 질문과 정규화한 문장이 정확히 일치하는지 확인했다.

## 예시가 학습시키는 동작

- 일반 답변은 한국어 Markdown 본문이다. 짧은 요청에는 완결 문장을, 비교와 단계 설명에는 표나 목록을 사용한다. 이전 대화에서 대상을 좁히거나 바로잡는 요청, 미확인 수량·장비 사양·현장 허가를 다루는 사례를 포함한다.
- 문서 보충은 `{"evidenceIds":["e…-…"],"text":"한두 문장의 보충 설명"}`이다. 선택된 문단의 ID를 그대로 사용하고, 질문이 요구한 조건이나 이유만 풀어 쓴다. 확인할 근거가 부족하거나 같은 문장을 반복해 달라는 요청의 목표 출력은 `{"evidenceIds":[],"text":""}`다.
- 보충에 사용한 근거는 저장소에 들어 있는 2022년 밀폐공간 안전작업 가이드의 원문 발췌다. 학습용 질문·대화·목표 답변은 이 실험을 위해 작성한 합성 예시다. [고용노동부 배포 페이지](https://www.moel.go.kr/local/boryeong/info/dataroom/view.do?bbs_seq=20230600030)

예를 들어 “전등 전압과 방수등급을 알려줘. 모델은 아직 몰라. 두 문장으로.”에는 다음처럼 답한다.

> 전압과 방수등급을 확인하려면 전등의 정확한 모델명이 필요해요. 전등 명판이나 제조사 자료의 정격 전압과 IP 표기를 확인해 주세요.

## 학습 도구에서 읽는 방법

각 파일은 UTF-8 JSONL이고, 한 줄은 `messages` 배열 하나를 가진다. 첫 메시지는 앱이 Gemma에 전달하는 전체 입력이다. 두 번째 메시지는 모델이 생성할 목표 답변이다. 최근 대화가 있는 사례는 실제 앱처럼 첫 메시지 안의 JSON 문맥으로 들어간다. [TRL 대화형 데이터 형식](https://huggingface.co/docs/trl/en/dataset_formats)

압축을 푼 폴더에서 다음처럼 읽을 수 있다. 정답에만 손실을 적용하는 TRL 구성을 쓸 때는 반환값의 `prompt`와 `completion`을 대화형 데이터로 전달한다. 최종 평가에서는 `prompt`로 생성한 결과를 `completion`과 비교한다.

```python
import json
from pathlib import Path

def read_split(name):
    path = Path(f"{name}.jsonl")
    with path.open(encoding="utf-8") as source:
        for line in source:
            user, assistant = json.loads(line)["messages"]
            yield {"prompt": [user], "completion": [assistant]}

train_rows = list(read_split("train"))
validation_rows = list(read_split("validation"))
test_rows = list(read_split("test"))
```

Gemma 4 E2B IT의 공식 채팅 템플릿을 사용한다. 학습에서는 질문과 목표 답변을 함께 렌더링하고, 평가 입력에는 질문만 넣어 생성 시작 토큰을 붙인다. 앱 조건과 맞춰 `enable_thinking=False`로 확인했다. `index.jsonl`은 검토용 메타데이터다. [공식 Gemma 4 E2B 채팅 템플릿](https://huggingface.co/google/gemma-4-E2B-it/blob/main/chat_template.jinja)

## 확인 범위

82개 모두 UTF-8과 Arrow JSONL 로딩, 근거 ID 대응, 공식 Gemma 4 E2B 채팅 템플릿 렌더링과 토큰화를 확인했다. 가장 긴 학습 레코드는 578토큰, 가장 긴 목표 응답은 109토큰이었다. 일반 답변 57개는 앱의 일반 답변 필터를 통과했고, 보충 25개는 앱 검증기에서 기대한 보충 또는 빈 출력으로 처리됐다. 이 PC의 기본 Python 3.14에서 `datasets.load_dataset`은 `dill` 호환 오류가 발생해 Arrow로 읽은 데이터를 `Dataset`으로 구성해 대조했다. 위 코드는 Python 표준 라이브러리로 파일을 읽는다.

검증은 예시와 앱 출력 형식까지 진행했다. 현장 적합성과 미세조정 효과는 별도 평가가 필요한 초기 데이터다. 학습 결과를 비교할 때는 문장 일치율보다 질문 범위, 필수 조건의 보존, 근거 ID의 적합성, 확인되지 않은 수치나 허가 단정 여부를 본다.
