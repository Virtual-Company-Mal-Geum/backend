# GEO 상품·결제 프론트엔드 API 명세서

- 버전: 2.3
- 작성일: 2026-09-18
- 개정: 2026-09-24 — 별도 Remediation·Verification 리소스를 없애고 기존 분석 주문 API(`/geo/order`, `/geo/report`)로 통합. 로그인 회원의 무료 Scan도 같은 API로 통합
- 개정: 2026-09-25 — 비로그인 방문자 Scan 제거. 모든 분석 주문은 로그인·계정 인증 필수
- 변경 요약: [`GEO_credit_frontend_spec_changes_v2.3.md`](GEO_credit_frontend_spec_changes_v2.3.md) — v2.1 대비 달라진 API·필드·상태값·오류 코드와 프론트 작업 순서
- 대상: GEO 서비스 프론트엔드·백엔드 개발자
- Base URL: `/api/v1`
- 인증 방식: JWT Bearer
- 정책 원본: `PRICING_MODEL.md` 2026-09-16 개정본
- 지원 결제: Remediation Pack은 토스페이먼츠 일반 신용·체크카드 및 간편결제, Partner는 세금계산서 + 수동 계좌이체

## 1. 문서 범위와 구현 단계

이 문서는 프론트엔드가 호출할 API, 화면 상태, 결제수단별 요청값을 정의한다. PG Webhook, 대사 배치, 관리자 입금 확인, DB 원장·잠금 구조는 백엔드 내부 구현으로 보고 응답 계약만 다룬다.

### 1.1 출시 단계

| 단계 | 상품·기능 | 계약 상태 | 프로젝트 구현 |
| --- | --- | --- | --- |
| 1 | 무료 Scan | 확정 | `/geo/order`. 로그인 회원만 |
| 1 | Remediation Pack 구매·크레딧·작업·환불 | 확정 | 우선 구현 |
| 1 | 토스페이먼츠 일반 카드·간편결제 | 확정 | 우선 구현 |
| 2 | Partner 플랜·세금계산서·계좌이체 | 초안 | API 계획만 남기고 이번 프로젝트에서는 구현하지 않음 |
| 별도 | AI 검색 인용·언급 추적 agent | 미정 | 가격·API를 만들지 않음 |

Partner API는 프론트가 임의로 선구현하지 않는다. 이 문서의 `계획` 엔드포인트는 다음 개발 단계의 계약 초안이다.

### 1.2 제외 범위

- 카드 자동결제와 빌링키
- Partner 자동 갱신 결제
- AI 검색 노출·인용 추적
- Pack 일부 사용 후 잔여 크레딧 부분 환불
- Partner 계좌이체 환불 자동화
- Partner Plus 온라인 즉시 결제

## 2. 고객과 지원 도메인

### 2.1 타깃 우선순위

| 우선순위 | 대상 |
| --- | --- |
| 주 타깃 | 소형 학원, 대형 학원·프랜차이즈, 교육 전문 제작사·마케팅사·LMS/SI |
| 부 타깃 | 이커머스, 뉴스 |
| 제외 | 학습 데이터가 부족한 `TECH_BLOG` |

타깃 우선순위는 화면 기본값·영업 문구·온보딩 순서에만 영향을 준다. 동일 Pack에 도메인별 가격 차등이나 결과 품질 제한을 두지 않는다.

### 2.2 도메인 입력 계약

`domainType` 허용값:

| 값 | 의미 |
| --- | --- |
| `EDUCATION` | 학원·교육 서비스 |
| `ECOMMERCE` | 쇼핑몰·상품 판매 서비스 |
| `NEWS` | 언론·뉴스 서비스 |

`EDUCATION`이면 `educationProfile.segment`가 필수다.

| segment | 기본 `noiseFilterEnabled` | 설명 |
| --- | ---: | --- |
| `SMALL_ACADEMY` | `false` | 광고처럼 보이는 자체 홍보도 유효 콘텐츠로 취급 |
| `LARGE_ACADEMY` | `true` | 외부 광고·배너 노이즈를 기본 제외. 프랜차이즈 포함 |

사용자는 기본값을 바꿀 수 있다. 정확도를 좌우하는 노이즈 필터를 유료 상위 플랜 기능으로 제한하지 않는다.

```json
{
  "url": "https://academy.example.com/curriculum",
  "domainType": "EDUCATION",
  "educationProfile": {
    "segment": "SMALL_ACADEMY",
    "noiseFilterEnabled": false
  }
}
```

`ECOMMERCE` 또는 `NEWS`에서는 `educationProfile`을 생략하거나 `null`로 보낸다.

## 3. 상품·가격 정책

모든 표시 가격은 서버 상품 API를 사용한다. 프론트에 상품명·가격·크레딧 수량을 하드코딩하지 않는다.

### 3.0 사용자 구분

모든 분석 주문은 회원가입 후 로그인한 회원만 할 수 있다. 로그인 전 방문자용 Scan은 없다. 회원별 주문 목록과 스팸 주문 방지를 회원 단위로 관리하기 위해서다.

무료·유료는 **사람이 아니라 주문 단위**로 정해진다. 같은 회원도 무료 Scan과 개선안 포함 주문을 모두 할 수 있다. 이 문서에서 "회원"은 플랜(`FREE`·Partner)이나 Pack 크레딧 보유 여부와 관계없이 로그인한 사용자 전체를 뜻한다.

| 주문 구분 | 조건 | 비용 |
| --- | --- | --- |
| 무료 Scan | 개선안 없이 요청한 주문 | 0원. 월 한도 적용 |
| 개선안 포함 주문 | `withImprovement: true` | Pack 크레딧 1개 또는 Partner 한도 |
| 재평가 | `baselineOrderId` 지정 | 원 주문에 포함 |

분석 주문은 이메일 인증을 마친 계정 또는 Google 계정 로그인만 접수한다. 미인증 계정은 `403 ACCOUNT_NOT_VERIFIED`를 받는다(§14).

### 3.1 무료 Scan

| 항목 | 정책 |
| --- | --- |
| 가격 | 0원 |
| 한도 | 회원 월 5회 |
| 결과 | 점수, 상위 문제 3개, 그중 1개만 위치·근거 상세, 이미지 의존 경고 |
| 보관 | 30일 |

