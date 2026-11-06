# Flyway 자동 마이그레이션과 PricingAccount 이전

서버 시작 시 Flyway가 `public.flyway_schema_history`를 확인하고 미적용 SQL만 버전 순서로 실행한다.
Hibernate는 `ddl-auto: validate`로 엔티티와 스키마를 검사하며 테이블을 자동 변경하지 않는다.
SQL 실패 시 서버 시작을 중단하며, PostgreSQL에서 해당 마이그레이션 전체를 롤백한다.
트랜잭션은 Flyway가 관리하므로 SQL 파일에 `BEGIN`/`COMMIT`을 추가하지 않는다.

## 새 DB

기존 연결 설정으로 서버를 실행하면 초기 스키마부터 모든 마이그레이션을 자동 적용한다.
DB 자체와 접속 계정은 미리 준비되어 있어야 하며, 계정에는 `public`의 DDL 실행 권한이 필요하다.

## 기존 DB의 최초 전환

1. 기존 DB를 백업하고 기존 서버의 쓰기 요청을 중지한다. `.env`의 연결 대상과 `public` 스키마를 확인한다.
2. 최초 한 번만 아래 환경변수를 설정하고 서버를 실행한다. IDE에서는 실행 구성의 환경변수에 같은 값을 넣는다.

```powershell
$env:FLYWAY_BASELINE_ON_MIGRATE = 'true'
.\gradlew.bat bootRun
```

3. 시작 성공 후 환경변수를 제거하고 이후부터 평소처럼 서버를 실행한다.

```powershell
Remove-Item Env:FLYWAY_BASELINE_ON_MIGRATE
```

이 설정은 초기 테이블 생성 버전 `20261104`까지를 기존 DB의 기준으로 등록한다.
그 이후의 PricingAccount 이전, Partner 상태 통합, 재평가 스키마 변경은 실제 실행한다.
기존 DB는 `client`, `client_order`, `analysis_job`, `analysis_report`, `pack_order`,
`partner_subscription`, `academy`, `partner_order`를 갖춘 기존 애플리케이션 DB여야 한다.
다른 DB나 불완전한 수동 스키마에 기준 버전을 강제로 등록하면 안 된다.

PricingAccount/Partner 상태 SQL을 수동 적용한 DB도 동일한 최초 전환 절차를 사용한다.
두 SQL은 이미 적용된 구조를 지원하며 기존 계정 사용량·상품 소유·잔여량을 유지한다.
Hibernate가 재평가 컬럼/FK를 먼저 추가한 DB도 지원한다.
최초 등록 없이 기존 DB에 연결하면 자동으로 기준을 추정하지 않고 시작을 중단한다.

## 앞으로 구조를 변경할 때

1. 엔티티를 수정한다.
2. 이 디렉터리에 더 큰 버전의 SQL을 추가한다. 예: `V20261107__add_order_column.sql`.
   같은 날 여러 파일은 `V20261107_1__...sql`처럼 번호를 붙인다. `__`는 밑줄 두 개다.
3. 코드와 SQL을 함께 커밋하고 배포한다. 서버 시작 시 미적용 파일만 실행된다.

이미 적용한 SQL은 이름·내용을 수정하거나 삭제하지 않는다. 수정 사항은 새 버전 파일로 추가한다.
현재 두 기존 SQL의 트랜잭션 구문 제거는 Flyway에 등록하기 전의 전환 작업이다.
Flyway 이력 등록 후에는 같은 방식으로 수정하지 않는다. `repair`로 체크섬 검사를 우회하지 않는다.
버전 파일은 DDL과 필요한 기준 데이터/변환 SQL만 포함한다. 비밀번호·토큰·실제 개인정보·DB 덤프는 커밋하지 않는다.

`clean`은 비활성화되어 있다. 마이그레이션은 자동으로 이전 버전으로 되돌아가지 않는다.
PricingAccount 이전은 구 소유 컬럼과 회원의 `plan`, `free_promotion_page_count`를 제거하므로
구버전 복귀가 필요하면 백업 복원 또는 별도 복구 마이그레이션을 준비한다.

## 마이그레이션 검증

일반 테스트는 H2와 Hibernate의 `create-drop`을 사용하며 PostgreSQL 전용 Flyway SQL은 실행하지 않는다.
별도 PostgreSQL 테스트는 임의 이름의 전용 스키마만 생성·삭제한다. 운영 DB 대신 임시 DB를 사용한다.

```powershell
$env:MIGRATION_TEST_DB_URL = 'jdbc:postgresql://127.0.0.1:55439/postgres'
$env:MIGRATION_TEST_DB_USER = 'postgres'
$env:MIGRATION_TEST_DB_PASSWORD = ''
.\gradlew.bat test --tests '*FlywayMigrationPostgresTest'
```

