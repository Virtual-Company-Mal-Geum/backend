# GEO 개선 전후 비교 프론트엔드 명세서

- 버전: 2.0
- 작성일: 2026-09-24
- 대상: GEO 서비스 프론트엔드·백엔드 개발자
- Base URL: `/api/v1/geo`
- 인증 방식: JWT Bearer (`Authorization: Bearer <accessToken>`)
- 관련 문서: [`GEO_credit_frontend_api_spec.md`](GEO_credit_frontend_api_spec.md) v2.2 — 크레딧, 개선안 포함 주문(`withImprovement`), 오류 응답 형식(§4.5)

v2.0부터는 새 리소스나 엔드포인트를 만들지 않는다. 기존 주문·리포트 API 3개(`POST /order`, `GET /reports`, `GET /report/{orderId}`)에 필드만 추가한다.

## 1. 범위

### 1.1 핵심 개념

**재평가 = 원 주문을 기준으로 삼는 새 주문**이다. 개선안(JSON-LD)을 에이전시가 적용한 뒤, 같은 URL을 다시 주문해 채점한다. 새 주문은 `baselineOrderId`로 원 주문에 묶인다.

| 용어 | 의미 |
| --- | --- |
| 원 주문 (최초 평가) | `withImprovement: true`로 요청한 주문. `baselineOrderId`가 `null` |
| 재평가 주문 | 개선안 적용 후 다시 채점한 주문. `baselineOrderId` = 원 주문 id |
| 버전 | 원 주문 1개와 재평가 주문 N개를 합쳐 부르는 말 |
| 기준(before) | 비교 뷰의 왼쪽. 항상 원 주문 |
| 비교 대상(after) | 비교 뷰의 오른쪽. 사용자가 고른 재평가 주문 |

### 1.2 대상

- 개선안과 함께 요청한 주문(`withImprovement: true`, Pack·Partner)만 재평가할 수 있다. 개선안 포함 주문은 상위 문서 §11을 따른다.
- 프론트는 플랜으로 분기하지 않는다. 재평가를 할 수 없는 주문은 서버가 `reevalRemaining: null`을 주고, 프론트는 이 값만 보고 버튼을 숨긴다. FREE 주문이 여기에 해당한다.

### 1.3 제외

- 재평가 전에 JSON-LD 반영 여부를 먼저 확인하는 기능(§9)
- 재평가끼리 비교(예: 재평가 1 vs 재평가 2)
- 대시보드 상단 통계 카드(`stats-row`) 변경

## 2. 사용자 흐름

1. 고객이 개선안과 함께 주문한다. 완료되면 결과 상세에서 개선안 JSON-LD를 복사해 에이전시에 전달한다.
2. 에이전시가 사이트에 적용한다. 우리 서비스는 이 단계에 관여하지 않는다.
3. 고객이 결과 상세에서 **개선안 적용 완료 → 재평가 요청**을 누른다. 프론트는 `POST /order`에 `{ baselineOrderId }`를 보낸다.
4. 프론트가 새 주문을 `GET /report/{newOrderId}`로 polling한다. 완료되면 전후 비교 뷰로 전환한다.
5. 대시보드 목록의 원 주문 행에 `55 → 71.5 (+16.5)`가 표시된다.

## 3. API 변경

### 3.1 요약

| 메서드 | 경로 | 변경 |
| --- | --- | --- |
| POST | `/order` | 요청에 `baselineOrderId` 추가 |
| GET | `/reports` | 재평가 주문은 목록에서 제외. 각 행에 점수·재평가 요약 필드 추가 |
| GET | `/report/{orderId}` | `baselineOrderId`, `versions`, `reevalRemaining`, `reevalExpiresAt` 추가 |
| POST | `/report/delete/{orderId}` | 원 주문을 삭제하면 재평가 주문도 함께 숨김. 요청 형식은 변경 없음 |

전후 비교용 API는 없다. 프론트가 `GET /report/{원 주문}`과 `GET /report/{재평가 주문}`을 각각 호출해서 비교한다.

### 3.2 POST `/order` — 재평가 요청

재평가일 때는 `baselineOrderId`만 보낸다. URL, 도메인 같은 나머지 값은 서버가 원 주문에서 복사하고, 요청에 들어온 다른 필드는 무시한다.