파트너 영업용 일괄 Scan은 일반 회원 한도와 다른 운영 기능이며 이 명세에 포함하지 않는다.

### 3.2 Remediation Pack

정책 가격은 VAT 별도다. 실제 결제 금액은 `공급가액 + VAT`다.

| productCode | 표시명 | 크레딧 | 공급가액 | VAT | 결제 합계 |
| --- | --- | ---: | ---: | ---: | ---: |
| `REMEDIATION_PACK_1` | Remediation Pack 1 | 1 | 14,900원 | 1,490원 | 16,390원 |
| `REMEDIATION_PACK_5` | Remediation Pack 5 | 5 | 49,000원 | 4,900원 | 53,900원 |
| `REMEDIATION_PACK_20` | Remediation Pack 20 | 20 | 149,000원 | 14,900원 | 163,900원 |

Pack 크레딧 1개로 한 페이지에 다음 작업을 제공한다.

1. 전체 문제 진단
2. 위치별 HTML·JSON-LD·텍스트 수정 diff
3. 구문·Schema·본문 일치 안전 검사
4. 개선안 적용 후 30일 안에 재평가 2회

- Pack 5·20의 미사용 크레딧은 지급일부터 12개월 유효하다.
- Pack 1 미사용 크레딧 만료일은 정책이 확정되지 않아 `expiresAt=null`을 허용한다.
- 재평가 2회는 새 Pack 크레딧을 차감하지 않는다.
- 동일 페이지의 새 개선안 포함 주문은 크레딧 1개를 다시 사용한다.
- Pack 구매액을 Partner 첫 달 요금에서 차감하는 정책은 적용 기간·중복 사용 규칙이 확정되기 전까지 프론트에서 계산하지 않는다.

### 3.3 Partner — 계획

| planCode | 월 공급가액 | VAT 포함 입금액 | 포함 학원 | 학원당 월 페이지 | 조건 |
| --- | ---: | ---: | ---: | ---: | --- |
| `FOUNDING_PARTNER` | 290,000원 | 319,000원 | 10곳 | 10페이지 | 첫 10개 파트너, 12개월 가격 고정, 작업 데이터 공유·사례 공개 동의 |
| `PARTNER` | 490,000원 | 539,000원 | 20곳 | 10페이지 | 추가 학원 1곳당 월 20,000원 + VAT |
| `PARTNER_PLUS` | 990,000원부터 | 견적 | 50곳 | 계약별 | 프랜차이즈 본사·대형 대행사, 온라인 즉시 결제 불가 |

Partner는 페이지 크레딧 묶음이 아니라 관리 학원 수 기준 월 계약이다. 결제수단은 카드 자동결제가 아닌 세금계산서 + 수동 계좌이체다.

## 4. 공통 API 규칙

### 4.1 인증

공개 API를 제외한 모든 요청에 다음 헤더를 보낸다.

```http
Authorization: Bearer <accessToken>
```

회원 ID는 JWT로 식별한다. body나 query에 `memberId`를 보내지 않는다. 다른 회원의 주문·작업·청구서를 조회하면 `404 RESOURCE_NOT_FOUND`를 반환한다.

### 4.2 멱등키

데이터를 생성하거나 상태를 변경하는 POST 요청에는 다음 헤더가 필요하다.

```http
Idempotency-Key: <UUID v4>
```

- 길이 16~128자의 영문·숫자·하이픈·밑줄만 허용한다.
- 한 번의 사용자 동작마다 새 키를 만든다.
- 네트워크 오류로 동일 요청을 재시도할 때는 같은 키와 같은 body를 사용한다.
- 같은 키로 body를 바꾸면 `409 IDEMPOTENCY_KEY_REUSED`를 반환한다.

| API | 키 생성 시점 |
| --- | --- |
| `POST /purchase-orders` | Pack 구매 클릭 |
| `POST /payments/confirm` | 토스 successUrl 진입 |
| `POST /geo/order` (`withImprovement: true`) | 개선안 포함 주문 제출 |
| `POST /payments/{id}/refunds` | Pack 환불 확인 |
| `POST /partner-applications` | Partner 신청 — 계획 |
| `POST /billing/invoices/{id}/deposit-reports` | 입금 알림 — 계획 |

`POST /geo/order`는 크레딧을 예약하는 개선안 포함 주문일 때만 멱등키가 필요하다. 개선안 없는 주문과 재평가 주문(`baselineOrderId`)은 크레딧을 쓰지 않아 멱등키 없이 보낸다. 재평가는 진행 중인 건이 있으면 서버가 `409 REEVAL_IN_PROGRESS`로 막기 때문에 중복 생성되지 않는다.

### 4.3 값 형식

| 값 | 형식 |
| --- | --- |
| 시간 | ISO 8601 UTC 문자열 |
| 금액 | KRW 원 단위 정수 |
| VAT | 공급가액의 10%, 서버 계산 |
| 수량 | 0 이상의 정수 |
| 페이지 크기 | 기본 20, 최대 100 |
| 목록 정렬 | 생성 시각 내림차순 |

금액 필드는 다음 구조를 공통 사용한다.

```json
{
  "supplyAmount": 49000,
  "vatAmount": 4900,
  "discountAmount": 0,
  "totalAmount": 53900,
  "currency": "KRW"
}
```

`totalAmount = supplyAmount + vatAmount - discountAmount`다. 프론트는 VAT나 할인액을 직접 다시 계산하지 않는다.

### 4.4 목록 응답

```json
{
  "items": [],
  "nextCursor": null,
  "hasNext": false
}
```

### 4.5 오류 응답

```json
{
  "code": "INSUFFICIENT_PACK_CREDITS",
  "message": "사용 가능한 Pack 크레딧이 부족합니다.",
  "retryable": false,
  "resourceId": null,
  "traceId": "trace_4ca2"
}
```

프론트 분기는 `message`가 아닌 `code`를 사용한다.

## 5. 상태값

### 5.1 Pack 주문·결제

| 상태 | 화면 처리 |
| --- | --- |
| `READY` | 결제창 진행 가능 |
| `CONFIRMING` | 승인 처리 중, 주문 조회 |
| `UNKNOWN` | 승인 결과 불명, 새 결제 금지 후 조회 |
| `SUCCEEDED` | 결제·크레딧 지급 완료 |
| `PARTIALLY_REFUNDED` | 자동 처리 중단, 문의 안내 |
| `REFUNDED` | 전액 환불 완료 |
| `FAILED` | 결제 실패 |
| `EXPIRED` | 주문 또는 승인 가능 시간 만료 |

