# Field Guard · Gemma 4 E2B 학습 데이터 v2

작성일 2026-11-06. Gemma 4 E2B IT의 첫 미세조정 비교 실험을 위해 학습용 120개, 검증용 24개, 최종 평가용 24개를 만들었다. 질문·목표 답변 한 쌍이 사례 한 개다. 앱 코드 `995290b`의 생성 경로와 출력 검증을 기준으로 구성했다.

| 파일 | 용도 | 일반 답변 | 근거 보충 | 빈 보충 | 합계 |
| --- | --- | ---: | ---: | ---: | ---: |
| [train.jsonl](train.jsonl) | 미세조정 | 83 | 20 | 17 | 120 |
| [validation.jsonl](validation.jsonl) | 학습 설정과 체크포인트 선택 | 16 | 4 | 4 | 24 |
| [test.jsonl](test.jsonl) | 설정 확정 후 기본 모델과 비교 | 16 | 4 | 4 | 24 |
| [app-eval.jsonl](app-eval.jsonl) | 검색부터 화면 답변까지 평가 | | | | 12 |

[index.jsonl](index.jsonl)은 Gemma 사례의 ID, 파일 내 줄 번호, 시나리오, 질문과 출처를 연결한다. 줄 번호는 1부터 센다. 동일 시나리오 묶음은 한 분할에만 두었다. 기존 v1의 82개 사례를 포함해 이번에 생성 사례 86개를 추가했다.

## 이 데이터에서 배우는 답변

- 작업자에게 필요한 행동이나 결론을 먼저 말한다. 비교는 표로, 순서가 중요할 때는 번호 목록으로 답한다.
- 후속 질문에서 대상과 범위를 좁힌다. 예를 들어 “위치만”이라고 하면 보고서 전체를 다시 설명하지 않고 위치 항목에 답한다.
- 측정값, 수신 상태, 허가처럼 실제 확인이 필요한 항목을 구분한다. 장비 모델이나 현장 상태가 없을 때 무엇을 확인할지 말한다.
- 문서 보충은 앱에서 받은 근거 ID를 사용해 조건을 풀어 쓴다. 중복 요청이나 근거 없는 수치 요청에는 빈 보충을 목표로 삼는다. 실제 보충의 근거는 저장소에 포함된 2022년 밀폐공간 안전작업 가이드에서 발췌했다. [고용노동부 배포 페이지](https://www.moel.go.kr/local/boryeong/info/dataroom/view.do?bbs_seq=20230600030)

`app-eval.jsonl`의 답변은 **앱 전체를 평가할 때 사용하는 기준 답안**이다. 가스별 농도표, 경계값, 작업 중 경보와 환기 중단, 측정 시각 예시가 들어 있다. 앱은 농도 같은 문서 기준을 검색해 직접 조립하고, Gemma가 생성하는 일반 설명에는 별도의 출력 제약을 적용한다. 이 차이를 반영해 앱 평가 문항은 학습·검증·최종 평가용 세 파일과 별도로 저장했다. [적정공기 정의](https://www.law.go.kr/LSW/lsSideInfoP.do?docCls=jo&joBrNo=00&joNo=0618&lsiSeq=273603&urlMode=lsScJoRltInfoR)

## 첫 학습을 비교하는 방법

120개는 답변 형식과 질문 범위가 개선되는지 보는 **실험 시작점**이다. 같은 24개 최종 평가 질문으로 기본 Gemma 4 E2B와 미세조정한 모델을 비교한다. 검증용 24개로 체크포인트를 먼저 고른 다음 최종 평가 파일을 사용한다. 가스별 수치와 작업 중 조치는 앱 전체 평가 12개로 따로 확인한다. [Gemma 미세조정 가이드](https://ai.google.dev/gemma/docs/tune)

예를 들어 형식은 좋아졌는데 조건 누락이 반복되면, 그 실패 유형에 새 상황과 대조 사례를 추가한다. 여러 유형에서 실패가 계속 나타날 때 300~500개를 다음 학습 규모로 잡을 수 있다. 실제 현장 질문과 전문가가 검토한 답변을 추가하고, 데이터 수보다 필수 조건 보존과 작업 허가 오판 여부를 비교한다. 이 수량은 Field Guard의 실험 계획이다.

## 파일 읽기

Gemma 사례는 UTF-8 JSONL 한 줄에 `user`와 `assistant` 메시지 하나씩을 담는다. 학습의 입력에는 전체 앱 프롬프트가 들어 있고, 목표 응답은 Markdown 본문 또는 보충 JSON이다. TRL의 대화형 `prompt` / `completion` 형식으로 넘기려면 다음처럼 읽는다. [TRL 데이터 형식](https://huggingface.co/docs/trl/en/dataset_formats)

```python
import json
from pathlib import Path

def read_split(name):
    with Path(f"{name}.jsonl").open(encoding="utf-8") as source:
        for line in source:
            user, assistant = json.loads(line)["messages"]
            yield {"prompt": [user], "completion": [assistant]}

train_rows = list(read_split("train"))
```

학습에는 공식 Gemma 4 E2B IT 채팅 템플릿을 적용한다. 평가할 때는 `prompt`만 렌더링해 생성하고, `completion`과 비교한다. 검증 때는 현재 앱처럼 `enable_thinking=False`를 사용했다. [공식 Gemma 4 E2B 채팅 템플릿](https://huggingface.co/google/gemma-4-E2B-it/blob/main/chat_template.jinja)

## 확인 범위

생성 사례 168개의 앱 프롬프트, 앱 출력 검사, JSONL 구조, 근거 ID와 토큰화를 검증했다. 앱 전체 평가 12개는 기준 답안과 채점 조건을 담은 파일이다. 이번 결과는 합성 예시의 정합성 검사이며 실제 미세조정 전후의 성능과 현장 적용 적합성은 후속 평가 항목이다.