```http
POST /api/v1/geo/order
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "baselineOrderId": 128
}
```

응답 `200 OK`는 기존과 같다.

```json
{
  "message": "GEO 분석 요청이 접수되었습니다.",
  "orderId": 141
}
```

`orderId`는 **새 재평가 주문의 id**다.

일반 주문(`baselineOrderId` 없음)의 요청과 응답은 바뀌지 않는다.

서버 검증과 실패 응답:

| 조건 | HTTP | `code` |
| --- | ---: | --- |
| 원 주문이 없거나 본인 주문이 아님 | 404 | `RESOURCE_NOT_FOUND` |
| 원 주문이 재평가 대상이 아님 (개선안 없이 요청한 주문, 또는 원 주문 자체가 재평가 주문) | 409 | `REEVAL_NOT_ALLOWED` |
| 원 주문이 아직 `COMPLETED`가 아님 | 409 | `REEVAL_NOT_ALLOWED` |
| 같은 원 주문의 재평가가 `ACCEPTED`/`PROCESSING` 상태 | 409 | `REEVAL_IN_PROGRESS` |
| 재평가 횟수 소진 | 409 | `REEVAL_LIMIT_EXCEEDED` |
| 재평가 기한 경과 | 409 | `REEVAL_WINDOW_EXPIRED` |

실패 응답 형식은 상위 문서 §4.5를 따른다.

```json
{
  "code": "REEVAL_LIMIT_EXCEEDED",
  "message": "포함된 재평가를 모두 사용했습니다.",
  "retryable": false,
  "resourceId": "128",
  "traceId": "trace_4ca2"
}
```

진행 중인 재평가가 있으면 `REEVAL_IN_PROGRESS`로 막힌다. 그래서 버튼을 두 번 눌러도 재평가 주문이 중복으로 생기지 않는다.

### 3.3 GET `/reports` — 목록

- **재평가 주문은 목록에 나오지 않는다.** 원 주문 1건이 1행이다.
- 기존 필드(`orderId`, `siteName`, `targetUrl`, `domainStatus`, `jobStatus`, `createdAt`)는 그대로 두고 아래 필드를 추가한다.

```json
[
  {
    "orderId": 128,
    "siteName": "예시학원",
    "targetUrl": "https://academy.example.com/curriculum",
    "domainStatus": "EDUCATION",
    "jobStatus": "COMPLETED",
    "createdAt": "2026-09-14T16:00:00",

    "score": { "raw": 58.0, "total": 55.0 },
    "reevalCount": 1,
    "latestReeval": {
      "orderId": 141,
      "jobStatus": "COMPLETED",
      "score": { "raw": 71.5, "total": 71.5 }
    }
  }
]
```

| 필드 | 설명 |
| --- | --- |
| `score` | 이 주문의 점수. `jobStatus`가 `COMPLETED`가 아니면 `null` |
| `reevalCount` | 요청된 재평가 주문 수. 실패 건도 포함 |
| `latestReeval` | 가장 최근 재평가 주문. 없으면 `null`. `score`는 `COMPLETED`가 아니면 `null` |

### 3.4 GET `/report/{orderId}` — 상세

기존 필드(`orderId`, `targetUrl`, `jobStatus`, `aiResult`, `errorMessage`, `createdAt`)는 그대로 두고 아래 필드를 추가한다. 원 주문 id와 재평가 주문 id 어느 쪽으로 조회해도 같은 `versions`가 온다.

```json
{
  "orderId": 128,
  "targetUrl": "https://academy.example.com/curriculum",
  "jobStatus": "COMPLETED",
  "aiResult": { "...": "§4" },
  "errorMessage": null,
  "createdAt": "2026-09-14T16:00:00",

  "baselineOrderId": null,
  "versions": [
    { "orderId": 128, "jobStatus": "COMPLETED", "createdAt": "2026-09-14T16:00:00", "score": { "raw": 58.0, "total": 55.0 } },
    { "orderId": 141, "jobStatus": "COMPLETED", "createdAt": "2026-09-20T12:21:02", "score": { "raw": 71.5, "total": 71.5 } }
  ],
  "reevalRemaining": 1,
  "reevalExpiresAt": "2026-10-14T16:02:11"
}
```