### 5.2 분석 주문

기존 분석 주문의 `jobStatus`를 그대로 쓴다.

| jobStatus | 화면 처리 |
| --- | --- |
| `ACCEPTED` | 대기. 개선안 포함 주문이면 Pack 크레딧 1개 예약 |
| `PROCESSING` | 수집·진단·개선안 생성 중 |
| `COMPLETED` | 결과 표시 |
| `FAILED` | 실패 안내. 개선안 포함 주문이면 크레딧 반환 여부 표시 |

개선안 포함 주문에만 `creditStatus`가 있고, 그 외 주문은 `null`이다.

| creditStatus | 의미 |
| --- | --- |
| `RESERVED` | 주문에 크레딧 1개 예약 |
| `CONSUMED` | 주문 성공으로 사용 확정 |
| `RELEASED` | 실패로 반환 |

### 5.3 재평가

재평가도 분석 주문이므로 §5.2의 `jobStatus`를 그대로 쓴다. 크레딧을 쓰지 않으므로 `creditStatus`는 `null`이다. 기한 만료는 상태값이 아니라 요청 시 `409 REEVAL_WINDOW_EXPIRED`로 알린다.

### 5.4 Pack 환불

`REQUESTED`, `PROCESSING`, `UNKNOWN`, `SUCCEEDED`, `FAILED`를 사용한다.

### 5.5 Partner 신청·청구 — 계획

| applicationStatus | 의미 |
| --- | --- |
| `REQUESTED` | 신청 접수 |
| `REVIEWING` | 사업자·플랜 조건 확인 |
| `APPROVED` | 신청 승인, 청구서 생성 가능 |
| `REJECTED` | 신청 반려 |
| `CANCELED` | 신청 취소 |

| invoiceStatus | 의미 |
| --- | --- |
| `DRAFT` | 운영 검토 중 |
| `AWAITING_DEPOSIT` | 입금 계좌와 금액 안내 완료 |
| `DEPOSIT_REPORTED` | 고객이 입금을 알렸으나 확인 전 |
| `PAID` | 운영자 또는 은행 대사로 입금 확인 |
| `OVERDUE` | 납기 경과 |
| `CANCELED` | 청구 취소 |

입금 알림만으로 `PAID`나 Partner 권한을 부여하지 않는다.

## 6. 엔드포인트 요약

| 단계 | 메서드 | 경로 | 인증 | 용도 |
| --- | --- | --- | --- | --- |
| 구현 | GET | `/pack-products` | 공개 | 판매 중 Pack 조회 |
| 구현 | POST | `/purchase-orders` | 회원 | Pack 구매 주문 생성 |
| 구현 | GET | `/purchase-orders/{orderId}` | 소유자 | 주문·결제 상태 조회 |
| 구현 | GET | `/purchase-orders` | 회원 | Pack 구매 내역 |
| 구현 | POST | `/payments/confirm` | 소유자 | 토스페이먼츠 카드·간편결제 승인 |
| 구현 | GET | `/pack-credits/balance` | 회원 | Pack 크레딧 잔액 |
| 구현 | GET | `/pack-credits/batches` | 회원 | 구매별 잔여량·만료일 |
| 구현 | GET | `/pack-credits/ledger` | 회원 | 크레딧 변경 이력 |
| 기존 확장 | POST | `/geo/order` | 회원 | 분석 주문. 기본은 무료 Scan, `withImprovement`로 개선안 포함, `baselineOrderId`로 재평가 |
| 기존 확장 | GET | `/geo/reports` | 회원 | 분석 주문 목록 (재평가 주문 제외) |
| 기존 확장 | GET | `/geo/report/{orderId}` | 소유자 | 주문 상태·결과·크레딧·재평가 정보 조회 |
| 구현 | GET | `/payments/{paymentId}/refund-eligibility` | 소유자 | Pack 전액 환불 가능 확인 |
| 구현 | POST | `/payments/{paymentId}/refunds` | 소유자 | 미사용 Pack 전액 환불 |
| 구현 | GET | `/refunds/{refundId}` | 소유자 | 환불 상태 조회 |
| 계획 | GET | `/partner-plans` | 공개 | Partner 플랜 조회 |
| 계획 | POST | `/partner-applications` | 회원 | Partner 계약 신청 |
| 계획 | GET | `/partner-applications/{applicationId}` | 소유자 | 신청·청구 연결 상태 |
| 계획 | GET | `/partner-subscriptions/{subscriptionId}` | 소유자 | Partner 권한·한도 조회 |
| 계획 | POST | `/partner-subscriptions/{subscriptionId}/cancel` | 소유자 | 다음 주기 해지 요청 |
| 계획 | GET | `/billing/invoices` | 회원 | 청구 내역 |
| 계획 | GET | `/billing/invoices/{invoiceId}` | 소유자 | 입금 계좌·금액·세금계산서 상태 |
| 계획 | POST | `/billing/invoices/{invoiceId}/deposit-reports` | 소유자 | 계좌이체 완료 알림 |

## 7. 무료 Scan

무료 Scan은 개선안 없는 분석 주문이다. 유료 주문과 같은 `/geo/order`·`/geo/report`를 쓰고, 서버가 결과를 제한해서 내려준다. 무료 Scan은 Pack 크레딧을 생성·예약·차감하지 않는다.

### 7.1 POST `/geo/order`

`withImprovement`를 생략하거나 `false`로 보낸다. 멱등키는 보내지 않는다.

```http
POST /api/v1/geo/order
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "targetUrl": "https://academy.example.com",
  "siteName": "예시학원",
  "serviceType": "교육"
}
```

접수 `200 OK`:

```json
{
  "message": "GEO 분석 요청이 접수되었습니다.",
  "orderId": 131,
  "creditStatus": null,
  "availablePackCredits": null
}
```

