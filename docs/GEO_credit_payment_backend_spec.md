# GEO 상품·결제·Pack 사용 백엔드 명세서

- 버전: 2.1
- 작성일: 2026-09-15
- 개정: 2026-09-25 — Remediation·Verification·FreeEvaluation을 기존 분석 주문(`client_order`)으로 통합, 비로그인 방문자 Scan 제거, 가입 남용 방지 추가. Pack 결제를 토스페이먼츠 카드·간편결제로 정리(카카오페이 직접 연동 제거, 프론트 명세 기준)
- 대상: Mal-Geum GEO 서비스의 Spring Boot · Spring Security · JPA · PostgreSQL 백엔드
- 정책 기준: `PRICING_MODEL.md` 2026-09-14 개정본
- 프론트 계약: `GEO_credit_frontend_api_spec.md` v2.3, `GEO_verification_compare_frontend_spec.md` v2.0
- 문서 성격: 구현용 설계 명세. 엔티티명은 제안이며 실제 코드·ERD와 병합 전 매핑 검토가 필요하다.
- 이번 산출물: 무료 Scan, Remediation Pack 결제·사용·재평가·환불의 구현 계약과 Partner 세금계산서·계좌이체의 향후 계획

## 1. 결론과 구현 범위

이번 프로젝트의 핵심 구현은 다음 흐름이다.

> 무료 Scan → Remediation Pack 주문 → 토스페이먼츠 카드 또는 간편결제 승인 → Pack 크레딧 지급 → 개선안 포함 주문 예약·수행 → 30일 내 재평가 2회

모든 분석 주문은 로그인한 회원만 할 수 있다. 비로그인 방문자용 Scan은 제공하지 않는다. 회원별 주문 목록 조회와 스팸 주문 방지를 회원 단위로 처리하기 위해서다(§7.2).

Partner는 관리 학원 수 기준 월 계약이며 세금계산서 + 수동 계좌이체를 사용한다. 기존 결정대로 Partner와 AI 검색 추적은 설계만 남기고 이번 프로젝트에서 직접 구현하지 않는다.

### 1.1 확정·설계·미확정 사항

| 구분 | 내용 | 구현 판단 |
| --- | --- | --- |
| 확정 | 주 타깃은 소형·대형 학원과 교육 파트너 | 교육 입력을 소형·대형으로 구분 |
| 확정 | 부 타깃은 이커머스·뉴스 | 같은 Pack 가격과 처리 계약 사용 |
| 확정 | `TECH_BLOG` 제외 | API enum과 모델 라우팅에서 제거 |
| 확정 | 무료 Scan: 회원 월 5회, 결과 30일 보관. 비로그인 이용 없음 | 유료 원장과 분리 |
| 확정 | Pack 1/5/20 공급가액 14,900/49,000/149,000원, VAT 별도 | 결제는 16,390/53,900/163,900원 |
| 확정 | Pack 크레딧 1개 = 페이지 1개의 전체 문제·위치별 diff·안전 검사 | 성공 시 1개 사용 확정. MVP 성공 기준은 §8.3 |
| 확정 | 성공한 개선안 포함 주문에 재평가 2회 | 새 Pack 크레딧 차감 없음 |
| 확정 | Pack 5·20 크레딧 12개월 유효 | 지급 시 만료일 확정 |
| 확정 | Pack 결제는 토스페이먼츠 카드·간편결제(`CARD`·`EASY_PAY`) | 토스 한 곳, 승인 API 하나. 간편결제사 직접 연동 없음 |
| 확정 | Partner는 Founding 29만원/10곳, Partner 49만원/20곳, Plus 99만원부터/50곳 | 월 공급가액, VAT 별도 |
| 확정 | Partner 기본 결제는 세금계산서 + 수동 계좌이체 | PG Payment와 다른 Invoice 도메인 |
| 확정 | AI 검색 추적은 별도 agent·서비스 | 이번 API·테이블에서 제외 |
| 설계 선택 | 재평가 기한은 원 주문 완료 시각부터 30일 | 클라이언트가 보낸 값으로 기한을 정하지 않음 |
| 설계 선택 | 무료 Scan·개선안 포함 주문·재평가를 모두 기존 분석 주문으로 표현 | 별도 Remediation·Verification·FreeEvaluation 테이블 없음 |
| 설계 선택 | 이메일 인증 또는 Google OAuth 계정만 분석 주문 가능 | 스크립트로 계정을 여러 개 만드는 무료 한도 우회 방지 |
| 설계 선택 | 미사용 Pack만 VAT 포함 결제액 전액 자동 환불 | 일부 사용 묶음은 운영 문의 |
| 미확정 | Pack 1 미사용 크레딧 만료 | `expires_at=NULL` |
| 미확정 | Pack 구매액의 Partner 첫 달 차감 조건 | 자동 계산·원장 구현 금지 |
| 미확정 | 재평가 실패 시 횟수 복원 세부 기준 | `FAILED` 재평가는 횟수에 넣지 않는 기본안 사용 |

### 1.2 단계별 범위

| 단계 | 포함 | 제외 |
| --- | --- | --- |
| 프로젝트 핵심 | 상품, 주문, 토스 카드·간편결제 승인, Pack 원장, 개선안 포함 주문의 크레딧 예약·사용·반환 | Partner 테이블·API, AI 추적 |
| 실제 유료 공개 전 | PG Webhook·조회 대사, 장애 복구, 미사용 전액 환불, 운영 검토 | 부분 환불, 자동결제 |
| 향후 계획 | Partner 신청·청구서·입금 확인·학원별 한도 | 빌링키, PG 자동 갱신 |

외부 결제 성공과 내부 Pack 지급이 모두 확인돼야 구매가 완료된다. 정상 결제 한 번만 성공한 시연으로 실제 유료 공개 준비가 끝났다고 판단하지 않는다.

## 2. 용어와 경계

| 용어 | 의미 |
| --- | --- |
| 분석 주문 | 기존 `client_order`(`Order`) + `analysis_job` + `analysis_report`. 무료 Scan·개선안 포함 주문·재평가를 모두 표현 |
| PackProduct | 판매 중인 Remediation Pack 상품과 정책 버전 |
| PurchaseOrder | Pack 한 묶음의 구매 주문·가격 스냅샷 |
| Payment | 토스페이먼츠 승인 및 취소 상태 |
| PackCreditBatch | Payment 한 건으로 지급된 Pack 크레딧 묶음 |
| PackCreditWallet | 회원별 잠금·이용 제한 기준 행 |
| PackCreditReservation | 개선안 포함 주문 한 건에 예약한 크레딧 1개 |
| PackCreditLedger | 지급·예약·사용·반환·환불·만료 불변 이력 |
| Refund | 미사용 Pack 결제를 원 결제수단으로 전액 취소하는 작업 |
| PartnerApplication | Partner 계약 신청 — 계획 |
| BillingInvoice | Partner 월 공급가액·VAT·입금 계좌·납기 상태 — 계획 |
| DepositReport | 고객이 보낸 입금 완료 알림. 실제 입금 증거가 아님 — 계획 |
| PartnerSubscription | 입금 확인 뒤 활성화되는 학원 수 기준 월 권한 — 계획 |
| Reconciliation | PG·은행의 실제 거래와 내부 상태를 대조하는 복구 작업 |

별도 Remediation·Verification·FreeEvaluation 테이블을 만들지 않는다. 기존 분석 주문에 컬럼 두 개를 추가해 주문 종류를 구분한다.

| 주문 종류 | `with_improvement` | `baseline_order_id` | 크레딧 | AI 호출 |
| --- | --- | --- | --- | --- |
| 무료 Scan | `false` | `NULL` | 없음 | `/evaluate` |
| 개선안 포함 주문 | `true` | `NULL` | 1개 예약 | `/diagnose` |
| 재평가 | `false` | 원 주문 id | 없음 | `/evaluate` |

결제 주문(`PurchaseOrder`)과 분석 주문은 같은 엔티티로 합치지 않는다.

## 3. 타깃·도메인 계약

### 3.1 허용 도메인

```java
enum DomainType {
    EDUCATION,
    ECOMMERCE,
    NEWS
}

enum EducationSegment {
    SMALL_ACADEMY,
    LARGE_ACADEMY
}
```

- `EDUCATION`은 `educationSegment`가 필수다.
- `ECOMMERCE`, `NEWS`는 `educationSegment=NULL`이어야 한다.
- `TECH_BLOG` 요청은 `422 UNSUPPORTED_DOMAIN`이다.
- 타깃 우선순위는 가격·크레딧 지급량을 바꾸지 않는다.

### 3.2 교육 노이즈 필터

| 구분 | 기본값 | 이유 |
| --- | ---: | --- |
| `SMALL_ACADEMY` | `noiseFilterEnabled=false` | 자체 광고·홍보도 핵심 콘텐츠일 가능성이 큼 |
| `LARGE_ACADEMY` | `noiseFilterEnabled=true` | 외부 광고·배너가 평가를 왜곡할 가능성이 큼 |

클라이언트가 값을 생략하면 서버가 segment 기본값을 적용한다. 명시값은 허용하되 요청 스냅샷으로 저장한다. 정확도에 필요한 필터를 요금제 권한으로 제한하지 않는다.

DB에는 다음 CHECK를 권장한다.