| 필드 | 설명 |
| --- | --- |
| `baselineOrderId` | 조회한 주문이 재평가 주문이면 원 주문 id, 원 주문이면 `null` |
| `versions` | 원 주문이 첫 번째, 그 뒤로 재평가 주문이 `createdAt` 오름차순. 탭을 만들 때 쓴다 |
| `reevalRemaining` | 남은 재평가 횟수. 재평가 대상이 아닌 주문(FREE 등)이면 `null` |
| `reevalExpiresAt` | 재평가 기한. `reevalRemaining`이 `null`이면 `null` |

`reevalRemaining`과 `reevalExpiresAt`은 원 주문 기준 값이다. 재평가 주문으로 조회해도 같은 값이 온다.

## 4. `aiResult` 구조

`aiResult`는 AI 서버 응답을 그대로 저장한 값이다. **원 주문과 재평가 주문은 채점 결과가 들어 있는 키 이름이 다르다.**

| 주문 | AI 호출 | 채점 결과 위치 | 개선안 JSON-LD |
| --- | --- | --- | --- |
| 원 주문 (개선안 포함) | `/diagnose` | `aiResult.analysis` | `aiResult.jsonld` |
| 재평가 주문 | `/evaluate` | `aiResult.result` | 없음 |
| 무료 주문 | `/evaluate` | `aiResult.result` (서버에서 잘림, 아래 참고) | 없음 |

프론트는 아래 함수 하나로 두 형식을 모두 처리한다.

```javascript
// 채점 결과(EvaluationResult) 꺼내기. /diagnose는 analysis, /evaluate는 result.
const getEvaluation = (aiResult) => aiResult?.analysis ?? aiResult?.result ?? null;
```

`content_warning`은 두 형식 모두 `aiResult.content_warning`(최상위)에 있다.

무료 주문은 `limited: true`로 오고, 상세 항목 1개(`limitedDetailKey`)를 뺀 나머지 항목의 `evidence_summary`는 `null`, `improvements`는 `[]`다. 규칙은 상위 문서 §7.2를 따른다.

### 4.1 EvaluationResult — `getEvaluation()`의 반환값

```json
{
  "evaluation_version": "geo-eval-v2.2",
  "common_evaluation": {
    "entity_topic_clarity":            { "score": 11.0, "evidence_summary": "학원명과 과목은 명확하나 지점 정보가 흩어져 있음", "improvements": ["지점명을 제목과 본문 첫 문단에 명시"] },
    "answerability_content_structure": { "score": 12.0, "evidence_summary": "...", "improvements": ["..."] },
    "evidence_citation_readiness":     { "score": 9.0,  "evidence_summary": "...", "improvements": ["..."] },
    "cross_source_consistency":        { "score": 6.0,  "evidence_summary": "...", "improvements": ["..."] },
    "freshness_operational_trust":     { "score": 5.0,  "evidence_summary": "...", "improvements": ["..."] }
  },
  "domain_specific_completeness": {
    "max_score": 30,
    "score": 15.0,
    "evidence_summary": "...",
    "missing_or_uncertain": ["수강료", "개강일"],
    "improvements": ["..."]
  },
  "raw_score": 58.0,
  "global_caps_applied": [
    { "cap": "mostly generic promotional copy with few concrete facts", "reason": "..." }
  ],
  "total_score": 55.0,
  "score_band": "average",
  "on_page_geo_readiness": "medium",
  "top_3_failure_reasons": ["...", "...", "..."],
  "priority_actions": ["...", "..."]
}
```

| 필드 | 설명 |
| --- | --- |
| `common_evaluation` | 공통 5항목. 키는 고정이고, 5개가 항상 모두 온다 |
| `domain_specific_completeness` | 도메인 항목. 만점 30 |
| `raw_score` | 6개 항목 점수 합. 0~100 |
| `global_caps_applied` | 적용된 점수 상한 목록. 비어 있을 수 있다 |
| `total_score` | `raw_score`와 적용된 상한 중 가장 낮은 값 |

### 4.2 개선안 JSON-LD — `aiResult.jsonld`

- `aiResult.jsonld.valid === false`이면 생성에 실패한 것이다. JSON-LD 블록 대신 `개선안 JSON-LD를 생성하지 못했습니다.`를 표시한다.
- 그 외에는 `aiResult.jsonld` 객체를 표시한다. 성공 응답의 정확한 형식은 §12에서 확정한다.