- 이번 달 무료 한도(§3.1)를 넘으면 `429 FREE_SCAN_LIMIT_EXCEEDED`를 반환하고 주문을 만들지 않는다.
- 결과는 `GET /geo/report/{orderId}`로 조회한다. polling 규칙은 §15의 분석 주문과 같다.
- 무료 주문 결과도 `GET /geo/reports` 목록에 나온다. 재평가 버튼은 없다(`reevalRemaining: null`).

### 7.2 무료 결과 제한

`withImprovement: false` 주문을 `GET /geo/report/{orderId}`로 조회하면 `limited: true`가 오고, `aiResult`는 서버에서 잘린 상태로 온다. **제한은 서버가 응답에서 값을 지우는 방식이다.** 프론트가 받은 값을 숨기는 방식으로 구현하지 않는다.

| `aiResult.result` 필드 | 무료 결과 |
| --- | --- |
| 항목별 `score`, `raw_score`, `total_score`, `score_band`, `on_page_geo_readiness` | 모두 제공 |
| `global_caps_applied` | 모두 제공 |
| `top_3_failure_reasons` | 모두 제공 — 상위 문제 3개 |
| 항목별 `evidence_summary`, `improvements` | **상세 항목 1개만** 제공. 나머지 항목은 `evidence_summary: null`, `improvements: []` |
| `domain_specific_completeness.missing_or_uncertain` | 도메인 항목이 상세 항목일 때만 제공. 아니면 `[]` |
| `priority_actions` | `[]` |
| `aiResult.content_warning` | 제공 — 이미지 의존·본문 부족 경고 |

상세 항목은 `score / 만점` 비율이 가장 낮은 항목 1개다. 서버가 고르고, `limitedDetailKey`로 알려준다.

```json
{
  "orderId": 131,
  "jobStatus": "COMPLETED",
  "withImprovement": false,
  "creditStatus": null,
  "limited": true,
  "limitedDetailKey": "cross_source_consistency",
  "aiResult": {
    "status": "success",
    "result": {
      "common_evaluation": {
        "entity_topic_clarity":     { "score": 11.0, "evidence_summary": null, "improvements": [] },
        "cross_source_consistency": { "score": 3.0,  "evidence_summary": "학원명이 본문과 메타데이터에서 다르게 표기됨", "improvements": ["학원 정식 명칭을 한 가지로 통일"] }
      },
      "top_3_failure_reasons": ["...", "...", "..."],
      "priority_actions": []
    },
    "content_warning": null
  },
  "reevalRemaining": null
}
```

화면 처리:
- `evidence_summary`가 `null`인 항목은 피드백 자리에 잠금 표시와 `개선안 포함 주문에서 확인할 수 있습니다.`를 보여주고, Pack 구매(§8)로 연결한다.
- 점수·차트는 유료 결과와 같은 방식으로 그린다.

## 8. Pack 상품과 구매 주문

### GET `/pack-products`

```http
GET /api/v1/pack-products
```

성공 `200 OK`:

```json
{
  "items": [
    {
      "productCode": "REMEDIATION_PACK_1",
      "name": "Remediation Pack 1",
      "creditQuantity": 1,
      "supplyAmount": 14900,
      "vatAmount": 1490,
      "totalAmount": 16390,
      "currency": "KRW",
      "creditValidityMonths": null,
      "reevalCountPerCredit": 2,
      "reevalWindowDays": 30
    },
    {
      "productCode": "REMEDIATION_PACK_5",
      "name": "Remediation Pack 5",
      "creditQuantity": 5,
      "supplyAmount": 49000,
      "vatAmount": 4900,
      "totalAmount": 53900,
      "currency": "KRW",
      "creditValidityMonths": 12,
      "reevalCountPerCredit": 2,
      "reevalWindowDays": 30
    },
    {
      "productCode": "REMEDIATION_PACK_20",
      "name": "Remediation Pack 20",
      "creditQuantity": 20,
      "supplyAmount": 149000,
      "vatAmount": 14900,
      "totalAmount": 163900,
      "currency": "KRW",
      "creditValidityMonths": 12,
      "reevalCountPerCredit": 2,
      "reevalWindowDays": 30
    }
  ]
}
```

### POST `/purchase-orders`

Pack 결제 전 서버 주문을 생성한다.

```http
POST /api/v1/purchase-orders
Authorization: Bearer <accessToken>
Idempotency-Key: b30fb259-46f0-4a10-8d06-f14a73fe3a67
Content-Type: application/json

{
  "productCode": "REMEDIATION_PACK_5",
  "paymentMethod": "CARD"
}
```

`paymentMethod` 허용값:

| 값 | provider | 허용 상품 |
| --- | --- | --- |
| `CARD` | `TOSS` | Remediation Pack 일반 신용·체크카드 |
| `EASY_PAY` | `TOSS` | Remediation Pack 간편결제 |
| `BANK_TRANSFER` | `MANUAL_BANK` | Partner 청구서만 허용. 이 API에서는 거부 |

토스 주문 성공 `201 Created`:

```json
{
  "orderId": "po_67b1e630-8460-4c45-b99b-5b238a136cde",
  "orderName": "Remediation Pack 5",
  "productCode": "REMEDIATION_PACK_5",
  "creditQuantity": 5,
  "supplyAmount": 49000,
  "vatAmount": 4900,
  "discountAmount": 0,
  "totalAmount": 53900,
  "currency": "KRW",
  "paymentMethod": "CARD",
  "paymentProvider": "TOSS",
  "checkout": {
    "type": "TOSS_WIDGET",
    "customerKey": "5b7d49ac-608f-4d6a-b245-d8a3b6f97c32"
  },
  "paymentMode": "TEST",
  "expiresAt": "2026-09-14T06:30:00Z",
  "policyVersion": "pricing-2026-09-16"
}
```

`CARD`와 `EASY_PAY` 모두 `paymentProvider=TOSS`, `checkout.type=TOSS_WIDGET`을 사용한다. `CARD`는 일반 신용카드와 체크카드를 함께 포함하며 프론트가 카드 종류별 API를 분리하지 않는다. 프론트는 `paymentMethod`를 위젯의 초기 결제수단에 반영하고, 결제수단을 바꾸려면 기존 주문을 재사용하지 않고 새 주문을 만든다.

### GET `/purchase-orders/{orderId}`

```http
GET /api/v1/purchase-orders/po_67b1e630-8460-4c45-b99b-5b238a136cde
Authorization: Bearer <accessToken>
```