```sql
CHECK (
  (domain_type = 'EDUCATION' AND education_segment IS NOT NULL)
  OR
  (domain_type IN ('ECOMMERCE', 'NEWS') AND education_segment IS NULL)
)
```

## 4. 상품·금액·세금 규칙

### 4.1 Remediation Pack

| productCode | creditQuantity | supplyAmount | vatAmount | totalAmount | validityMonths |
| --- | ---: | ---: | ---: | ---: | ---: |
| `REMEDIATION_PACK_1` | 1 | 14,900 | 1,490 | 16,390 | `NULL` — 미확정 |
| `REMEDIATION_PACK_5` | 5 | 49,000 | 4,900 | 53,900 | 12 |
| `REMEDIATION_PACK_20` | 20 | 149,000 | 14,900 | 163,900 | 12 |

1. 모든 금액은 원 단위 `long/BIGINT`, 통화는 `KRW`다. `float/double`을 사용하지 않는다.
2. Pack MVP의 `discountAmount`는 0이다.
3. `totalAmount = supplyAmount + vatAmount`를 상품 등록과 주문 생성 양쪽에서 검증한다.
4. 클라이언트는 `productCode`, `paymentMethod`만 선택한다. 가격·VAT·수량은 서버 상품에서 결정한다.
5. PurchaseOrder에 상품 코드·버전·표시명·수량·공급가액·VAT·총액·유효기간·정책 버전을 복사한다.
6. PG 승인 금액은 공급가액이 아니라 VAT 포함 `totalAmount`다.
7. PG 응답의 주문 ID·통화·총액·상점·환경을 주문 스냅샷과 다시 비교한다.
8. 상품 가격이 바뀌어도 이미 생성한 주문의 스냅샷을 변경하지 않는다.

할인·쿠폰을 추가할 때는 VAT 과세표준과 세금계산서 처리를 별도로 확정한다. Partner 첫 달 Pack 차감액을 지금의 `discountAmount`로 임의 구현하지 않는다.

### 4.2 결제수단 매핑

| paymentMethod | provider | 허용 대상 | 비고 |
| --- | --- | --- | --- |
| `CARD` | `TOSS` | Pack | 일반 신용·체크카드. 토스 결제위젯 |
| `EASY_PAY` | `TOSS` | Pack | 토스 결제위젯이 제공하는 간편결제(토스페이·카카오페이 등) |
| `BANK_TRANSFER` | `MANUAL_BANK` | Partner Invoice | PG 실시간 계좌이체·가상계좌가 아님 |

- `CARD`와 `EASY_PAY`는 같은 토스 결제위젯·같은 승인 API(`POST /payments/confirm`)를 쓴다. 간편결제사별 직접 연동이나 별도 준비·승인 API는 두지 않는다.
- 토스 결제위젯에는 카드와 간편결제만 노출한다.
- 승인 결과의 실제 method가 `카드` 또는 `간편결제`이면 주문의 `paymentMethod`와 달라도 금액 검증 후 지급하고, 실제 method를 Payment에 기록한다. 결제수단은 가격에 영향을 주지 않는다.
- 그 외 method(`가상계좌`, `계좌이체`, `휴대폰` 등)는 자동 지급하지 않고 검토 대상으로 둔다.
- Partner 수동 계좌이체를 토스 `계좌이체`나 `WAITING_FOR_DEPOSIT` 상태로 구현하지 않는다.

### 4.3 주문 식별자와 만료

- 공개 주문 ID는 6~64자의 무작위 문자열로 생성한다. 예: `po_<uuid>`.
- 한 PurchaseOrder에는 Pack 하나와 Payment 하나만 허용한다.
- 결제수단 변경은 기존 주문 수정이 아니라 새 주문 생성이다.
- 로컬 주문 시작 가능 시간은 기본 30분이다. PG 세션 유효시간과 별개로 기록한다.
- 로컬 주문이 만료됐어도 실제 승인 거래가 발견되면 폐기하지 않고 지급·취소·검토로 종결한다.
- 토스 `customerKey`는 회원별 무작위 고정값을 사용하고 회원 DB 순차 ID·이메일·전화번호를 그대로 쓰지 않는다.

## 5. Pack 결제 정상 흐름

```mermaid
sequenceDiagram
    participant F as 프론트
    participant B as Spring
    participant D as PostgreSQL
    participant P as 결제사
    F->>B: Pack 주문 생성
    B->>D: 주문·Payment READY
    B-->>F: 주문값·customerKey
    F->>P: 토스 결제위젯 인증(카드 또는 간편결제)
    F->>B: paymentKey·orderId·totalAmount
    B->>P: 서버 승인
    B->>D: SUCCEEDED·Batch·PURCHASE 원장
    B-->>F: 지급 완료 또는 확인 중
```

### 5.1 공통 승인 트랜잭션

PG 호출과 DB 커밋은 하나의 원자적 트랜잭션이 아니다. 승인 처리는 다음 경계로 나눈다.

| 단계 | DB 작업 | 외부 호출 |
| --- | --- | --- |
| T1 승인 준비 | 소유자·주문·결제수단·총액 확인, operation token·고정 멱등키 저장, `CONFIRMING` 커밋 | 없음 |
| PG 승인 | DB 트랜잭션 없음 | provider별 승인 API |
| T2 승인 반영 | Payment 잠금·응답 재검증, `SUCCEEDED` + Batch + `PURCHASE` + Outbox 원자 커밋 | 없음 |

- T1 전 종료: 지급 없음.
- T1 후 외부 호출 전 종료: 복구 worker가 실제 거래부터 조회한다.
- PG 승인 후 T2 전 종료: 조회 결과로 T2를 재실행한다.
- T2 후 응답 유실: 같은 주문 조회에서 기존 성공을 반환한다.
- `Payment.SUCCEEDED`와 Batch 지급을 서로 다른 커밋으로 나누지 않는다.

외부 호출 timeout·5xx·연결 종료는 확정 실패가 아니다. Payment를 `UNKNOWN`으로 두고 조회 대사를 예약한다. 사용자에게 새 주문을 즉시 만들도록 유도하지 않는다.

### 5.2 토스 카드·간편결제

1. 프론트가 주문의 `totalAmount`, `orderId`, `customerKey`로 토스 결제위젯을 연다. `paymentMethod`는 위젯의 초기 선택값으로만 쓴다.
2. `successUrl`의 `paymentKey`, `orderId`, `amount`를 `POST /payments/confirm`으로 전달한다.
3. 서버는 body 금액을 주문 총액과 비교한 뒤 토스 승인 API를 호출한다.
4. 응답의 `paymentKey`, `orderId`, `totalAmount`, `currency`를 재검증하고, 실제 method를 §4.2 규칙으로 판단한다.
5. `DONE`이 확인돼야 공통 T2를 실행한다.

토스 인증 뒤 승인 세션에는 시간 제한이 있으므로 successUrl 진입 직후 호출한다. 브라우저 failUrl·성공 URL 자체는 지급 근거가 아니다.

## 6. 상태 모델

### 6.1 PurchaseOrder와 Payment

`PurchaseOrder.lifecycle`은 `OPEN`, `CLOSED`, `EXPIRED`다. 결제 성공·환불 판단은 Payment가 기준이다.

| Payment 상태 | 의미 | 다음 전이 |
| --- | --- | --- |
| `READY` | 주문 생성, 승인 전 | `CONFIRMING`, `FAILED`, `EXPIRED` |
| `CONFIRMING` | 승인 작업 소유권 확보·외부 호출 중 | `SUCCEEDED`, `UNKNOWN`, `FAILED` |
| `UNKNOWN` | 승인 결과 미확정 | `SUCCEEDED`, `FAILED`, `EXPIRED`, 검토 |
| `SUCCEEDED` | 승인 검증과 Pack 지급 커밋 완료 | `PARTIALLY_REFUNDED`, `REFUNDED` |
| `PARTIALLY_REFUNDED` | 외부 부분 취소 등 정책 밖 상태 | 이용 제한·검토 |
| `REFUNDED` | 총액 전액 취소 반영 | 종료 |
| `FAILED` | provider가 확정한 승인 실패 | 종료 |
| `EXPIRED` | 신규 승인 불가 | 실제 거래 발견 시 복구 가능 |

`provider_status`, `review_required`, `provider_transaction_id`, 취소 거래는 별도로 저장한다. `UNKNOWN`과 `FAILED`를 합치지 않는다. `REFUNDED → SUCCEEDED`처럼 늦은 응답이 취소를 되돌리는 전이를 금지한다.

### 6.2 개선안 포함 주문과 크레딧 예약

상태는 기존 `AnalysisJobStatus`를 쓴다. 괄호 안은 프론트에 노출하는 값이다.

| AnalysisJob | Reservation | 처리 |
| --- | --- | --- |
| `PENDING` (`ACCEPTED`) | `RESERVED` | 주문 생성과 같은 트랜잭션에서 1개 예약 |
| `RUNNING`·`RETRY_WAIT` (`PROCESSING`) | `RESERVED` | 추가 차감 없음 |
| `SUCCEEDED` (`COMPLETED`) | `CONSUMED` | §8.3 성공 기준의 결과 저장과 동시에 사용 확정 |
| `FAILED` (`FAILED`) | `RELEASED` | 크레딧 반환 |

처리 전 취소 기능은 두지 않는다.