## 5. 프론트 상수

### 5.1 채점 항목

기존 `geo-result.html`의 KPI 카드(`/ 50`, Schema Completeness, Information Density, GEO-Readiness)와 레이더·막대 차트의 축은 실제 AI 결과와 맞지 않는다. 아래 6개 항목으로 교체한다.

```javascript
// 표시 순서 고정. key는 EvaluationResult의 키와 1:1.
const SCORE_ITEMS = [
  { key: 'entity_topic_clarity',            label: '주제·엔티티 명확성', max: 15, group: 'common' },
  { key: 'answerability_content_structure', label: '답변 가능성·구조',   max: 20, group: 'common' },
  { key: 'evidence_citation_readiness',     label: '근거·인용 준비도',   max: 15, group: 'common' },
  { key: 'cross_source_consistency',        label: '출처 간 일관성',     max: 10, group: 'common' },
  { key: 'freshness_operational_trust',     label: '최신성·운영 신뢰',   max: 10, group: 'common' },
  { key: 'domain_specific_completeness',    label: '도메인 정보 완결성', max: 30, group: 'domain' },
];

function getItem(evaluation, item) {
  return item.group === 'common'
    ? evaluation.common_evaluation[item.key]
    : evaluation.domain_specific_completeness;
}
```

### 5.2 점수 상한(cap) 라벨

`global_caps_applied[].cap`은 아래 6개 문자열 중 하나로만 온다. 표에 없는 값이 오면 원문을 그대로 보여준다.

```javascript
const CAP_LABELS = {
  'html_text empty or nearly empty':                                            { label: '본문 텍스트 없음',             ceiling: 25 },
  'main entity or page purpose cannot be identified':                           { label: '페이지 주제 불명확',           ceiling: 35 },
  'page is mostly navigation, footer, login UI, ads, or repeated boilerplate':  { label: '메뉴·푸터·광고 위주 페이지',   ceiling: 30 },
  'mostly generic promotional copy with few concrete facts':                    { label: '구체적 사실이 적은 홍보 문구', ceiling: 55 },
  'major contradiction between visible HTML and metadata/JSON-LD':              { label: '본문과 JSON-LD 불일치',        ceiling: 50 },
  'no usable answerable content for plausible user questions':                  { label: '답변 가능한 콘텐츠 없음',      ceiling: 55 },
};
```

### 5.3 대표 점수

```javascript
// 상한(cap) 판정이 안정화되기 전까지는 raw_score를 대표 점수로 쓴다. (§7.2)
// 안정화되면 이 값만 'total_score'로 바꾼다.
const PRIMARY_SCORE = 'raw_score';
const pickScore = (s) => (s == null ? null : PRIMARY_SCORE === 'raw_score' ? s.raw : s.total);
```

`score` 객체(`{ raw, total }`)는 목록의 `score`, `latestReeval.score`, 상세의 `versions[].score`에 쓰인다.

## 6. 화면 명세

### 6.1 의뢰 대시보드 — `geo-personal.html`

**데이터**: `GET /reports`. 페이지네이션과 검색은 지금처럼 프론트에서 처리한다.

**행 구성**: 한 행이 원 주문 1건이다. 재평가 주문은 API가 내려주지 않는다.

| 영역 | 표시 |
| --- | --- |
| 제목 | `siteName`, 없으면 `targetUrl` |
| 보조 | `targetUrl` · 도메인 라벨(`EDUCATION` → 교육, `ECOMMERCE` → 이커머스, `NEWS` → 뉴스) |
| 날짜 | `createdAt` (`YYYY.MM.DD`) |
| 상태 배지 | 아래 상태 매핑 |
| 점수 | 아래 점수 표시 규칙 |
| 동작 | `COMPLETED`이면 `결과 보기`, `삭제` (기존과 같음) |

상태 매핑:

| `jobStatus` | `data-status` | 배지 |
| --- | --- | --- |
| `ACCEPTED` | `queued` | `◌ 대기 중` |
| `PROCESSING` | `progress` | `◉ 진행 중` |
| `COMPLETED` | `done` | `● 완료` |
| `FAILED` | `failed` | `✕ 실패` |

점수 표시 규칙:

| 조건 | 표시 예 |
| --- | --- |
| `jobStatus` ≠ `COMPLETED` | 점수 영역 비움 |
| `latestReeval === null` | `55점` |
| `latestReeval.jobStatus`가 `ACCEPTED`/`PROCESSING` | `55점` + 배지 `재평가 진행 중` |
| `latestReeval.jobStatus === 'COMPLETED'` | `55 → 71.5 (+16.5)` + 배지 `재평가 {reevalCount}회` |
| `latestReeval.jobStatus === 'FAILED'` | `55점` + 배지 `재평가 실패` |

변화량의 색상과 부호는 §7.1을 따른다.

**링크**: `geo-result.html?id={orderId}` (기존 파라미터 그대로)

### 6.2 결과 상세 — `geo-result.html`

**URL 파라미터**

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| `id` | 예 | 주문 id. 원 주문이든 재평가 주문이든 상관없다 |
| `view` | 아니오 | `{orderId}` \| `compare` |

**데이터 로드 순서**

1. `GET /report/{id}`로 `versions`, `reevalRemaining`, `reevalExpiresAt`을 받는다.
2. 원 주문 id = `baselineOrderId ?? orderId`
3. 탭을 열 때 해당 `orderId`로 `GET /report/{orderId}`를 호출한다. 받은 결과는 페이지 메모리에 캐시한다.

`view`가 없을 때 첫 화면:
- `id`가 재평가 주문이면 그 주문의 단일 보기
- `id`가 원 주문이고 `COMPLETED` 재평가가 하나라도 있으면 `compare`
- 그 외에는 원 주문의 단일 보기

#### 6.2.1 상단 구성

```
[메타 스트립]  URL · 도메인 · 최초 평가 완료일
[버전 탭]      최초 평가 09.14 | 재평가 1 09.20 | 재평가 2 ◉ | [전후 비교]
[재평가 요청 영역]
[본문]         선택한 탭의 단일 보기 또는 비교 뷰
```

버전 탭(`versions` 순서대로):

| 버전 | 탭 표시 | 클릭 |
| --- | --- | --- |
| 첫 번째 (원 주문) | `최초 평가 · MM.DD` | 단일 보기 |
| 재평가 `COMPLETED` | `재평가 N · MM.DD` | 단일 보기 |
| 재평가 `ACCEPTED`/`PROCESSING` | `재평가 N ◉` + 스피너 | 비활성 |
| 재평가 `FAILED` | `재평가 N ⚠` | 실패 안내 카드(§10) |

`전후 비교` 탭은 `COMPLETED` 재평가가 1개 이상일 때만 보인다.

#### 6.2.2 재평가 요청 영역

버튼 문구: **개선안 적용 완료 → 재평가 요청**
보조 문구: `남은 재평가 {reevalRemaining}회 · {reevalExpiresAt | MM.DD}까지 · 크레딧 차감 없음`

아래 조건을 **모두** 만족할 때만 버튼을 활성화한다. 하나라도 어긋나면 비활성화하고, 먼저 걸리는 사유 하나를 버튼 아래에 표시한다.

| 순서 | 조건 | 어긋날 때 |
| ---: | --- | --- |
| 1 | `reevalRemaining !== null` | 버튼 영역 전체를 숨김 |
| 2 | 원 주문 `jobStatus === 'COMPLETED'` | 버튼 영역 전체를 숨김 |
| 3 | `versions`에 `ACCEPTED`/`PROCESSING` 재평가가 없음 | `재평가가 진행 중입니다.` |
| 4 | `reevalRemaining > 0` | `포함된 재평가를 모두 사용했습니다.` |
| 5 | 현재 시각 < `reevalExpiresAt` | `재평가 기한(MM.DD)이 지났습니다.` |

#### 6.2.3 재평가 확인 모달

```
제목    개선안이 사이트에 적용되었나요?
본문    에이전시가 개선안 적용을 마친 뒤 요청해 주세요.
        적용 전에 요청하면 점수가 달라지지 않고 재평가 횟수만 차감됩니다.
버튼    [취소]  [재평가 요청]
```

- `재평가 요청`을 누르면 버튼을 비활성화하고 `POST /order`에 `{ "baselineOrderId": <원 주문 id> }`를 보낸다.
- `200`을 받으면 모달을 닫고 `GET /report/{원 주문 id}`를 다시 불러 탭과 남은 횟수를 갱신한 뒤, 응답의 `orderId`로 polling을 시작한다(§8).
- 오류 처리는 §10을 따른다.