성공 `200 OK`:

```json
{
  "orderId": "po_67b1e630-8460-4c45-b99b-5b238a136cde",
  "productCode": "REMEDIATION_PACK_5",
  "creditQuantity": 5,
  "supplyAmount": 49000,
  "vatAmount": 4900,
  "discountAmount": 0,
  "totalAmount": 53900,
  "currency": "KRW",
  "paymentMethod": "CARD",
  "paymentProvider": "TOSS",
  "paymentId": "pay_1d8ef273",
  "paymentStatus": "SUCCEEDED",
  "creditGranted": true,
  "refundedAmount": 0,
  "reviewRequired": false,
  "expiresAt": "2026-09-14T06:30:00Z",
  "createdAt": "2026-09-14T06:00:00Z",
  "updatedAt": "2026-09-14T06:02:03Z"
}
```

### GET `/purchase-orders`

```http
GET /api/v1/purchase-orders?size=20&cursor=<nextCursor>
Authorization: Bearer <accessToken>
```

목록 각 항목은 주문 상세의 요약 필드와 `createdAt`을 반환한다.

## 9. Pack 결제수단 연동

### 9.1 프론트 공개 설정

| 값 | 용도 |
| --- | --- |
| `TOSS_CLIENT_KEY` | 토스 SDK 초기화 |
| `TOSS_SUCCESS_URL` | 토스 인증 성공 화면 |
| `TOSS_FAIL_URL` | 토스 실패·취소 화면 |

토스 시크릿 키는 프론트에 포함하지 않는다.

### 9.2 토스페이먼츠 카드·간편결제

토스페이먼츠 한 곳에서 `CARD`와 `EASY_PAY`를 처리한다. `CARD`는 일반 신용카드·체크카드를 포함하며, `EASY_PAY`는 토스 결제위젯에서 제공하는 간편결제다. 별도 간편결제사 직접 연동 API는 두지 않는다.

SDK에는 서버 주문의 `orderId`, `orderName`, `totalAmount`, `currency`, `checkout.customerKey`를 전달한다. 어떤 수단을 선택하더라도 결제 인증 성공 후 다음 승인 API를 호출한다.

#### POST `/payments/confirm`

```http
POST /api/v1/payments/confirm
Authorization: Bearer <accessToken>
Idempotency-Key: 7cf3a985-10a1-4e19-872a-0e7f236acde0
Content-Type: application/json

{
  "orderId": "po_67b1e630-8460-4c45-b99b-5b238a136cde",
  "paymentKey": "<successUrl의 paymentKey>",
  "amount": 53900
}
```

`amount`는 공급가액 49,000원이 아니라 VAT 포함 `totalAmount=53,900`원이다.

### 9.3 공통 승인 응답

완료 `200 OK`:

```json
{
  "orderId": "po_67b1e630-8460-4c45-b99b-5b238a136cde",
  "paymentId": "pay_1d8ef273",
  "paymentMethod": "CARD",
  "paymentStatus": "SUCCEEDED",
  "creditGranted": true,
  "grantedQuantity": 5,
  "availablePackCredits": 5
}
```

확인 중 `202 Accepted`:

```json
{
  "orderId": "po_67b1e630-8460-4c45-b99b-5b238a136cde",
  "paymentStatus": "UNKNOWN",
  "creditGranted": false,
  "pollUrl": "/api/v1/purchase-orders/po_67b1e630-8460-4c45-b99b-5b238a136cde",
  "retryAfterSeconds": 3
}
```

`SUCCEEDED + creditGranted=true`가 모두 확인된 뒤 구매 완료를 표시한다. `CONFIRMING` 또는 `UNKNOWN`에서는 결제창을 다시 열지 않는다.

## 10. Pack 크레딧

### GET `/pack-credits/balance`

```json
{
  "available": 4,
  "reserved": 1,
  "refundHeld": 0,
  "nextExpiryAt": "2027-09-14T06:02:03Z",
  "restricted": false
}
```

### GET `/pack-credits/batches`

구매별 잔여 크레딧과 만료일을 반환한다.

```json
{
  "items": [
    {
      "batchId": "cb_7a1431d0",
      "orderId": "po_67b1e630-8460-4c45-b99b-5b238a136cde",
      "productCode": "REMEDIATION_PACK_5",
      "granted": 5,
      "available": 4,
      "reserved": 1,
      "consumed": 0,
      "expiresAt": "2027-09-14T06:02:03Z"
    }
  ],
  "nextCursor": null,
  "hasNext": false
}
```

### GET `/pack-credits/ledger`

원장 항목은 `eventId`, `type`, `quantity`, `batchId`, 연관 `orderId`(Pack 구매 주문)·`analysisOrderId`(분석 주문)·`refundId`, `reasonCode`, `createdAt`을 반환한다.

## 11. 개선안 포함 주문과 재평가

별도 작업(Remediation) 리소스를 두지 않는다. 유료 개선안도 기존 분석 주문 API로 요청하고, 주문할 때 개선안 포함 여부를 정한다. 재평가 요청과 목록·상세 조회, 전후 비교 화면은 [`GEO_verification_compare_frontend_spec.md`](GEO_verification_compare_frontend_spec.md)에서 정의한다. 이 절은 크레딧에 관련된 부분만 다룬다.

### POST `/geo/order` — 개선안 포함 주문

기존 주문 요청에 `withImprovement`를 추가한다. `true`이면 서버가 Pack 크레딧 1개를 예약하고 AI 서버에 채점과 개선안(JSON-LD)을 함께 요청한다.

```http
POST /api/v1/geo/order
Authorization: Bearer <accessToken>
Idempotency-Key: 54e53bb1-f044-446e-a493-8551434e71ad
Content-Type: application/json

{
  "targetUrl": "https://academy.example.com/curriculum",
  "siteName": "예시학원",
  "serviceType": "교육",
  "withImprovement": true
}
```

| 필드 | 설명 |
| --- | --- |
| `withImprovement` | 생략하거나 `false`이면 무료 Scan(§7.1)이다. 크레딧 예약도 멱등키도 없다 |

접수 `200 OK` — 기존 응답에 크레딧 필드를 추가한다.

```json
{
  "message": "GEO 분석 요청이 접수되었습니다.",
  "orderId": 128,
  "creditStatus": "RESERVED",
  "availablePackCredits": 3
}
```