`RESERVED → CONSUMED` 또는 `RESERVED → RELEASED` 중 하나만 가능하다. 반환 후 도착한 늦은 성공 결과를 공개하거나 다시 차감하지 않는다.

### 6.3 재평가

재평가는 원 주문을 복사한 새 분석 주문이다. 상태는 §6.2와 같은 `AnalysisJobStatus`를 쓰고, 크레딧 예약은 없다. 재평가 권리를 slot 행으로 저장하지 않고 조회할 때 계산한다.

- 사용 횟수 = `baseline_order_id = 원 주문`이고 AnalysisJob이 `FAILED`가 아닌 주문 수.
- 사이트에 개선안이 적용되지 않은 결과도 유효한 재평가 결과이므로 횟수를 사용한다.
- `FAILED`(수집·AI 최종 실패)는 횟수에 넣지 않는다. 같은 원 주문에서 실패가 반복되면 운영 검토 대상으로 표시한다.
- 남은 횟수 = `pack_product.reeval_count` − 사용 횟수.
- 기한 = 원 주문 `analysis_job.completed_at` + `pack_product.reeval_window_days`. UTC로 계산한다.
- 한 원 주문에는 진행 중(`PENDING`·`RUNNING`·`RETRY_WAIT`) 재평가가 최대 1건이다.

### 6.4 Refund

| Refund 상태 | Batch 수량 | 처리 |
| --- | --- | --- |
| `REQUESTED` | A→H | 취소 Outbox 생성 |
| `PROCESSING` | H 유지 | provider 취소 호출 |
| `UNKNOWN` | H 유지 | 실제 취소 조회 |
| `SUCCEEDED` | H→F | 취소 거래·원장 확정 |
| `FAILED` | H→A | 취소 미실행이 확정된 경우만 반환 |

### 6.5 무료 Scan

별도 상태가 없다. 분석 주문의 `AnalysisJobStatus`를 쓴다. 결과 30일 보관 만료는 작업 실패가 아니라 `AnalysisReport.reportStatus = EXPIRED`다(기존 `expiredReport()`).

## 7. 무료 Scan

### 7.1 결과 범위

- 점수
- 우선순위 상위 문제 3개
- 그중 1개만 위치·근거 상세
- 이미지 의존 경고
- 결과 보관 30일

`GET /geo/report/{orderId}` 응답을 만들 때 서버가 제한 필드를 지운다(규칙은 프론트 명세 §7.2). 숨길 필드를 전체 응답에 넣고 프론트 CSS로 가리는 방식은 사용하지 않는다. 제한 여부는 요청값이 아니라 주문에 저장된 `with_improvement`로 판단한다.

무료 결과를 결제 후 그대로 전체 공개하지 않는다. Pack 크레딧으로 새 개선안 포함 주문을 만들어야 한다.

### 7.2 사용량 제한과 남용 방지

| 사용자 | 한도 | 서버 기준 |
| --- | --- | --- |
| 회원 | 무료 Scan 월 5회 | `Asia/Seoul` 달력 월 기준 제안 |
| 파트너 영업용 일괄 Scan | 별도 | 관리자·영업 권한 경로, 일반 API와 분리 |

- 한도 계산: `client_order`에서 `client_id = 본인`, `with_improvement = false`, `baseline_order_id IS NULL`, 이번 달 생성 건수. 재평가 주문은 무료 한도에 들어가지 않는다.
- 동시 요청으로 6번째가 통과하지 않도록 회원의 Wallet 행을 잠근 뒤 건수를 세고 주문을 저장한다.
- 형식 오류·지원하지 않는 도메인처럼 주문이 생성되지 않은 요청은 한도를 사용하지 않는다. 접수된 주문은 이후 실패해도 남용 방지를 위해 기본적으로 한도에 포함한다. 시스템 장애 보상 규칙은 운영 정책으로 분리한다.

**로그인 필수와 가입 남용 방지**

비로그인 방문자 경로를 만들지 않는다. 분석 주문 API는 Spring Security의 `anyRequest().authenticated()` 아래에 두고, 방문자 식별·조회 토큰·IP 기반 무료 한도는 구현하지 않는다.

가입 필수만으로는 스크립트로 계정을 여러 개 만들어 무료 한도를 우회하는 것을 막지 못한다. 다음을 함께 적용한다.

| 방어 | 내용 |
| --- | --- |
| 계정 인증 | 이메일 인증을 마친 계정 또는 Google OAuth 계정만 분석 주문을 접수한다. 미인증이면 `403 ACCOUNT_NOT_VERIFIED`. 현재 이메일 가입에는 인증 절차가 없으므로 새로 구현한다 |
| 인증 없는 경로 속도 제한 | 남는 공개 경로는 가입·로그인·상품 조회·Webhook이다. 가입·로그인에 IP 기준 속도 제한을 건다 |
| 회원별 한도 | 무료 Scan 월 5회. 개선안 포함 주문은 크레딧 수, 재평가는 원 주문당 횟수로 자연히 제한된다 |
| 전역 큐 상한 | 대기 작업이 상한을 넘으면 `429 QUEUE_FULL`, 주문과 크레딧 예약을 만들지 않는다 |

### 7.3 비동기 처리

무료 Scan도 다른 분석 주문과 같이 영속 `analysis_job` 큐로 처리한다(현재 구현). worker가 단일 처리 용량에 맞춰 상태를 전이한다. 프로세스 메모리의 `@Async`만으로 큐를 만들지 않는다.

## 8. Pack 크레딧과 개선안 포함 주문

### 8.1 Batch 불변식

각 PackCreditBatch에 다음 수량을 저장한다.

| 기호 | 필드 | 의미 |
| --- | --- | --- |
| Q | granted_quantity | 최초 지급 |
| A | available_quantity | 사용 가능 |
| R | reserved_quantity | 개선안 포함 주문 예약 |
| C | consumed_quantity | 성공 작업 사용 |
| H | refund_held_quantity | 환불 보류 |
| F | refunded_quantity | 환불 완료 |
| E | expired_quantity | 만료 |

항상 `Q = A + R + C + H + F + E`이며 모든 수량은 0 이상이다.

| 원장 eventType | 수량 변화 | eventKey |
| --- | --- | --- |
| `PURCHASE` | Q+q, A+q | `purchase:{paymentId}` |
| `RESERVE` | A-1, R+1 | `reserve:{orderId}` |
| `CONSUME` | R-1, C+1 | `consume:{reservationId}` |
| `RELEASE` | R-1, A+1 | `release:{reservationId}` |
| `REFUND_HOLD` | A-q, H+q | `refund-hold:{refundId}` |
| `REFUND` | H-q, F+q | `refund:{refundId}` |
| `REFUND_RELEASE` | H-q, A+q | `refund-release:{refundId}` |
| `EXPIRE` | A-q, E+q | `expire:{batchId}:{scheduleDate}` |

원장 행은 UPDATE·DELETE하지 않는다. 보정은 원인·운영자·연관 사건을 가진 새 이벤트로 기록한다.

### 8.2 Batch 선택과 만료

1. Wallet을 비관적 쓰기 잠금한다.
2. 차단되지 않고 `expires_at > now OR expires_at IS NULL`인 Batch만 본다.
3. 만료 시각이 빠른 Batch부터, NULL은 마지막으로 선택한다.
4. 동일 만료면 지급 시각과 ID 순으로 선택한다.
5. A가 1 이상이면 주문·AnalysisJob·Reservation·RESERVE를 같은 트랜잭션으로 저장한다.

Pack 5·20은 실제 지급 시각에서 `Asia/Seoul` 달력 기준 12개월 후를 계산해 UTC로 저장한다. 단순 365일과 혼용하지 않는다. Pack 1은 정책 확정 전 `NULL`이다.

만료 전에 예약된 R은 작업 종결까지 보호한다. 실패 반환 시 이미 만료 시각이 지났으면 RELEASE와 EXPIRE를 같은 트랜잭션에 기록한다.

### 8.3 개선안 포함 주문 성공 기준

MVP에서 크레딧 사용 확정은 다음이 모두 `analysis_report`에 저장된 시점이다.

1. AI `/diagnose` 응답의 채점 결과(`analysis`)
2. 개선안 JSON-LD(`jsonld`). `jsonld.valid == false`이면 성공이 아니다
3. AnalysisJob `SUCCEEDED`와 `completed_at`

다음 항목은 Pack 제공 범위(§1.1)에 포함된 방향이다. AI 응답에 추가되는 시점에 성공 기준에 넣는다.

- 위치 selector 또는 Schema path
- HTML·JSON-LD·텍스트 수정 diff
- 구문·Schema·본문 일치 안전 검사 결과

점수만 생성되거나 개선안 JSON이 잘린 경우 성공이 아니다. 변경이 필요 없으면 빈 결과 대신 `NO_CHANGE_NEEDED`와 검증 근거를 저장한다.

### 8.4 실패·재시도·알림

- `attempt_no`, `next_attempt_at`, `lease_until`, `execution_token`, AI `remote_job_id`를 영속화한다.
- 기본 시도 한도는 최초 포함 3회인 설계값이며 운영 측정 후 조정한다.
- AI 서버는 같은 주문 ID의 중복 제출을 기존 remote job에 연결해야 한다.
- lease 만료는 작업 종료 증거가 아니다. remote job을 먼저 조회한다.
- 최신 execution token과 `Reservation.RESERVED`를 모두 검사한 결과만 반영한다.
- 크롤링 차단·입력 초과·모델 최종 실패는 크레딧 반환 대상이다.
- 이메일·문자 알림 실패는 주문 성공과 크레딧 사용을 되돌리지 않는다. Outbox로 재시도한다.