#### 6.2.4 단일 보기

최초 평가와 재평가가 같은 레이아웃을 쓴다. 입력은 `getEvaluation(aiResult)` 하나다.

| 순서 | 블록 | 내용 |
| ---: | --- | --- |
| 1 | 경고 배너 | `aiResult.content_warning`이 있을 때만: `본문 텍스트가 적거나 메뉴·약관 위주인 페이지라 점수 신뢰도가 낮을 수 있습니다.` |
| 2 | 점수 KPI | 대표 점수 `/ 100`, `score_band` 라벨. 보조 줄에 다른 쪽 점수. 예: `상한 적용 점수 55`. 상한이 있으면 상한 칩(§5.2 라벨) |
| 3 | 항목 막대 | `SCORE_ITEMS` 6개. `점수 / 만점`과 막대 |
| 4 | 레이더 차트 | 6개 항목, 축 값 = `점수 / 만점 × 100` |
| 5 | 항목별 피드백 | 항목마다 `evidence_summary`와 `improvements[]` 목록. 도메인 항목은 `missing_or_uncertain[]`도 칩으로 표시 |
| 6 | 핵심 요약 | `top_3_failure_reasons[]`, `priority_actions[]` |
| 7 | 개선안 JSON-LD | `aiResult.jsonld`가 있을 때만(= 원 주문). 들여쓰기 2칸으로 표시하고 `복사` 버튼을 둔다. §4.2 참고 |

`score_band` 라벨: `exceptional` 매우 우수, `strong` 우수, `good` 양호, `average` 보통, `weak` 미흡, `very_weak` 매우 미흡.

### 6.3 전후 비교 뷰

**입력**: `before` = 원 주문의 `getEvaluation(aiResult)`, `after` = 선택한 재평가 주문의 `getEvaluation(aiResult)`

`COMPLETED` 재평가가 2개 이상이면 뷰 상단에 `비교 대상: [재평가 2 · 09.27 ▾]` 셀렉트를 둔다. 기본값은 가장 최근의 `COMPLETED` 재평가다. 기준은 언제나 원 주문이다.

| 순서 | 블록 | 내용 |
| ---: | --- | --- |
| 1 | 점수 헤더 | 왼쪽 `적용 전 55`, 오른쪽 `적용 후 71.5`, 가운데 변화량 `+16.5`. 대표 점수 기준 |
| 2 | 상한 변화 | 전과 후의 `global_caps_applied`가 다를 때만. 예: `상한 해제: 구체적 사실이 적은 홍보 문구 (55점 상한)`, `상한 추가: 본문과 JSON-LD 불일치 (50점 상한)` |
| 3 | 항목 비교 표 | 아래 표 |
| 4 | 차트 | 레이더 1개에 두 데이터셋을 겹쳐 그린다. 적용 전은 회색 점선, 적용 후는 accent 실선 |
| 5 | 항목별 피드백 | 항목마다 접이식. 펼치면 왼쪽 `적용 전 improvements`, 오른쪽 `적용 후 improvements`를 나란히 |
| 6 | 핵심 요약 | `priority_actions`를 전·후 나란히 |
| 7 | 안내 문구 | 고정 문구: `AI 채점 특성상 같은 페이지도 점수가 소폭 달라질 수 있습니다. 항목별 변화와 피드백을 함께 확인해 주세요.` |

항목 비교 표:

| 항목 | 만점 | 적용 전 | 적용 후 | 변화 |
| --- | ---: | ---: | ---: | ---: |
| 주제·엔티티 명확성 | 15 | 11 | 13 | ▲ +2 |
| … | | | | |
| **합계 (raw)** | 100 | 58 | 71.5 | ▲ +13.5 |
| 상한 적용 점수 (total) | 100 | 55 | 71.5 | ▲ +16.5 |

- 행 순서는 `SCORE_ITEMS` 순서다.
- 합계 행은 항상 두 줄(raw, total)을 다 보여준다. 둘 중 `PRIMARY_SCORE`에 해당하는 줄을 굵게 표시한다.

피드백은 자동으로 비교하지 않는다. AI가 매번 문장을 새로 쓰기 때문에 같은 내용이어도 문자열이 달라서 "사라진 피드백"이나 "새 피드백"을 가려낼 수 없다. 전과 후를 나란히 보여주기만 한다.