- `withImprovement`가 `false`이면 `creditStatus`와 `availablePackCredits`는 `null`이다.
- 사용 가능한 크레딧이 없으면 `409 INSUFFICIENT_PACK_CREDITS`를 반환하고 주문을 만들지 않는다.
- 주문이 `COMPLETED`가 되면 `CONSUMED`, `FAILED`가 되면 `RELEASED`로 바뀐다.

### GET `/geo/report/{orderId}` — 크레딧 관련 필드

기존 응답에 다음을 추가한다. 재평가 관련 필드(`versions`, `reevalRemaining` 등)는 비교 명세서 §3.4를 따른다.

```json
{
  "orderId": 128,
  "jobStatus": "COMPLETED",
  "withImprovement": true,
  "creditStatus": "CONSUMED",
  "limited": false,
  "limitedDetailKey": null
}
```

무료 주문(`withImprovement: false`)은 `limited: true`와 잘린 `aiResult`가 온다(§7.2).

개선안 포함 주문의 `aiResult`에는 채점 결과(`analysis`)와 개선안 JSON-LD(`jsonld`)가 함께 들어 있다. 형식은 비교 명세서 §4를 따른다.

### 재평가

`POST /geo/order`에 `{ "baselineOrderId": <원 주문 id> }`만 보낸다. 크레딧을 차감하지 않는다. 자세한 규칙은 비교 명세서 §3.2를 따른다.

- 원 주문이 `withImprovement: true`이고 `COMPLETED`인 경우에만 가능하다.
- 원 주문 1건당 재평가 횟수와 기한은 `GET /pack-products`의 `reevalCountPerCredit`, `reevalWindowDays`와 같다. 기한은 원 주문 완료 시각부터 센다.

## 12. Pack 전액 환불

토스페이먼츠 카드·간편결제 Pack 구매 중 해당 결제로 받은 크레딧을 하나도 예약·사용·만료하지 않은 경우만 원 결제수단으로 전액 환불한다. 일부 사용 묶음의 잔여 크레딧 부분 환불은 제공하지 않는다.

Partner 계좌이체의 해지·과오납 반환은 이 API를 사용하지 않고 운영 정산으로 처리한다.

### GET `/payments/{paymentId}/refund-eligibility`

```json
{
  "paymentId": "pay_1d8ef273",
  "eligible": true,
  "refundAmount": 53900,
  "currency": "KRW",
  "creditQuantity": 5,
  "usedQuantity": 0,
  "reservedQuantity": 0,
  "expiredQuantity": 0,
  "reasonCode": null
}
```

### POST `/payments/{paymentId}/refunds`

프론트는 환불 금액·수량을 결정하지 않는다.

```http
POST /api/v1/payments/pay_1d8ef273/refunds
Authorization: Bearer <accessToken>
Idempotency-Key: a68040a5-06ae-4ef1-a823-caf865cb8374
Content-Type: application/json

{
  "reason": "미사용 Pack 환불"
}
```

### GET `/refunds/{refundId}`

`REQUESTED`, `PROCESSING`, `UNKNOWN`에서 조회하고 `SUCCEEDED`, `FAILED`에서 중지한다.

## 13. Partner·세금계산서·계좌이체 — 계획

### 13.1 결제 방식 정의

이 문서의 `BANK_TRANSFER`는 PG 실시간 계좌이체나 가상계좌가 아니다. Partner와 월 계약을 맺고 서버가 안내한 회사 계좌로 고객이 직접 송금하며, 운영자 또는 은행 거래 대사가 입금을 확인하는 B2B 수동 계좌이체다.

프론트의 `입금 완료 알림`은 확인 요청일 뿐 결제 성공 처리가 아니다.

### GET `/partner-plans`

```json
{
  "items": [
    {
      "planCode": "FOUNDING_PARTNER",
      "name": "Founding Partner",
      "supplyAmount": 290000,
      "vatAmount": 29000,
      "totalAmount": 319000,
      "includedAcademies": 10,
      "pagesPerAcademyPerMonth": 10,
      "foundingSlotsRemaining": 7,
      "caseStudyConsentRequired": true,
      "onlineCheckoutAvailable": false
    },
    {
      "planCode": "PARTNER",
      "name": "Partner",
      "supplyAmount": 490000,
      "vatAmount": 49000,
      "totalAmount": 539000,
      "includedAcademies": 20,
      "pagesPerAcademyPerMonth": 10,
      "additionalAcademySupplyAmount": 20000,
      "onlineCheckoutAvailable": false
    },
    {
      "planCode": "PARTNER_PLUS",
      "name": "Partner Plus",
      "startingSupplyAmount": 990000,
      "includedAcademies": 50,
      "quoteRequired": true,
      "onlineCheckoutAvailable": false
    }
  ]
}
```

### POST `/partner-applications`

```http
POST /api/v1/partner-applications
Authorization: Bearer <accessToken>
Idempotency-Key: e364526e-34db-4797-a193-4d2c71d07f6c
Content-Type: application/json

{
  "planCode": "PARTNER",
  "managedAcademyCount": 20,
  "companyName": "말금에듀마케팅",
  "businessRegistrationNumber": "123-45-67890",
  "representativeName": "홍길동",
  "billingEmail": "billing@example.com",
  "taxInvoiceEmail": "tax@example.com",
  "expectedDepositorName": "말금에듀마케팅",
  "caseStudyConsent": false
}
```

사업자등록번호·대표자명은 결제 페이지 분석 로그나 일반 오류 로그에 남기지 않는다.

접수 `202 Accepted`:

```json
{
  "applicationId": "pa_39e6271a",
  "applicationStatus": "REQUESTED",
  "planCode": "PARTNER",
  "estimatedSupplyAmount": 490000,
  "estimatedVatAmount": 49000,
  "estimatedTotalAmount": 539000,
  "invoiceId": null,
  "createdAt": "2026-09-14T08:00:00Z"
}
```

Partner Plus와 기본 포함 수를 넘는 신청 금액은 `estimated`일 뿐 확정 금액이 아니다.

### GET `/partner-applications/{applicationId}`

승인되면 `invoiceId`와 확정 금액을 반환한다. 반려 시 사용자에게 공개 가능한 `reasonCode`만 반환한다.