빈 DB 생성 및 모든 엔티티 검증, 기존 DB 전환과 구매 보존, 수동 적용 DB 수용,
중복 실행 방지, 최초 전환 설정 누락 시 중단, 소유 관계 오류 시 전체 롤백을 검증한다.

## 첫 달 사용량

이전 코드에는 무료 분석 사용량의 기준 월·차감 이력이 없었다. 기존 `free_promotion_page_count`나 모든 일반 주문 건수로 사용량을 추정하지 않고, 전환 시 무료 사용량을 0으로 시작한다. 기존 주문의 `pricing_mode_at_acceptance`, `free_scan_usage_month`는 NULL을 유지한다. 이전 스크립트를 다시 실행해도 이미 존재하는 중앙 계정의 사용량은 초기화하지 않는다.

## 적용한 정책

- 무료 일반 분석: `Asia/Seoul` 달력 월 기준 5회. 접수 후 최종 실패해도 사용량을 유지한다.
- 유료 일반 분석: 유효한 Pack 또는 활성 Partner가 있으면 무제한이며 상품 사용량을 차감하지 않는다.
- Pack 유효성: PAID, 잔여량 양수, 만료 전. 마지막 크레딧이 소진되면 Pack만으로 받던 일반 분석 무제한도 종료한다.
- Partner 상태는 ACTIVE와 INACTIVE 두 가지다. 유효성은 ACTIVE이며 만료 전인지로 판단한다. 취소 시 INACTIVE가 되고, 기존 PENDING_PAYMENT·CANCELLED도 이전 SQL로 INACTIVE에 통합한다. 특정 학원의 개선 한도 소진은 구독 전체의 일반 분석 권한을 없애지 않는다.
- 일반 분석 무제한과 상품의 개선안 한도는 별개다.
- Pack·Partner 동시 보유 시 개선안 요청의 `improvementSource`를 회원이 선택한다. 선택한 이용권이 부족해도 다른 이용권으로 자동 대체하지 않는다. 하나만 보유한 기존 호출은 해당 이용권을 사용한다.
- 재평가는 기존 원 주문의 기한·횟수·소유권으로 판단하며 무료 한도와 별도다.

## API 변경

`GET /api/v1/geo/profile`은 `pricing.mode`, `partnerTier`, `availableImprovementSources`, `scanAllowance`로 이용권 정보를 제공한다. 유료 일반 분석은 `unlimited=true`, `monthlyLimit=null`, `remaining=null`이다. 기존 `plan` 응답 필드와 `legacyPlan()` 변환은 제거했다. 프론트도 `pricing`만 사용한다.

`POST /api/v1/geo/order`의 개선안 요청에 `improvementSource: "PACK" | "PARTNER"`를 추가했다. 동시 보유 중 선택을 생략하면 `409 IMPROVEMENT_SOURCE_REQUIRED`, FREE를 선택하면 `400 INVALID_IMPROVEMENT_SOURCE`다. 선택한 Pack이 없으면 `409 INSUFFICIENT_PACK_CREDITS`, Partner가 없으면 `403 PARTNER_SUBSCRIPTION_REQUIRED`, 등록 학원이 아니면 `403 ACADEMY_NOT_REGISTERED`, 학원별 개선 한도 소진은 `409 ACADEMY_PAGE_LIMIT_EXCEEDED`다. 무료 일반 분석 한도는 `429 FREE_SCAN_LIMIT_EXCEEDED`다.

## 검증과 남은 범위

`PricingServiceTest`는 월 경계, 유료 전환·종료, 상품 선택, 타 회원 이용권 격리, 재평가를 검증한다. `PricingPostgresIntegrationTest`는 실제 PostgreSQL에서 동시 접수, 주문·사용량 롤백, 기존 소유 관계·잔액 이전과 재실행을 검증한다. 해당 테스트는 운영 DB 대신 테스트 전용 DB에만 연결한다.

```powershell
$env:PRICING_TEST_DB_URL = 'jdbc:postgresql://127.0.0.1:55437/pricing_test'
.\gradlew.bat test --tests '*PricingPostgresIntegrationTest'
```

위 테스트는 `pricing_test` 사용자와 빈 비밀번호를 사용하는 임시 로컬 DB용이며 테스트 실행 시 매핑된 테이블을 생성·삭제한다. 운영 DB 주소를 넣지 않는다.

이번 변경은 기존 상품 모델의 접수 시 사용량 차감 방식을 사용한다. 결제 승인·멱등키 저장, Pack 크레딧 예약·성공 확정·최종 실패 반환 원장, Partner 학원별 월 사용량 자동 초기화는 이번 리팩터링에서 구현하지 않았다. 일반 무료 사용량의 월 초기화는 구현했다. 전체 결제·크레딧 명세의 예약·환불 계약을 구현 완료로 해석하지 않는다.