## 7. 표시 규칙

### 7.1 숫자

- 점수는 소수점 첫째 자리까지 표시한다. `.0`은 생략한다(`55.0` → `55`).
- 변화량 = `after - before`. 소수점 첫째 자리에서 반올림한다.
- 변화량이 `> 0`이면 초록 `▲ +n`, `< 0`이면 빨강 `▼ -n`, `0`이면 회색 `– 0`.

### 7.2 대표 점수를 raw로 두는 이유

2026-09 게이트웨이 재현성 실험(이커머스 7개 사이트 × 3회)에서 같은 입력인데도 `total_score`가 최대 20점까지 흔들렸다. 세부 항목 합계(`raw_score`)는 상대적으로 안정적이었다. 흔들림의 원인은 상한(cap) 판정 단계다. 상한 판정이 안정화될 때까지 대표 점수는 `raw_score`로 두고, `total_score`는 보조로 보여준다. 전환은 `PRIMARY_SCORE` 상수 하나만 바꾸면 된다.

### 7.3 만점 초과 방어

같은 실험에서 항목 점수가 만점을 넘는 사례가 관측됐다(`cross_source_consistency` 10점 만점에 18~20점). 현재 AI 출력 스키마에 만점 제약이 선언돼 있지만, 실제로 강제되는지 확인되기 전까지 프론트는 표시 단계에서 방어한다.

```javascript
const shown = Math.min(Math.max(score, 0), item.max); // 표시·막대·변화량 계산 모두 shown 기준
```

### 7.4 null 처리

- `getEvaluation(aiResult)`가 `null`이면 해당 버전의 본문 블록 대신 상태 안내만 표시한다.
- `improvements`, `missing_or_uncertain`, `top_3_failure_reasons`, `priority_actions`가 빈 배열이면 `없음`을 회색으로 표시한다.
- 단, `limited: true`인 주문에서 `evidence_summary`가 `null`인 항목은 `없음` 대신 잠금 표시와 `개선안 포함 주문에서 확인할 수 있습니다.`를 보여주고 Pack 구매로 연결한다.

## 8. polling

| 대상 | 호출 | 간격 | 계속 | 중지 |
| --- | --- | --- | --- | --- |
| 재평가 주문 | `GET /report/{재평가 orderId}` | 3초 | `ACCEPTED`, `PROCESSING` | `COMPLETED`, `FAILED` |

- 페이지가 열려 있고 진행 중인 재평가가 있으면 polling한다. 새로고침했을 때는 `versions`에서 진행 중인 주문을 찾아 다시 시작한다. localStorage에 따로 저장하지 않는다.
- 10분이 지나도 끝나지 않으면 polling을 멈추고 `처리가 지연되고 있습니다. 잠시 후 새로고침해 주세요.`를 표시한다.
- 중지 상태가 되면 `GET /report/{원 주문 id}`를 다시 불러 탭과 남은 횟수를 갱신한다. `COMPLETED`이면 비교 뷰로 전환하고, `FAILED`이면 해당 탭을 `⚠`로 바꾼다.
- 대시보드는 polling하지 않는다. 진입할 때만 목록을 불러온다.

## 9. 추후 기능 — 이번에 구현하지 않음

**재평가 전 JSON-LD 반영 확인**: 재평가 요청이 들어오면 AI 채점 전에 페이지를 먼저 수집해, JSON-LD가 원 주문 때와 같은지 비교한다. 같으면 "아직 개선안이 반영되지 않은 것 같아요"라고 안내하고 재평가 횟수를 차감하지 않는다. 백엔드 `OrderService`에 TODO로만 남겨 두었다. 오류 코드와 화면 문구는 구현할 때 이 문서에 추가한다. 그 전까지 프론트는 §6.2.3 모달 문구로만 안내한다.

## 10. 오류 처리

분기는 `message`가 아닌 `code`로 한다.