### 8.5 재평가 요청

`POST /geo/order`에 `baselineOrderId`만 담긴 요청이다.

1. 원 주문의 소유자, `with_improvement = true`, `baseline_order_id IS NULL`, AnalysisJob `SUCCEEDED`를 확인한다. 아니면 `404 RESOURCE_NOT_FOUND` 또는 `409 REEVAL_NOT_ALLOWED`.
2. 원 주문 행을 `SELECT ... FOR UPDATE`로 잠근다.
3. 기한이 지났으면 `409 REEVAL_WINDOW_EXPIRED`.
4. 진행 중인 재평가가 있으면 `409 REEVAL_IN_PROGRESS`.
5. 사용 횟수가 한도 이상이면 `409 REEVAL_LIMIT_EXCEEDED`.
6. 원 주문의 URL·도메인을 복사한 새 주문과 AnalysisJob을 같은 트랜잭션으로 저장한다. 요청의 다른 필드는 무시하므로 클라이언트가 URL을 바꾸지 못한다.
7. worker는 저장된 `raw_scraped_data`를 재사용하지 않고 라이브 페이지를 새로 수집해 `/evaluate`로 채점한다.

멱등키는 받지 않는다. 4번 검사가 원 주문 잠금 아래에서 중복 생성을 막는다.

추후: 6번 전에 페이지만 먼저 수집해 JSON-LD가 원 주문과 같으면 "아직 반영되지 않음"으로 안내하고 횟수를 쓰지 않는다. `OrderService`에 TODO로 남겨 두었다.

## 9. Pack 환불

### 9.1 자동 전액 환불 조건

| 상품 | 조건 | 환불 총액 |
| --- | --- | ---: |
| Pack 1 | Q=A=1, R=C=H=F=E=0 | 16,390원 |
| Pack 5 | Q=A=5, R=C=H=F=E=0 | 53,900원 |
| Pack 20 | Q=A=20, R=C=H=F=E=0 | 163,900원 |

Payment가 `SUCCEEDED`이고 해당 Batch가 전량 미사용일 때만 provider에 VAT 포함 총액 전액 취소를 요청한다. 일부 사용·예약·만료된 묶음은 PG가 부분 취소를 지원하더라도 자동 환불하지 않는다.

자동 처리 범위 밖이라는 응답을 법적 환불 불가라고 표현하지 않는다. 사유 코드와 문의 경로를 제공한다.

### 9.2 환불 트랜잭션

1. T1에서 Wallet → Payment → Batch 순으로 잠근다.
2. 전량 미사용·미환불을 다시 검사한다.
3. A 전체를 H로 옮기고 Refund·REFUND_HOLD·Outbox를 원자 커밋한다.
4. 트랜잭션 밖에서 provider별 전액 취소를 호출한다.
5. 성공 T2는 H→F, REFUND, 취소 거래 키와 누계, Payment `REFUNDED`를 함께 반영한다.
6. 확정 실패는 H→A와 REFUND_RELEASE를 한 번만 반영한다.
7. timeout·응답 유실은 H를 유지하고 `UNKNOWN`으로 조회 대사한다.

`Payment.refunded_amount_krw`는 확인된 취소 거래 누계다. 같은 provider 취소 거래 키를 다시 수신해도 증가시키지 않는다. `0 ≤ refunded ≤ totalAmount`를 보장한다.

### 9.3 외부 취소

우리 Refund 없이 취소가 관찰되면 `review_required=true`와 Wallet 이용 제한을 기록한다. 전량 미사용 전액 취소만 자동 원장 복구할 수 있다. 이미 사용한 Pack의 부분·전액 외부 취소는 음수 잔액이나 과거 원장 삭제로 맞추지 않고 운영 검토로 보낸다.

Partner 계좌이체 해지·과오납 반환은 Pack Refund를 사용하지 않는다.

## 10. API 계약

### 10.1 공통 규칙

- Base path는 `/api/v1`.
- 공개 경로(가입·로그인·상품 조회·Webhook)를 제외한 모든 API는 `Authorization: Bearer <accessToken>`.
- 회원 ID는 Principal에서 얻고 body에 받지 않는다.
- 다른 회원의 리소스는 404로 응답한다.
- 생성·승인·취소 POST는 `Idempotency-Key`가 필수다. 단, `POST /geo/order`는 크레딧을 예약하는 `withImprovement: true`일 때만 필수다.
- 같은 키·같은 정규화 body는 같은 리소스와 기존 결과를 반환한다.
- 같은 키·다른 body는 `409 IDEMPOTENCY_KEY_REUSED`다.
- 목록은 `(created_at,id)` 역순 cursor, 기본 20·최대 100이다.
- 시간은 ISO 8601 UTC, 금액은 KRW 정수다.

공통 오류:

```json
{
  "code": "INSUFFICIENT_PACK_CREDITS",
  "message": "사용 가능한 Pack 크레딧이 부족합니다.",
  "retryable": false,
  "resourceId": null,
  "traceId": "trace_4ca2"
}
```

### 10.2 프로젝트 구현 API

| 메서드·경로 | 인증 | 핵심 역할 |
| --- | --- | --- |
| GET `/pack-products` | 공개 | 판매 Pack·공급가액·VAT·총액 |
| POST `/purchase-orders` | 회원 | Pack 주문·Payment 생성 |
| GET `/purchase-orders/{orderId}` | 소유자 | 주문·결제·지급·환불 상태 |
| GET `/purchase-orders` | 회원 | Pack 구매 이력 |
| POST `/payments/confirm` | 소유자 | 토스 카드·간편결제 승인 |
| GET `/pack-credits/balance` | 회원 | 가용·예약·환불 보류 잔액 |
| GET `/pack-credits/batches` | 회원 | 구매별 잔액·만료 |
| GET `/pack-credits/ledger` | 회원 | Pack 원장 |
| POST `/geo/order` (기존 확장) | 회원 | 무료 Scan, `withImprovement`로 개선안 포함 주문(크레딧 예약), `baselineOrderId`로 재평가 |
| GET `/geo/reports` (기존 확장) | 회원 | 분석 주문 목록. 재평가 주문 제외, 점수·재평가 요약 포함 |
| GET `/geo/report/{orderId}` (기존 확장) | 소유자 | 상태·결과(무료는 제한)·크레딧 상태·`versions`·남은 재평가 |
| POST `/geo/report/delete/{orderId}` (기존) | 소유자 | 리포트 삭제. 원 주문 삭제 시 재평가 주문도 숨김 |
| GET `/payments/{paymentId}/refund-eligibility` | 소유자 | 미사용 전액 환불 가능 확인 |
| POST `/payments/{paymentId}/refunds` | 소유자 | provider 전액 취소 접수 |
| GET `/refunds/{refundId}` | 소유자 | 환불 상태 |

### 10.3 운영·복구 API

| 메서드·경로 | 인증 | 역할 |
| --- | --- | --- |
| POST `/webhooks/payments/toss` | PG 통지 | 영속 수신 후 빠른 2xx |
| GET `/admin/payment-reviews` | 관리자 | 장기 미확정·외부 취소·금액 불일치 |
| POST `/admin/payments/{paymentId}/reconcile` | 관리자 | 실제 provider 조회 작업 재예약 |
| GET `/admin/order-reviews` | 관리자 | 개선안 포함 주문의 lease·remote job·원장 불일치, 반복 실패 재평가 |

관리자 API는 UI 숨김이 아니라 서버 권한 검사·감사 로그가 필요하다. 임의 Payment 성공·임의 무제한 크레딧 지급 버튼은 만들지 않는다.