### GET `/billing/invoices/{invoiceId}`

입금 안내 응답:

```json
{
  "invoiceId": "inv_b23438e1",
  "applicationId": "pa_39e6271a",
  "subscriptionId": null,
  "invoiceStatus": "AWAITING_DEPOSIT",
  "planCode": "PARTNER",
  "billingPeriod": {
    "startsAt": "2026-10-01T00:00:00Z",
    "endsAt": "2026-10-31T23:59:59Z"
  },
  "supplyAmount": 490000,
  "vatAmount": 49000,
  "discountAmount": 0,
  "totalAmount": 539000,
  "currency": "KRW",
  "discounts": [],
  "bankTransfer": {
    "bankName": "<서버 설정 은행명>",
    "accountNumber": "<서버 설정 입금 계좌>",
    "accountHolder": "<서버 설정 예금주>",
    "expectedDepositorName": "말금에듀마케팅",
    "dueAt": "2026-09-25T14:59:59Z"
  },
  "taxInvoiceStatus": "REQUESTED",
  "paidAt": null
}
```

- 계좌번호·예금주·입금액·납기일은 서버 응답을 그대로 표시한다.
- 실제 계좌를 프론트 환경변수나 코드에 하드코딩하지 않는다.
- Pack 첫 달 차감이 운영 승인된 경우 `discounts`에 `PACK_FIRST_MONTH_OFFSET` 항목과 금액이 반환된다.

### POST `/billing/invoices/{invoiceId}/deposit-reports`

```http
POST /api/v1/billing/invoices/inv_b23438e1/deposit-reports
Authorization: Bearer <accessToken>
Idempotency-Key: a7562354-a169-439b-b513-468f7597381f
Content-Type: application/json

{
  "depositorName": "말금에듀마케팅",
  "depositedAmount": 539000,
  "depositedAt": "2026-09-20T02:10:00Z"
}
```

성공 `202 Accepted`:

```json
{
  "depositReportId": "dr_51d8ec5c",
  "invoiceId": "inv_b23438e1",
  "invoiceStatus": "DEPOSIT_REPORTED",
  "subscriptionActivated": false,
  "message": "입금 확인 후 Partner 권한이 활성화됩니다."
}
```

고객이 보낸 금액·입금자명은 신고값일 뿐 신뢰하지 않는다. 운영자나 은행 거래내역이 청구서와 일치함을 확인한 뒤에만 `PAID`로 전이한다.

### GET `/billing/invoices`

`invoiceStatus`, `subscriptionId`, `cursor`, `size`로 필터링할 수 있다.

### GET `/partner-subscriptions/{subscriptionId}`

```json
{
  "subscriptionId": "sub_b7a831d2",
  "planCode": "PARTNER",
  "status": "ACTIVE",
  "paymentMethod": "BANK_TRANSFER",
  "includedAcademies": 20,
  "registeredAcademies": 13,
  "pagesPerAcademyPerMonth": 10,
  "currentPeriodStartsAt": "2026-10-01T00:00:00Z",
  "currentPeriodEndsAt": "2026-10-31T23:59:59Z",
  "nextInvoiceAt": "2026-10-25T00:00:00Z"
}
```

### POST `/partner-subscriptions/{subscriptionId}/cancel`

다음 결제 주기 해지를 요청한다. 즉시 환불·당월 금액 일할 계산은 정책 미확정이므로 지원하지 않는다.

## 14. 오류 코드와 화면 처리

| HTTP | code | 프론트 처리 |
| ---: | --- | --- |
| 400 | `INVALID_REQUEST` | 필드 오류 표시 |
| 400 | `IDEMPOTENCY_KEY_REQUIRED` | 요청 생성 로직 점검 |
| 400 | `EDUCATION_SEGMENT_REQUIRED` | 소형·대형 학원 선택 요청 |
| 401 | `UNAUTHORIZED` | 재로그인 후 리소스 ID로 상태 복원 |
| 403 | `ACCOUNT_RESTRICTED` | 문의 경로 표시 |
| 403 | `ACCOUNT_NOT_VERIFIED` | 이메일 인증 안내, 인증 메일 재발송 |
| 404 | `RESOURCE_NOT_FOUND` | 없는 항목 또는 접근 권한 없음 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 원 body 복구 또는 새 사용자 동작으로 처리 |
| 409 | `ORDER_EXPIRED` | 새 Pack 주문 생성 |
| 409 | `AMOUNT_MISMATCH` | 승인 중지, 주문 재조회 |
| 409 | `PAYMENT_PROVIDER_MISMATCH` | 주문의 결제수단으로 진행 |
| 409 | `PAYMENT_SESSION_EXPIRED` | 새 결제 세션 또는 주문 생성 |
| 409 | `INSUFFICIENT_PACK_CREDITS` | Pack 구매 화면 안내 |
| 409 | `REEVAL_NOT_ALLOWED` | 재평가 대상이 아님. 주문 상세 재조회 |
| 409 | `REEVAL_IN_PROGRESS` | 진행 중인 재평가 polling |
| 409 | `REEVAL_LIMIT_EXCEEDED` | 포함된 재평가 소진 안내 |
| 409 | `REEVAL_WINDOW_EXPIRED` | 재평가 기한 만료 안내 |
| 409 | `CREDITS_IN_USE` | 진행 중 작업 완료·취소 후 환불 안내 |
| 409 | `CREDITS_ALREADY_USED` | 자동 전액 환불 불가 안내 |
| 409 | `BANK_TRANSFER_NOT_ALLOWED_FOR_PACK` | 카드 또는 간편결제 선택 안내 |
| 409 | `DEPOSIT_ALREADY_REPORTED` | 청구서 상태 조회 |
| 422 | `PAYMENT_DECLINED` | 결제수단 확인 후 새 주문 안내 |
| 422 | `UNSUPPORTED_DOMAIN` | 교육·이커머스·뉴스만 표시 |
| 422 | `UNSUPPORTED_URL` | URL 수정 안내 |
| 429 | `FREE_SCAN_LIMIT_EXCEEDED` | 회원 전환 또는 다음 갱신일 안내 |
| 429 | `QUEUE_FULL` | 작업 미접수, 잔액 재조회 |
| 503 | `TEMPORARILY_UNAVAILABLE` | 기존 리소스 상태부터 조회 |