| HTTP | code | 화면 처리 |
| ---: | --- | --- |
| 404 | `RESOURCE_NOT_FOUND` | `의뢰를 찾을 수 없습니다.` 후 대시보드로 이동 |
| 409 | `REEVAL_NOT_ALLOWED` | 모달을 닫고 `GET /report/{원 주문 id}` 재조회. 버튼은 §6.2.2 조건에 따라 다시 그린다 |
| 409 | `REEVAL_IN_PROGRESS` | 모달을 닫고 재조회한 뒤 진행 중인 탭을 polling |
| 409 | `REEVAL_LIMIT_EXCEEDED` | 모달을 닫고 §6.2.2의 4번 사유 표시 |
| 409 | `REEVAL_WINDOW_EXPIRED` | 모달을 닫고 §6.2.2의 5번 사유 표시 |
| 기타·네트워크 | — | `요청하지 못했습니다. 잠시 후 다시 시도해 주세요.` 재조회로 접수 여부부터 확인 |

재평가 `FAILED` 탭의 안내 카드 문구: `재평가 중 사이트를 수집하지 못했습니다. 사이트가 열리는지 확인한 뒤 다시 요청해 주세요.`

## 11. 인수 기준

- [ ] 대시보드에서 재평가 주문이 별도 행으로 나오지 않고 원 주문 1건 = 1행이다.
- [ ] 완료된 재평가가 있는 행에 `전 → 후 (±변화)`와 `재평가 N회`가 표시된다.
- [ ] 결과 상세의 KPI·차트가 `SCORE_ITEMS` 6개 항목(만점 합 100)으로 표시된다.
- [ ] `getEvaluation()`으로 `/diagnose`(원 주문)와 `/evaluate`(재평가) 결과를 모두 렌더링한다.
- [ ] 재평가 버튼은 §6.2.2 조건을 모두 만족할 때만 활성화되고, 비활성 사유가 표시된다.
- [ ] FREE 주문(`reevalRemaining: null`)에는 재평가 버튼이 없다.
- [ ] 재평가 완료 후 새로고침 없이 비교 뷰로 전환되고 남은 횟수가 갱신된다.
- [ ] 재평가가 진행 중일 때 새로고침해도 진행 상태와 polling이 복원된다.
- [ ] 비교 표에 6개 항목, raw 합계, total이 모두 있고 대표 점수 줄이 굵게 표시된다.
- [ ] 상한이 바뀐 경우에만 상한 변화 블록이 보인다.
- [ ] 만점을 넘는 점수가 와도 막대가 100%를 넘지 않고 표시값이 만점이다.
- [ ] 개선안 JSON-LD 블록과 복사 버튼은 원 주문 탭에서만 보인다.

## 12. 구현 전 확정이 필요한 항목

| 항목 | 현재 처리 |
| --- | --- |
| 재평가 횟수·기한 | Pack은 `GET /pack-products`의 `reevalCountPerCredit`·`reevalWindowDays`(2회·30일). Partner는 미확정. 프론트는 서버 값만 쓰므로 화면 변경 없음 |
| 재평가 실패 시 횟수 복원 | 미확정 |
| 상한 판정 안정화 시점 | AI 게이트웨이 수정 후 `PRIMARY_SCORE`를 `total_score`로 전환 |
| `aiResult.jsonld` 성공 형식 | 백엔드 `/diagnose` 연동 시 확정 |
| 항목·상한 한국어 라벨 | §5.1, §5.2는 초안. 기획 검수 필요 |

## 부록. 백엔드 구현 메모

프론트 계약에는 영향이 없다.

- `Order`에 `baseline_order_id`(nullable) 컬럼 하나를 추가한다. 재평가 주문끼리 줄줄이 연결하지 않고 모두 원 주문을 가리키게 한다. 기존 Order–AnalysisJob–AnalysisReport 1:1 구조와 워커를 그대로 쓴다.
- `reevalRemaining`, `reevalExpiresAt`은 따로 저장하지 않는다. `reevalCountPerCredit - count(baseline_order_id = 원 주문)`과 원 주문 완료 시각 + `reevalWindowDays`로 계산한다.
- 재평가 주문은 저장된 `rawScrapedData`를 재사용하지 않고 라이브 페이지를 새로 수집한다. AI는 `/evaluate`로 호출한다(개선안을 다시 생성하지 않음).
- `score`(`{ raw, total }`)는 `rawAILog`의 `analysis` 또는 `result`에서 `raw_score`, `total_score`를 읽는다.
- 현재 전역 예외 처리기가 없다. §3.2의 오류 응답을 위해 재평가 검증 예외용 핸들러가 하나 필요하다.