### 10.4 Pack 주문 생성

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
  "expiresAt": "2026-09-15T06:30:00Z",
  "policyVersion": "pricing-2026-09-14"
}
```

`paymentMethod`는 `CARD` 또는 `EASY_PAY`다. `BANK_TRANSFER`는 이 API에서 `409 BANK_TRANSFER_NOT_ALLOWED_FOR_PACK`이다.

### 10.5 토스 승인 — 카드·간편결제 공통

```json
{
  "orderId": "po_67b1e630-8460-4c45-b99b-5b238a136cde",
  "paymentKey": "<토스 paymentKey>",
  "amount": 53900
}
```

amount는 비교용이며 서버는 주문의 `totalAmount=53900`을 승인 command에 사용한다. paymentKey가 주문에 결합되면 다른 키로 바꿀 수 없다.

### 10.6 공통 승인 응답

성공 `200`:

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

미확정 `202`:

```json
{
  "orderId": "po_67b1e630-8460-4c45-b99b-5b238a136cde",
  "paymentStatus": "UNKNOWN",
  "creditGranted": false,
  "pollUrl": "/api/v1/purchase-orders/po_67b1e630-8460-4c45-b99b-5b238a136cde",
  "retryAfterSeconds": 3
}
```

### 10.7 분석 주문·재평가

요청·응답 예시는 프론트 명세(`GEO_credit_frontend_api_spec.md` §7·§11, `GEO_verification_compare_frontend_spec.md` §3)를 기준으로 한다. 백엔드 처리 분기:

| 요청 | 처리 |
| --- | --- |
| `baselineOrderId` 있음 | 재평가(§8.5). 다른 필드 무시, 크레딧·멱등키 없음 |
| `withImprovement: true` | 멱등키 검사 → Wallet 잠금 → Batch 선택·RESERVE → 주문·AnalysisJob 저장(§8.2). 응답에 `creditStatus=RESERVED`, 남은 크레딧 |
| 그 외 | 무료 Scan. Wallet 잠금 → 월 한도 검사(§7.2) → 주문·AnalysisJob 저장 |

모든 분기에서 계정 인증(`ACCOUNT_NOT_VERIFIED`)과 전역 큐 상한(`QUEUE_FULL`)을 먼저 검사한다.

주문 요청의 도메인 입력은 §3의 `domainType`·`educationSegment`와 기존 `GeoOrderRequest.serviceType`을 하나로 합쳐야 한다(§20).

### 10.8 오류 매핑

| HTTP | code | 백엔드 처리 |
| ---: | --- | --- |
| 400 | `INVALID_REQUEST` | 필드·형식 오류 |
| 400 | `IDEMPOTENCY_KEY_REQUIRED` | POST 처리 전 거절 |
| 400 | `EDUCATION_SEGMENT_REQUIRED` | EDUCATION segment 누락 |
| 401 | `UNAUTHORIZED` | 인증 필요 |
| 403 | `ACCOUNT_RESTRICTED` | 결제·원장 검토 계정 |
| 403 | `ACCOUNT_NOT_VERIFIED` | 이메일 미인증 계정의 분석 주문 |
| 404 | `RESOURCE_NOT_FOUND` | 없음 또는 다른 소유자 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 동일 키·다른 body |
| 409 | `ORDER_EXPIRED` | 신규 승인 금지 |
| 409 | `AMOUNT_MISMATCH` | provider 호출 전 차단 |
| 409 | `PAYMENT_KEY_CONFLICT` | 기존 토스 키와 불일치 |
| 409 | `PAYMENT_PROVIDER_MISMATCH` | 주문 결제수단 불일치 |
| 409 | `PAYMENT_SESSION_EXPIRED` | provider 세션 만료 |
| 409 | `BANK_TRANSFER_NOT_ALLOWED_FOR_PACK` | Pack 수동이체 금지 |
| 409 | `DEPOSIT_ALREADY_REPORTED` | 같은 Partner 입금 알림 중복 — 계획 |
| 409 | `INSUFFICIENT_PACK_CREDITS` | 개선안 포함 주문 생성 안 함 |
| 409 | `REEVAL_NOT_ALLOWED` | 재평가 대상 아님(개선안 없는 주문·재평가 주문·미완료) |
| 409 | `REEVAL_IN_PROGRESS` | 같은 원 주문의 재평가 진행 중 |
| 409 | `REEVAL_LIMIT_EXCEEDED` | 재평가 횟수 소진 |
| 409 | `REEVAL_WINDOW_EXPIRED` | 재평가 기한 경과 |
| 409 | `CREDITS_IN_USE` | 환불과 예약 경쟁 |
| 409 | `CREDITS_ALREADY_USED` | 자동 전액 환불 불가 |
| 409 | `NO_REFUNDABLE_CREDITS` | 환불 가능 수량 없음 |
| 409 | `REFUND_REVIEW_REQUIRED` | 자동 처리 중단 |
| 422 | `PAYMENT_DECLINED` | provider가 확정한 실패 |
| 422 | `UNSUPPORTED_URL` | URL 정책 위반 |
| 422 | `UNSUPPORTED_DOMAIN` | 허용 enum 아님 |
| 429 | `FREE_SCAN_LIMIT_EXCEEDED` | 무료 Scan 한도 초과 |
| 429 | `QUEUE_FULL` | 영속 작업 생성·크레딧 예약 안 함 |
| 503 | `TEMPORARILY_UNAVAILABLE` | 상태 기록 전 장애 |

## 11. 구현 데이터 모델

### 11.1 공통 원칙

- PK는 기존 프로젝트 규칙을 따른다.
- 외부 공개 ID는 추측하기 어려운 별도 UUID 문자열을 권장한다.
- 시간은 PostgreSQL `timestamptz`.
- enum은 JPA `EnumType.STRING`과 `VARCHAR` CHECK를 기본으로 한다.
- PostgreSQL 고유 enum과 ordinal을 혼용하지 않는다.
- 운영 마이그레이션은 Flyway 등 현재 도구로 관리하고 `ddl-auto=validate`를 사용한다.

### 11.2 핵심 테이블

| 테이블 | 주요 필드 | 필수 제약 |
| --- | --- | --- |
| `pack_product` | code, name, version, quantity, supply_amount, vat_amount, total_amount, currency, validity_months nullable, reeval_count, reeval_window_days, active, policy_version | code UNIQUE, 금액·수량 양수, total=supply+vat |
| `purchase_order` | public_id, member_id, product snapshot, payment_method, payment_provider, supply/vat/discount/total snapshot, policy_version, lifecycle, expires_at, created_at | public_id UNIQUE, total>0, 회원 FK |
| `payment` | purchase_order_id, provider, environment, merchant_id, provider_transaction_id nullable, status, provider_status, total_amount, refunded_amount, approved_at, grant_applied_at, operation_token, lease_until, next_check_at, review_required | order UNIQUE, provider 거래 식별 UNIQUE, 0≤refunded≤total |
| `pack_credit_wallet` | member_id, toss_customer_key, restricted, reason, updated_at | member_id PK/FK, customer key UNIQUE |
| `pack_credit_batch` | member_id, payment_id, Q/A/R/C/H/F/E, granted_at, expires_at nullable, blocked | payment_id UNIQUE, 수량 비음수, Q 합계 CHECK |
| `pack_credit_reservation` | order_id, batch_id, member_id, quantity, state, reserved_at, finalized_at | order_id UNIQUE, quantity=1 |
| `pack_credit_ledger` | member_id, batch_id, event_key, event_type, delta Q/A/R/C/H/F/E, order_id, refund_id, reason, actor, created_at | event_key UNIQUE, UPDATE·DELETE 금지 |
| `client_order` (기존 확장) | + `with_improvement` boolean NOT NULL DEFAULT false, + `baseline_order_id` nullable self FK | `CHECK (baseline_order_id IS NULL OR with_improvement = false)`. 원 주문이 재평가 주문이 아닌지는 서비스에서 검사 |
| `analysis_job` (기존 확장) | + `completed_at` nullable | `SUCCEEDED` 전이 시 기록. 재평가 기한 계산 기준 |
| `client` (기존 확장) | + `email_verified_at` nullable | OAuth 가입은 가입 시각으로 채움 |
| `payment_refund` | payment_id, batch_id, member_id, quantity, amount, status, reason, pg_idempotency_key, provider_cancel_key, retry fields | 진행 환불 payment당 최대 1개, MVP 전액 |
| `api_idempotency` | member_id, operation_scope, idem_key, request_hash, resource_id, state, response snapshot, timestamps | member+scope+key UNIQUE |
| `payment_webhook_inbox` | provider, environment, transmission_id nullable, payload_hash, protected_payload, status, retry fields | provider 범위 중복 방지 |
| `outbox_event` | event_key, aggregate, event_type, protected_payload, state, retry fields | event_key UNIQUE |

토스 `paymentKey`는 `provider_transaction_id`로 저장하고 길이·형식은 어댑터가 검증한다. 승인 결과의 실제 method(`카드`·`간편결제`)와 간편결제사 이름은 Payment에 함께 저장한다. provider 원문 응답은 꼭 필요한 필드만 정규화해 저장하고, 원문 보관이 필요하면 암호화·접근·삭제 정책을 별도로 둔다.

### 11.3 관계

```mermaid
erDiagram
    PURCHASE_ORDER ||--|| PAYMENT : records
    PAYMENT ||--o| PACK_CREDIT_BATCH : grants
    PAYMENT ||--o{ PAYMENT_REFUND : refunds
    PACK_CREDIT_BATCH ||--o{ PACK_CREDIT_RESERVATION : allocates
    CLIENT_ORDER ||--o| PACK_CREDIT_RESERVATION : reserves
    CLIENT_ORDER ||--o{ CLIENT_ORDER : reevaluated_by
    CLIENT_ORDER ||--|| ANALYSIS_JOB : runs
    CLIENT_ORDER ||--o| ANALYSIS_REPORT : stores
```

지급 전 Payment에는 Batch가 없다. 무료 Scan·재평가 주문에는 Reservation이 없다. member_id 일치는 서비스 계층과 가능한 복합 FK로 모두 보호한다.

### 11.4 인덱스

- 구매 목록: `(member_id, created_at DESC, id DESC)`.
- Batch 선택: `(member_id, expires_at, granted_at, id)` + 가용·차단 필터.
- 원장 목록: `(member_id, created_at DESC, id DESC)`.
- 분석 작업 큐: `analysis_job (status, next_run_at)`.
- 재평가 조회: `client_order (baseline_order_id)`.
- Payment 복구: `(status, next_check_at)`.
- Refund·Inbox·Outbox: `(status/state, next_attempt_at)`.
- 진행 환불 부분 UNIQUE: `payment_id WHERE status IN ('REQUESTED','PROCESSING','UNKNOWN')`.
- 무료 Scan 한도: `client_order (client_id, created_at) WHERE with_improvement = false AND baseline_order_id IS NULL`.

## 12. 동시성·멱등성

### 12.1 잠금 순서

구현 범위의 기본 잠금 순서는 다음과 같다.

> Wallet → Payment → Batch(ID 순) → 원 주문(재평가 시) → Reservation → Refund

- 후보를 조회한 뒤 실제 전이 트랜잭션에서 다시 조건을 검사한다.
- 잔액을 읽고 Java에서 1을 빼서 저장하는 방식만 사용하지 않는다.
- 사용자 취소와 worker 시작은 조건부 전이로 승자 하나만 만든다.
- PG·AI·이메일 네트워크 호출 중 DB 잠금을 유지하지 않는다.
- 데드락·잠금 timeout은 짧은 DB 트랜잭션만 제한 재시도한다.

Wallet은 가입 시 만들고 기존 회원은 마이그레이션으로 채운다. 존재하지 않는 행을 `SELECT FOR UPDATE`했다고 회원별 직렬화가 성립한다고 가정하지 않는다.

### 12.2 중복 방지 계층

| 계층 | 유일성 | 보호 대상 |
| --- | --- | --- |
| HTTP API | subject + operation scope + idempotency key + request hash | 더블클릭·네트워크 재전송 |
| 비즈니스 DB | 구매 주문당 Payment, payment당 Batch, 분석 주문당 Reservation, ledger eventKey | 서로 다른 HTTP 키·Webhook·대사 |
| provider | 승인·취소 operation별 저장된 키 | 외부 중복 호출 |
| worker | execution token + lease + remote job ID | AI 중복 실행·늦은 결과 |
| 재평가 | 원 주문 행 잠금 + 진행 중 검사 + 횟수 검사 | 한도 초과·동시 요청 |

HTTP idempotency 행과 생성 리소스를 같은 트랜잭션에 연결한다. 인증·소유자 검사를 캐시된 응답 반환보다 먼저 한다. HTTP 키 보관은 최소 30일의 설계값을 사용하되 미종결 거래의 비즈니스 유일성은 영구 제약으로 유지한다.

## 13. 결제 어댑터

Pack 결제사는 토스페이먼츠 하나다. 카드와 간편결제를 같은 어댑터로 처리하고, 여러 PG를 위한 범용 추상화는 만들지 않는다.

```java
class TossPaymentGateway {
    ApprovalResult confirm(TossConfirmCommand command);
    PaymentSnapshot query(QueryPaymentCommand command);
    CancelResult cancel(CancelCommand command);
}
```

`ApprovalResult`와 `PaymentSnapshot`은 최소한 성공/확정 실패/불확실, provider 거래 ID, 주문 ID, 금액, 통화, method, 원상태, 승인 시각, 취소 누계를 구분한다.

### 13.1 토스 매핑

| 작업 | 공식 API 의미 | 저장·검증 |
| --- | --- | --- |
| 승인 | paymentKey·orderId·amount 승인 | 주문 총액·통화·method 검증 |
| paymentKey 조회 | 단일 Payment 조회 | 승인·취소 복구 |
| orderId 조회 | 주문 기준 조회 | paymentKey 유실 복구 |
| 취소 | paymentKey 기준 취소 | Refund 고정 멱등키·총액 |

### 13.2 비밀값

- 토스 시크릿 키는 서버 Secret 관리에 둔다.
- 테스트·운영 키, MID, Webhook URL, DB를 가능한 분리한다.
- 카드번호·CVC·비밀번호를 백엔드 DTO·DB에 받거나 저장하지 않는다.
- provider 인증 헤더, business secret을 로그·trace·예외 메시지에 포함하지 않는다.

## 14. Webhook·대사·복구

### 14.1 Webhook Inbox

1. provider별 전용 HTTPS 경로에서 본문 크기·형식·허용 이벤트를 검사한다.
2. 전송 ID 또는 payload hash로 Inbox에 영속 기록한다.
3. Inbox 커밋 뒤 빠르게 2xx를 응답한다.
4. worker가 저장된 내부 주문과 provider API 조회 결과를 비교한다.
5. 검증된 snapshot만 `applyVerifiedPaymentSnapshot`에 넘긴다.
6. 중복 지급은 Payment당 Batch UNIQUE와 PURCHASE eventKey가 최종 차단한다.

Webhook payload의 성공 문자열만 보고 Pack을 지급하지 않는다. Webhook 지원·인증 방법이 공식 계약에서 확인되지 않은 provider 경로는 만들지 않는다.

### 14.2 순서 역전

클라이언트 승인 응답, Webhook, 주기 대사는 같은 검증·반영 서비스로 들어간다. 외부 조회 전 `mutation_version`과 operation token을 읽고, 반영 시 다시 잠가 값이 바뀌었으면 snapshot을 그대로 적용하지 않는다.

늦은 승인 응답은 확인된 취소 누계를 줄이거나 `REFUNDED`를 되돌릴 수 없다. 같은 Payment의 승인·취소·대사는 하나의 영속 작업 소유권으로 직렬화한다.

### 14.3 초기 복구 주기

| 대상 | 기본값 |
| --- | --- |
| Payment `CONFIRMING/UNKNOWN` | 1분 스캔 + 지수 백오프 |
| Refund `REQUESTED/PROCESSING/UNKNOWN` | Outbox + 1분 누락 복구 |
| 분석 작업 lease 만료 | remote job 조회 후 재개 |
| Inbox·Outbox 실패 | 1→5→15→60분, 이후 검토 |
| 30분 이상 결제 미확정 | 관리자 알림, 자동 실패 금지 |
| provider 성공·DB 미지급 | 검증 후 T2 재실행 |
| provider 취소·DB 미반영 | 취소 거래 키 검증 후 환불 반영 |
| 수량·금액 불변식 불일치 | Wallet 제한·운영 검토 |

운영 데이터가 없는 초기값이며 외부 SLA가 아니다.

## 15. Spring 구성

마이크로서비스를 새로 만들지 않고 현재 Spring 애플리케이션의 모듈·패키지를 분리한다.

| 구성요소 | 책임 |
| --- | --- |
| `PackCatalogService` | 상품·정책 버전·VAT 포함 총액 제공 |
| `PurchaseOrderService` | 주문 스냅샷·payment method 검증 |
| `PaymentApplicationService` | provider 호출 전후 조정 |
| `PaymentTransactionService` | 짧은 T1/T2 트랜잭션 |
| `TossPaymentGateway` | 토스 카드·간편결제 승인·조회·취소 DTO |
| `PackCreditService` | Batch 선택·예약·소비·반환·환불 수량 |
| `OrderService` (기존 확장) | 무료 한도·개선안 크레딧 예약·재평가 검증 |
| `AnalysisReportService` (기존 확장) | 무료 결과 제한·`versions`·점수 요약 |
| `GeoAsyncWorker` (기존 확장) | 주문 종류별 AI 호출(`/evaluate`·`/diagnose`)·결과 반영·CONSUME/RELEASE |
| `RefundApplicationService` | 환불 보류·provider 취소·복구 |
| `WebhookInboxService` | provider 통지 영속 수신 |
| `ReconciliationWorker` | 실제 거래 조회·불일치 복구 |
| `OutboxWorker` | 환불·알림 등 영속 작업 |

`@Transactional` 안에서 PG·AI HTTP 호출을 실행하지 않는다. 같은 클래스의 self-invocation에 `@Transactional`을 붙였다고 새 경계가 생긴다고 가정하지 않는다. 별도 Bean 또는 `TransactionTemplate`을 사용한다.

공통 승인 의사코드:

```text
approve(principal, providerRequest, apiIdempotencyKey):
  prepared = tx.prepareApproval(principal, providerRequest, apiIdempotencyKey)
  if prepared.alreadyCompleted: return prepared.existingResult
  if prepared.ownedByOtherWorker: return 202 + orderStatusUrl

  result = approvalExecutor.execute(prepared.command)  // provider별 호출, DB TX 밖

  if result.verifiedSuccess:
      return tx.applyApprovalAndGrant(result, prepared.operationToken)
  if result.definitiveFailure:
      return tx.applyFailure(result, prepared.operationToken)

  tx.markUnknownAndScheduleQuery(prepared.operationToken)
  return 202 + orderStatusUrl
```

카드와 간편결제는 같은 토스 `confirm` 경로와 같은 결과 반영 경로를 사용한다.

## 16. 보안·개인정보·운영

- 분석 주문 API는 로그인 필수다. 인증 없이 열린 경로는 가입·로그인·상품 조회·Webhook뿐이며, 가입·로그인에는 IP 속도 제한을 건다(§7.2).
- 모든 회원 리소스 명령·조회는 소유자를 검사한다. UUID가 권한 검사를 대체하지 않는다.
- 결과 제한·크레딧 차감 여부는 요청 필드가 아니라 주문에 저장된 `with_improvement`와 서버 원장으로 판단한다.
- CORS는 실제 프론트 origin과 `Authorization`, `Content-Type`, `Idempotency-Key`만 필요한 범위로 허용한다.
- 상품 가격·VAT·크레딧·환불액·주문 소유자는 서버 기준이다.
- 크롤링 URL은 http/https만 허용하고 loopback·사설 IP·link-local·클라우드 메타데이터를 DNS 해석과 redirect마다 차단한다.
- URL의 민감 query, 연락처, 토큰, provider secret은 로그에서 마스킹한다.
- 알림은 인증 계정 연락처를 사용하고 요청 body로 임의 제3자 연락처를 받지 않는다.
- Payment·원장 기록을 회원 탈퇴와 함께 연쇄 삭제하지 않는다. 개인정보 분리·보관 정책을 적용한다.
- 테스트 결제와 운영 결제를 화면·키·DB 필드로 구분한다.
- 운영자는 미확정 거래를 조회·재대사할 수 있지만 근거 없이 성공 처리하지 못한다.
- 핵심 잠금·부분 인덱스·CHECK 테스트는 실제 PostgreSQL에서 수행한다. H2만으로 동시성 검증을 끝내지 않는다.

## 17. Partner·세금계산서·수동 계좌이체 — 계획

### 17.1 상품 계약

| planCode | 월 공급가액 | VAT 포함 | 포함 학원 | 학원당 월 페이지 | 조건 |
| --- | ---: | ---: | ---: | ---: | --- |
| `FOUNDING_PARTNER` | 290,000 | 319,000 | 10 | 10 | 첫 10개 파트너, 12개월 고정, 사례 공개 동의 |
| `PARTNER` | 490,000 | 539,000 | 20 | 10 | 추가 학원 1곳당 20,000 + VAT |
| `PARTNER_PLUS` | 990,000부터 | 견적 | 50 | 계약별 | 즉시 결제 없음 |

페이지 수가 아니라 관리 학원 수가 월 과금 기준이다. AI 검색 추적은 포함하지 않는다.

### 17.2 계좌이체 경계

`BANK_TRANSFER`는 고객이 회사 계좌로 직접 송금하고 운영자 또는 은행 거래 대사가 확인하는 방식이다. PG Payment와 상태를 공유하지 않는다.

```mermaid
sequenceDiagram
    participant F as 파트너 화면
    participant B as Spring
    participant O as 운영자
    participant K as 은행 거래내역
    F->>B: Partner 신청
    O->>B: 신청 승인·청구서 발행
    B-->>F: 금액·계좌·납기
    F->>K: 직접 송금
    F->>B: 입금 완료 알림
    O->>K: 거래 일치 확인
    O->>B: 입금 확정
    B-->>F: Invoice PAID·Partner ACTIVE
```

고객의 DepositReport는 상태를 `DEPOSIT_REPORTED`로만 바꾼다. 실제 거래 확인 없이 Invoice `PAID`, Subscription `ACTIVE`, 학원 한도를 부여하면 안 된다.

### 17.3 계획 API

| 메서드·경로 | 역할 |
| --- | --- |
| GET `/partner-plans` | 플랜·가격·포함 학원·견적 여부 |
| POST `/partner-applications` | 사업자·요금제 신청 |
| GET `/partner-applications/{applicationId}` | 검토·Invoice 연결 상태 |
| GET `/partner-subscriptions/{subscriptionId}` | 활성 권한·학원·기간 한도 |
| POST `/partner-subscriptions/{subscriptionId}/cancel` | 다음 주기 해지 요청 |
| GET `/billing/invoices` | 청구 이력 |
| GET `/billing/invoices/{invoiceId}` | 금액·입금 계좌·세금계산서 상태 |
| POST `/billing/invoices/{invoiceId}/deposit-reports` | 고객 입금 알림 |
| GET `/admin/billing/deposit-reports` | 미확인 입금 알림 |
| POST `/admin/billing/invoices/{invoiceId}/confirm-deposit` | 검증된 입금 확정 |
| POST `/admin/billing/invoices/{invoiceId}/reject-deposit` | 불일치 알림 반려 |

### 17.4 계획 상태

- PartnerApplication: `REQUESTED`, `REVIEWING`, `APPROVED`, `REJECTED`, `CANCELED`.
- BillingInvoice: `DRAFT`, `AWAITING_DEPOSIT`, `DEPOSIT_REPORTED`, `PAID`, `OVERDUE`, `CANCELED`.
- DepositReport: `REPORTED`, `CONFIRMED`, `REJECTED`.
- PartnerSubscription: `PENDING_PAYMENT`, `ACTIVE`, `PAST_DUE`, `CANCEL_AT_PERIOD_END`, `CANCELED`.
- TaxInvoice: `NOT_REQUESTED`, `REQUESTED`, `ISSUED`, `FAILED`, `CANCELED`.

세금계산서 발행 상태와 입금 상태는 같은 enum으로 합치지 않는다. 세금계산서 발행 실패가 입금 사실을 없애지 않으며, 입금 확인이 세금계산서 발행 완료를 뜻하지 않는다.

### 17.5 계획 데이터 모델

| 테이블 | 핵심 필드·제약 |
| --- | --- |
| `partner_plan` | code, 월 공급가액·VAT, 포함 학원, 학원당 페이지, 조건, active |
| `partner_application` | member, plan snapshot, 관리 학원 수, 사업자정보, 동의, status, review actor |
| `partner_subscription` | member, application, plan snapshot, status, current period, academy limits |
| `billing_invoice` | application/subscription, period, 공급가액·VAT·할인·총액, status, due_at, paid_at |
| `deposit_report` | invoice, 신고 입금자·금액·시각, status, confirmer, evidence reference |
| `tax_invoice_record` | invoice, 공급받는자 정보, status, external document id, issued_at |
| `partner_academy` | subscription, academy identity, segment, noise filter, active |
| `partner_usage` | subscription, academy, period, page count, remediation link |

사업자등록번호·대표자·세금계산서 이메일은 목적·보관 기간·접근 권한을 정한 뒤 암호화 또는 분리 저장한다. 일반 결제 로그와 분석 로그에 포함하지 않는다.

### 17.6 입금 확정 트랜잭션

1. 관리자 인증·권한·사유를 검증한다.
2. Invoice를 잠그고 아직 `PAID/CANCELED`가 아닌지 확인한다.
3. 실제 은행 거래 참조·입금자·금액·시각을 청구서와 비교한다.
4. DepositReport `CONFIRMED`, Invoice `PAID`, 최초 Subscription `ACTIVE`, entitlement Outbox를 한 번만 커밋한다.
5. 동일 은행 거래 참조를 다른 Invoice에 재사용하지 못하게 UNIQUE 처리한다.
6. 초과·부족 입금은 자동 활성화하지 않고 검토한다.

Founding 잔여 좌석은 신청 접수가 아니라 승인·계약 확정 중 하나의 명시된 시점을 기준으로 잠가야 한다. 그 기준은 구현 전에 확정한다.

### 17.7 지금 만들지 않을 것

- Partner JPA 빈 엔티티·마이그레이션
- billingKey·자동결제 토큰
- 월 자동 갱신 스케줄러
- 세금계산서 외부 서비스 어댑터
- 은행 Open API 연동
- Partner 해지·일할 환불 자동화

이번 구현의 Pack 원장에 `GRANT_SUBSCRIPTION` 같은 사용하지 않는 이벤트를 미리 추가하지 않는다.

## 18. 인수 테스트

아래 항목은 구현 완료 조건이며 현재 통과 결과가 아니다.

### 18.1 상품·결제

| ID | 시나리오 | 기대 결과 |
| --- | --- | --- |
| PAY-01 | Pack 1/5/20 주문 | 총액 16,390/53,900/163,900, 수량 1/5/20 |
| PAY-02 | Pack 5를 49,000원 또는 100원으로 승인 | provider 호출 전 AMOUNT_MISMATCH, 지급 0 |
| PAY-03 | Pack 주문에 BANK_TRANSFER | 409, Payment 외부 호출 없음 |
| PAY-04 | 다른 회원 주문 승인·조회·환불 | 404, 변경 없음 |
| PAY-05 | 토스 같은 주문 승인 10회 | 외부 승인 작업 하나, Batch·PURCHASE 하나 |
| PAY-06 | 승인·Webhook·대사 동시 도착 | 지급 정확히 1회 |
| PAY-07 | provider 성공 후 T2 전 종료 | 재기동 대사로 1회 지급 |
| PAY-08 | T2 후 응답 유실 | 조회·재요청으로 기존 성공 복원 |
| PAY-09 | timeout·일시 404 | UNKNOWN, 즉시 재결제 유도 없음 |
| PAY-10 | 취소 뒤 늦은 성공 응답 | 취소 누계·REFUNDED 되돌림 없음 |
| EASY-01 | `EASY_PAY` 주문을 토스 간편결제로 승인 | 지급 1회, 실제 method `간편결제` 기록 |
| EASY-02 | `CARD` 주문에서 사용자가 간편결제로 결제 | 금액 일치 시 지급, 실제 method 기록 |
| EASY-03 | 승인 결과 method가 `가상계좌` 등 허용 외 | 자동 지급 없음, 검토 대상 |
| EASY-04 | 다른 주문의 paymentKey·orderId 조합 | 승인·지급 없음 |

### 18.2 도메인·무료 Scan

| ID | 시나리오 | 기대 결과 |
| --- | --- | --- |
| DOM-01 | EDUCATION인데 segment 누락 | 400 EDUCATION_SEGMENT_REQUIRED |
| DOM-02 | SMALL_ACADEMY 기본값 | noiseFilter=false 저장 |
| DOM-03 | LARGE_ACADEMY 기본값 | noiseFilter=true 저장 |
| DOM-04 | ECOMMERCE에 education segment | 400, 작업 없음 |
| DOM-05 | TECH_BLOG | 422 UNSUPPORTED_DOMAIN |
| FREE-01 | 회원 월 5회 접수 후 6번째 | 429, 작업 생성 없음 |
| FREE-02 | 무료 결과 응답 | 문제 3개, 상세는 1개, 제한 필드 NULL |
| FREE-03 | 결과 30일 만료 | EXPIRED, Pack 원장 영향 없음 |
| FREE-04 | JWT 없이 `POST /geo/order` | 401, 주문 없음 |
| FREE-05 | 이메일 미인증 계정 주문 | 403 ACCOUNT_NOT_VERIFIED, 주문 없음 |
| FREE-06 | 무료 4회 사용 후 동시 요청 3건 | 1건만 접수, 나머지 429 |
| FREE-07 | 재평가 주문 | 무료 한도 사용 안 함 |

### 18.3 Pack 원장·개선안 포함 주문·재평가

| ID | 시나리오 | 기대 결과 |
| --- | --- | --- |
| CRD-01 | 잔액 1에서 서로 다른 개선안 포함 주문 2건 동시 접수 | 1건만 RESERVED, 다른 건 409 |
| CRD-02 | 같은 요청 멱등키 10회 | 주문·Reservation·RESERVE 하나 |
| CRD-03 | 같은 URL을 새 키로 새 주문 | 새 주문·별도 크레딧 예약 |
| CRD-04 | JSON-LD 생성 최종 실패(`jsonld.valid=false`) | SUCCEEDED 없음, 크레딧 RELEASE |
| CRD-05 | 성공 저장과 CONSUME 중 DB 실패 | 둘 다 롤백·재반영 가능 |
| CRD-06 | 성공·실패 callback 경쟁 | 최신 token만 한 번 종결 |
| REEVAL-01 | 개선안 없는 주문·재평가 주문을 원 주문으로 지정 | 409 REEVAL_NOT_ALLOWED |
| REEVAL-02 | 재평가 3건 동시 요청 | 1건만 생성, 나머지 409 REEVAL_IN_PROGRESS |
| REEVAL-03 | 미적용 페이지 재평가 성공 | 결과 저장·횟수 1 사용·크레딧 0 |
| REEVAL-04 | 재평가 FAILED | 횟수 사용 안 함·크레딧 0 |
| REEVAL-05 | 기한 경과 | 409 REEVAL_WINDOW_EXPIRED |
| REEVAL-06 | 2회 성공 후 3번째 | 409 REEVAL_LIMIT_EXCEEDED |
| REEVAL-07 | 요청 body에 다른 URL 포함 | 무시, 원 주문 URL로 수집 |
| DB-01 | 모든 시나리오 뒤 | Q=A+R+C+H+F+E, 모든 수량 비음수 |

### 18.4 환불

| ID | 시나리오 | 기대 결과 |
| --- | --- | --- |
| REF-01 | Pack 5 전량 미사용 | 5개 H→F, 53,900원 전액 취소 |
| REF-02 | Pack 20 중 7개 사용 | provider 호출 없이 409 |
| REF-03 | 환불 접수·개선안 포함 주문 예약 경쟁 | 한쪽만 조건 충족, 이중 사용 없음 |
| REF-04 | 취소 성공 후 DB 반영 전 종료 | H 유지 후 조회로 F 확정 |
| REF-05 | 취소 timeout | UNKNOWN, H 유지 |
| REF-06 | 취소 미실행 확정 | H→A 한 번, REFUND_RELEASE 한 번 |
| REF-07 | 외부에서 사용 Pack 취소 | Wallet 제한·검토, 음수 잔액 없음 |

### 18.5 Partner 계획 계약 테스트

| ID | 시나리오 | 기대 결과 |
| --- | --- | --- |
| BANK-01 | 고객이 입금 알림 제출 | DEPOSIT_REPORTED, ACTIVE 아님 |
| BANK-02 | 운영자가 실제 거래 없이 확인 | 거절, 감사 로그 |
| BANK-03 | 정확한 539,000원 거래 확인 | Invoice PAID·Subscription ACTIVE 한 번 |
| BANK-04 | 같은 은행 거래를 두 청구서에 사용 | 두 번째 UNIQUE 충돌·활성화 없음 |
| BANK-05 | 과소·과다 입금 | 자동 활성화 없음·검토 |
| BANK-06 | 세금계산서 발행 실패 | 입금 상태와 독립, 재시도 가능 |

## 19. 권장 구현 순서

| 순서 | 구현 | 완료 기준 |
| ---: | --- | --- |
| 1 | Pack 상품·금액·도메인 enum·마이그레이션 | 가격/VAT/도메인 테스트 |
| 2 | 주문·Payment·Wallet·Batch·원장 + Mock Gateway | PAY-01~05, DB 불변식 |
| 3 | 토스 카드 승인·조회 | 테스트 결제 E2E·응답 유실 복구 |
| 4 | 토스 간편결제 노출·method 검증 | EASY 테스트 |
| 5 | 개선안 포함 주문의 크레딧 예약·`/diagnose` 연동·결과 원자 커밋 | CRD 동시성·실패 반환 |
| 6 | 재평가(`baseline_order_id`)·횟수·기한 | REEVAL 테스트 |
| 7 | 무료 Scan 한도·제한 결과·30일 보관·이메일 인증 | FREE·DOM 테스트 |
| 8 | Webhook Inbox·Outbox·대사 | provider 성공/DB 실패 복구 |
| 9 | 미사용 Pack 전액 환불 | REF 테스트 |
| 10 | 운영 검토·환경 분리·관측성 | 실제 유료 공개 기준 |

학교 프로젝트 시연은 1~7단계의 테스트 환경까지로 제한할 수 있다. 실제 돈을 받으려면 8~10단계와 운영 담당자가 필요하다. Partner 단계는 이번 순서에 포함하지 않는다.

## 20. 구현 전 확정할 값

| 항목 | 현재 값·처리 |
| --- | --- |
| 실제 기존 엔티티·API 이름 | 소스·ERD와 매핑 필요 |
| Pack 1 만료 | 미확정, NULL |
| 재평가 기한 기준 | 명세 기본은 원 주문 완료 +30일, 정책 확인 필요 |
| 재평가 실패 시 횟수 복원 | `FAILED`는 횟수에 넣지 않는 기본안. 고객 사이트 차단으로 인한 반복 실패는 운영 검토 |
| 이메일 인증 방식 | 가입 시 메일 링크 인증 제안. OAuth 계정은 인증된 것으로 간주 |
| 가입·로그인 속도 제한 값 | IP당 분당 횟수 설계 필요 |
| 주문 요청의 도메인 입력 | §3의 `domainType`·`educationSegment`와 기존 `serviceType` 병합 필요 |
| 무료 회원 월 초기화 시각 | Asia/Seoul 달력 월 제안 |
| 토스 결제위젯 설정·API 버전 | 카드·간편결제만 노출하도록 계약·설정 확인. 간편결제 수수료 견적 확인 |
| provider Webhook·조회 기능 | 실제 가맹점 계약과 공식 버전 확인 |
| 유료 리포트 보관 기간 | Pack 크레딧 만료와 별도 정책 필요 |
| 환불 문의 채널·약관 문구 | 실제 유료 공개 전 확정 |
| Partner 입금 계좌·세금계산서 시스템·은행 대사 | Partner 구현 전 확정 |
| Pack 첫 달 차감 | 기간·대상·VAT 처리·중복 차감 확정 전 구현 금지 |
| Partner 미납·해지·일할 계산 | 정책 확정 전 구현 금지 |

## 21. 근거 자료

- `PRICING_MODEL.md`, 2026-09-14: 타깃, 상품 구조, 가격, Pack·Partner 기능, 계좌이체 정책.
- `GEO_credit_frontend_api_spec.md` v2.3: 프론트 요청·응답과 화면 상태 계약.
- `GEO_verification_compare_frontend_spec.md` v2.0: 재평가·전후 비교 계약.
- 첨부 `SW상상기업 사업계획서_Mal-Geum(송지한).pdf`: GEO 서비스와 비동기 처리 배경. 가격·범위는 최신 정책을 우선한다.
- [토스페이먼츠 주문서형 결제 연동](https://docs.tosspayments.com/guides/v2/payment-widget/integration)
- [토스페이먼츠 코어 API](https://docs.tosspayments.com/reference)
- [토스페이먼츠 인증·멱등키](https://docs.tosspayments.com/reference/using-api/authorization)
- [토스페이먼츠 Webhook](https://docs.tosspayments.com/guides/v2/webhook)
- [Spring Data JPA Locking](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html)
- [PostgreSQL Explicit Locking](https://www.postgresql.org/docs/current/explicit-locking.html)

결제사 공식 문서는 2026-09-15 다시 확인했다. 이 문서의 DB 구조, 상태, 잠금, 복구 주기는 본 프로젝트용 설계이며 결제사의 사업 정책이나 법적 환불 규정을 대신하지 않는다.