## 15. 프론트 상태 관리

```typescript
type PendingPackPurchase = {
  orderId: string;
  paymentMethod: "CARD" | "EASY_PAY";
  confirmIdempotencyKey?: string;
  createdAt: string;
};

type PendingBankTransfer = {
  applicationId: string;
  invoiceId?: string;
  depositReportId?: string;
};
```

- access token, PG Secret, 계좌 인증정보를 저장하지 않는다.
- 결제 승인·작업 접수·환불·입금 알림 요청 중에는 해당 버튼을 비활성화한다.
- 버튼 비활성화는 UX 보호이며 서버 멱등성이 중복 방지의 최종 기준이다.
- 결제 성공, 개선안 포함 주문 접수·실패, 환불 성공 뒤 Pack 잔액을 다시 조회한다.

Polling 종료 조건:

| 대상 | 계속 | 중지 |
| --- | --- | --- |
| Pack 결제 | `CONFIRMING`, `UNKNOWN` | `READY`, `SUCCEEDED`, `FAILED`, `REFUNDED`, `EXPIRED` |
| 분석 주문(개선안 포함·재평가) | `ACCEPTED`, `PROCESSING` | `COMPLETED`, `FAILED` |
| 환불 | `REQUESTED`, `PROCESSING`, `UNKNOWN` | `SUCCEEDED`, `FAILED` |
| Partner 신청 | `REQUESTED`, `REVIEWING` | `APPROVED`, `REJECTED`, `CANCELED` |
| 계좌이체 청구 | `DEPOSIT_REPORTED` | `AWAITING_DEPOSIT`, `PAID`, `OVERDUE`, `CANCELED` |

`READY`와 `AWAITING_DEPOSIT`은 실패 상태가 아니다. 서버 작업을 기다리는 자동 polling은 중지하고 사용자의 결제·입금을 기다린다.

## 16. 인수 기준

### 16.1 우선 구현

- 무료 Scan에 교육·이커머스·뉴스만 허용하고 `TECH_BLOG`를 노출하지 않는다.
- 무료 Scan은 `withImprovement` 없이 `POST /geo/order`로 접수하고, 결과 제한은 서버 응답(`limited: true`)으로만 처리한다.
- 교육 선택 시 소형·대형 학원 분류와 노이즈 필터 기본값을 적용한다.
- Pack 1·5·20의 VAT 별도 가격과 VAT 포함 결제 합계를 서버 응답으로 표시한다.
- `CARD`와 `EASY_PAY`만 Pack 구매에 허용하고, 두 방식 모두 `TOSS` provider로 처리한다.
- 결제 성공과 Pack 크레딧 지급이 모두 확인된 뒤 구매 완료를 표시한다.
- Pack 5·20 크레딧 만료일을 지급일 + 12개월로 표시한다.
- 개선안 포함 주문 1건에 Pack 크레딧 1개만 예약·사용한다.
- 완료된 개선안 포함 주문에 30일 내 재평가 2회를 제공하고 추가 크레딧을 차감하지 않는다.
- 미사용 Pack만 VAT 포함 결제 합계 전액을 원 결제수단으로 환불한다.
- message 문자열이 아닌 오류 code로 분기한다.

### 16.2 Partner 계획 계약

- Partner 가격은 공급가액과 VAT 포함 입금액을 구분한다.
- Partner Plus에는 즉시 결제 버튼 대신 견적 신청을 표시한다.
- 실제 입금 계좌를 서버 응답으로만 표시한다.
- 고객의 입금 알림만으로 청구서 `PAID` 또는 Partner `ACTIVE`를 만들지 않는다.
- 입금 확인 뒤에만 Partner 권한을 활성화한다.
- 계좌이체 환불을 Pack PG 환불 API로 처리하지 않는다.

## 17. 구현 전 확정이 필요한 항목

| 항목 | 현재 처리 |
| --- | --- |
| Pack 1 미사용 크레딧 만료 | 미확정, `null` 허용 |
| Pack 구매액의 Partner 첫 달 차감 | 적용 기간·대상 Pack·중복 차감 규칙 확정 필요 |
| 재평가 실패 시 횟수 복원 | 미확정 |
| 주문 도메인 입력 | §2.2의 `domainType`·`educationProfile`과 기존 `GeoOrderRequest.serviceType`을 하나로 합쳐야 함 |
| 개선안 diff·안전 검사 표시 | §3.2 제공 항목이지만 현재 AI 응답은 JSON-LD만 있음. `/diagnose` 연동 시 응답 계약 확정 |
| Pack·Partner 동시 보유 시 차감 순서 | Partner 한도를 먼저 쓸지 결정 필요 |
| 회원 월 한도 초기화 시각 | `Asia/Seoul` 달력 월 제안 |
| 이메일 인증 방식 | 가입 시 메일 링크 인증 제안. Google 계정은 인증된 것으로 간주 |
| 무료 결과 상세 항목 선정 | §7.2의 "비율이 가장 낮은 항목 1개"는 초안. 기획 확정 필요 |
| 토스페이먼츠 운영 키와 반환 URL | 환경별 설정 필요 |
| Partner 입금 계좌 | 법인·사업자 계좌 확정 필요 |
| 세금계산서 발행 시스템 | 직접 처리 또는 외부 서비스 결정 필요 |
| 입금 대사 | 관리자 수동 확인 또는 은행 거래 연동 결정 필요 |
| Partner 해지·미납·일할 계산 | 정책 확정 전 구현 금지 |
| Founding Partner 잔여 좌석의 동시성 | 승인 시점 기준과 예약 만료 규칙 필요 |
| 개인정보·사업자정보 보관 기간 | 약관·개인정보 정책 확정 필요 |

## 18. 참고

- `PRICING_MODEL.md`, 2026-09-16: 상품 구조, 타깃, 가격, 구현 범위, 결제·수납 정책
- [토스페이먼츠 결제위젯 연동](https://docs.tosspayments.com/guides/v2/payment-widget/integration)
- [토스페이먼츠 코어 API](https://docs.tosspayments.com/reference)

이 문서의 Partner API는 향후 구현 방향을 고정하기 위한 초안이다. AI 검색 추적은 비용 구조와 제품 가치 검증이 끝난 뒤 별도 명세로 분리한다.
