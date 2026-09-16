# 알림 발송 채널 선정 (GEO 서비스)

조사일: 2026-09-16 · 환율 기준 1 USD = 1,390 KRW · 문자 단가는 VAT 별도가 표준이므로 비용 모델에서는 VAT 포함가(×1.1)로 환산 · 가격과 정책은 바뀌므로 각 표의 출처 링크로 재확인할 것.

**전제:** 분석을 동기 응답에서 비동기로 전환하고, AI 응답 처리와 리포트 저장이 모두 끝난 뒤에 주문 시 기입한 이메일·휴대폰으로 결과를 통지한다.

**관련 문서**
- 인프라 원가: [INFRA_CLOUD_SELECTION.md](INFRA_CLOUD_SELECTION.md) (2026-09-10)
- 상품·가격·원가: [PRICING_MODEL.md](PRICING_MODEL.md) (2026-09-15)
- 구현 흐름·기존 결함: [BACKEND_IMPLEMENTATION_FLOW.md](BACKEND_IMPLEMENTATION_FLOW.md)

---

## 0. 결론 (한 문단)

**이메일이 본채널이고, 문자는 결제 고객 대상 옵트인 보조채널이다.** 이메일은 AWS SES 기준 건당 0.14원으로 Pack 1건 변동원가(약 1원, [PRICING_MODEL.md](PRICING_MODEL.md) §4.1)의 1/7에 불과한 반면, SMS는 건당 8.8원으로 분석 원가의 **약 9배**다. 알림 하나가 분석 자체보다 비싸지는 구조라 "전 주문 문자 발송"은 상품 설계로 성립하지 않는다. 게다가 우리 첫 구매자는 에이전시 담당자(B2B)이고 산출물이 링크·HTML 리포트라, 45자짜리 SMS보다 이메일이 매체로도 맞다. 비용보다 먼저 막히는 건 **규제**다 — 문자는 발신번호 사전등록이 필수고(본인 명의 휴대폰은 본인인증으로 당일 가능), 알림톡은 카카오톡 채널 비즈니스 인증에 **사업자등록증**이 필요해 등록 전에는 선택지에 없다. 따라서 지금은 Brevo 무료(300건/일)로 0원에 시작해 도메인 확보 후 SES로 옮기고, 문자는 결제 고객이 생긴 뒤 옵트인으로 붙인다. 구현에서 가장 중요한 건 채널 선택이 아니라 **`notification_outbox` 테이블과 `UNIQUE(order_id, channel)`** 이다 — 재시도로 문자가 두 번 나가면 되돌릴 수 없다.

---

## 1. 왜 이 선택인가 — 원가 구조

### 1.1 알림 단가와 분석 원가의 비교

| 항목 | 건당 | 출처 |
|---|---|---|
| Pack 변동원가 (평가·생성 모두 로컬) | 약 1원 | [PRICING_MODEL.md](PRICING_MODEL.md) §4.1 |
| Pack 변동원가 (생성을 Gemini 유료로) | 약 2.6원 | 동일 |
| **이메일 (AWS SES)** | **0.14원** | $0.10 / 1,000건 |
| **SMS (Solapi 표준)** | **8.8원** (8원 + VAT) | Solapi 가격정책 |
| 알림톡 (Solapi 표준) | 14.3원 (13원 + VAT) | 동일 |

**이메일은 원가에 묻히고, 문자는 원가를 지배한다.** 전 주문에 SMS를 붙이면 Pack 1건 변동원가가 1원 → 9.8원으로 **약 10배**가 된다. GPU를 로컬로 돌려 건당 0.3원까지 깎아놓은 [INFRA_CLOUD_SELECTION.md](INFRA_CLOUD_SELECTION.md)의 최적화가 문자 한 통에 전부 상쇄된다.

다만 **매출 대비로는 감당 가능한 수준**이라는 점도 같이 봐야 한다. Partner 450곳 상단(월 9만 Pack)에서 전건 SMS를 보내도 월 79.2만원이고, 이는 그 시나리오 매출(2.2억원/월)의 0.36%다. 그러니 "문자는 비싸서 못 쓴다"가 아니라 **"이메일이 63배 싸면서 리포트 링크·HTML·첨부까지 실어 나르는데 45자 문자에 돈을 쓸 이유가 없다"** 가 정확한 판단 근거다. 문자의 값어치는 원가가 아니라 **도달률**에서 나온다 — 그래서 결제 고객에게만, 옵트인으로 붙인다.

### 1.2 알림 단위를 Pack이 아니라 주문으로 잡는다

비용을 두 자릿수 배로 가르는 건 단가가 아니라 **알림 단위**다. 파트너가 학원 20곳 × 10페이지를 한 번에 걸면 분석은 200건이지만 통지는 1건이면 충분하다.

| 알림 단위 | 월 알림 건수 (Partner 450곳 상단 기준) | 이메일 | SMS |
|---|---|---|---|
| Pack 1건마다 | 90,000건 | 12,600원 | 792,000원 |
| 학원 1곳마다 | 9,000건 | 1,260원 | 79,200원 |
| 파트너 배치 1회마다 | 450건 | 63원 | 3,960원 |

**현재 구조가 이미 맞다.** `AnalysisJob`은 `Order`와 1:1(`@OneToOne`, `unique = true`)이므로 주문 단위 통지가 자연스럽게 나온다. 단, 향후 Partner 상품에서 "학원 20곳 일괄 스캔"을 주문 N건으로 쪼개면 알림도 N건이 되므로, **그때는 배치 단위 묶음 통지(다이제스트)를 별도로 설계**해야 한다.

---

## 2. 이메일

### 2.1 필요 기술

| 항목 | 내용 | 비고 |
|---|---|---|
| 의존성 | `spring-boot-starter-mail` (`JavaMailSender`) | SES·Brevo·Gmail 모두 SMTP 인터페이스를 제공 → **host/port/계정만 바꾸면 벤더 교체**. 벤더 SDK를 넣으면 이 이점이 사라진다 |
| 템플릿 | `spring-boot-starter-thymeleaf` | HTML 메일. 문자열 concat은 유지보수 불가 |
| 도메인 인증 | **SPF + DKIM + DMARC** | 2024-02부터 Gmail·Yahoo가 대량 발송자에 DMARC 요구. 없으면 스팸함 직행 |
| 국내 수신율 | KISA 화이트도메인 등록 (무료) | 네이버·다음 수신율. `spam.kisa.or.kr` |
| SES 한정 | 샌드박스 해제 신청 | 초기에는 검증된 주소로만 발송 가능. 승인 보통 1영업일 |
| 반송 처리 | 바운스·스팸신고 웹훅 (SES는 SNS 연동) | **바운스율 5% 초과 시 계정 정지.** 오타 이메일을 방치하면 실제로 막힌다 |

기존 코드와의 정합: [AiRestClientConfig.java](../src/main/java/com/malgeum/geo/global/config/AiRestClientConfig.java)에서 이미 `RestClient`를 쓰고 있지만, 이메일은 REST가 아니라 SMTP로 붙이는 편이 벤더 교체가 쉽다.

### 2.2 제공자 비교

| 제공자 | 무료 한도 | 유료 단가 | 건당 | 판정 |
|---|---|---|---|---|
| **AWS SES (서울 `ap-northeast-2`)** | 신규 계정 3,000건/월 × 12개월 * | **$0.10 / 1,000건** | **0.14원** | **본선.** 압도적 최저가, 서울 리전 존재 |
| **Brevo Free** | **300건/일 (≈9,000건/월), 영구** | 이후 유료 플랜 | 0원 | **0단계 채택.** 자체 도메인 없어도 시작 가능 |
| Resend | 3,000건/월 (100건/일) | $20/월 50,000건 | 0.56원 | 개발자 경험 우수, 단가는 SES의 4배 |
| Gmail SMTP (개인) | 500건/일 | — | 0원 | From이 `gmail.com` → DMARC 실패. **데모용만** |
| Google Workspace SMTP | 2,000건/일 | $7.2/인·월 | 0원 | 자체 도메인 쓰면 DMARC는 통과. 트랜잭션 메일 전용 설계는 아님 |

\* 2025-07-15 이후 생성된 AWS 계정은 개별 서비스 무료 티어 없이 최대 $200 크레딧으로 대체된다 ([INFRA_CLOUD_SELECTION.md](INFRA_CLOUD_SELECTION.md) §2-2의 AWS 프리티어 개편과 동일 건).

### 2.3 숨은 비용

- **첨부파일 $0.12/GB** (SES) — 리포트 PDF를 첨부하면 과금된다. **링크로 보낼 것.** 마침 리포트는 이미 DB에 있고 프론트 상세 페이지가 있다.
- **전용 IP $24.95/월** — 이 규모에서는 불필요. 공유 IP로 충분하다.
- **문자와 달리 이메일은 잔액 개념이 없다** — 선불 충전 소진으로 조용히 실패하는 사고가 없다.

---

## 3. 문자 / 알림톡

### 3.1 규제 — 실제 관문

비용보다 여기서 먼저 막힌다.

1. **발신번호 사전등록제** — 인터넷 발송 문자는 사전 등록된 번호로만 나간다. (근거: 전기통신사업법 발신번호 변작 방지 조항 + 「거짓으로 표시된 전화번호로 인한 이용자 피해 예방 등에 관한 고시」)
   - 본인 명의 **휴대폰**: 휴대폰 본인인증으로 즉시 등록 → **개인도 당일 가능**
   - **유선번호·대표번호**: 고시 개정으로 서류가 강화되어 **통신서비스 이용증명원** 제출 + 심사 필요
2. **알림톡**: 카카오톡 채널 개설 → 비즈니스 인증(**사업자등록증**) → 템플릿 사전 검수(영업일 1~3일). **템플릿 문구를 바꿀 때마다 재검수**이므로 리포트 요약문처럼 가변 문구는 알림톡에 담기 어렵다.
3. **정보통신망법 제50조 (광고성 정보)** — 사전 동의 + `[광고]` 표기 + 21~08시 발송 금지 + 수신거부 방법 명시 의무.
   - **우리 케이스는 적용 제외다.** "고객이 직접 주문한 서비스의 처리 결과 통지"는 영리목적 광고성 정보의 예외에 해당한다. 알림톡이 허용하는 "회원가입·결제·배송·알림·고지·신청 등 수신자 액션 기반 정보성 메시지"와 정확히 같은 범주다.
   - **단, 같은 메시지에 "Partner 상품 안내" 한 줄만 끼워도 즉시 광고성이 된다.** 결과 통지와 마케팅은 템플릿 레벨에서 물리적으로 분리한다.

### 3.2 필요 기술

별도 SDK가 사실상 필요 없다. 이미 `RestClient`를 쓰고 있으므로 같은 방식으로 REST 호출한다.

| 제공자 | 연동 방식 | 비고 |
|---|---|---|
| **Solapi** | 자바 SDK (`net.nurigo:sdk`) 또는 REST | 가장 단순. 단가도 최저 |
| NCP SENS | REST + **HMAC-SHA256 시그니처 직접 구현** (~30줄) | 네이버 크레딧(그린하우스 등)이 있으면 고려 |
| Twilio / AWS SNS | REST | 한국 수신 건당 60~70원대. 국내 대행사의 **8배** + 발신번호 등록 이슈는 그대로 → **탈락** |

### 3.3 단가 비교 (Solapi 표준, VAT 별도)

| 유형 | 단가 | VAT 포함 | 길이 | 용도 |
|---|---|---|---|---|
| **SMS** | **8원** | 8.8원 | 90 byte = 한글 45자 | "분석 완료 + 단축링크" |
| LMS | 14원 | 15.4원 | 2,000 byte | 요약 포함 시 |
| MMS | 22원 | 24.2원 | 이미지 | 불필요 |
| **알림톡** | **13원** (1만건↑ 10원 / 10만건↑ 8원) | 14.3원 | 1,000자 + 버튼 | 사업자등록 후 |
| 친구톡 | 29원 | 31.9원 | 광고 가능 | 마케팅 전용, 별건 |

⚠️ **알림톡이 SMS보다 비싸다.** "알림톡이 제일 싸다"는 통념은 이 단가표에서 성립하지 않는다 — 월 10만건 구간(8원)에서야 SMS와 같아진다. **"완료됐습니다 + 링크" 한 줄이면 SMS(45자)가 더 싸고 사업자등록도 필요 없다.** 알림톡의 값어치는 단가가 아니라 브랜드 노출·버튼 UI·도달률이며, 그건 결제 고객이 생긴 뒤의 문제다.

### 3.4 무료 Scan에 문자를 붙이면 안 되는 이유

[PRICING_MODEL.md](PRICING_MODEL.md) §6.2의 무료 Scan은 "누구나, 리드 획득 목적"이다. 여기에 문자를 붙이면:

- 인증 없이 아무 번호나 입력받아 발송 → **문자 폭탄 도구가 된다**
- 국내 대행사는 **선불 충전제**라 잔액이 하룻밤에 소진된다 (이메일에는 없는 실패 모드)
- 발신번호가 신고되면 **등록 자체가 정지**되어 유료 고객 알림까지 같이 죽는다

→ **무료 Scan은 이메일만. 문자는 결제 고객 + 본인인증된 번호로 제한.** 이건 비용 최적화가 아니라 보안 요구사항이다.

---

## 4. 비용 모델 (월, KRW)

전제: 알림 1건 = 주문 1건, 이메일 SES 0.14원, SMS 8.8원(VAT 포함), 알림톡은 구간 단가 × 1.1.

| 월 알림 건수 | 이메일만 | 이메일 + SMS 전건 | 이메일 + 알림톡 전건 | 대응 단계 |
|---|---|---|---|---|
| 1,000 | **140원** | 8,940원 | 14,440원 | 0단계 (무료 티어 내, 실질 0원) |
| 10,000 | **1,400원** | 89,400원 | 111,400원 | 1단계 |
| 100,000 | **14,000원** | 894,000원 | 894,000원 | Partner 시장 상단 초과 |

**비교 기준:** [INFRA_CLOUD_SELECTION.md](INFRA_CLOUD_SELECTION.md) §3의 인프라 총비용은 1천 주문/일에서 약 1만원, 1만 주문/일에서 약 2.5만원(LoRA 전환 시)이다. **이메일은 이 숫자를 전혀 흔들지 않고(0.1~5%), SMS 전건 발송은 인프라비를 3~4배로 만든다.**

---

## 5. 단계별 도입안

| 단계 | 트리거 | 조치 | 월 비용 |
|---|---|---|---|
| **0단계 (지금)** | 도메인·사업자등록 전 | Brevo Free(300건/일) + `spring-boot-starter-mail` + Thymeleaf. **`notification_outbox`부터 만든다** | **0원** |
| **1단계** | 자체 도메인 확보 | AWS SES 서울 + DKIM/SPF/DMARC + KISA 화이트도메인. Brevo에서 **SMTP 설정만 교체**(코드 무변경) | ~1,400원 |
| 2단계 | 첫 결제 고객 발생 | SMS 옵트인 추가(Solapi, 본인 명의 번호 사전등록). 본문은 "분석 완료 + 단축링크" 45자 | ~9만원 |
| 3단계 | 사업자등록 완료 **그리고** 월 10만건 초과 | 알림톡 전환 검토. 그 전에는 SMS가 더 싸다 | — |

**0단계에서 outbox를 먼저 만드는 이유:** 채널은 나중에 바꿔도 코드가 거의 안 변하지만, 발송 이력·재시도·중복방지 구조가 없으면 나중에 문자를 붙일 때 **중복 발송 사고가 돈으로 직결**된다.

---

## 6. 구현 설계

### 6.1 훅 포인트

[GeoAsyncWorker.java](../src/main/java/com/malgeum/geo/service/GeoAsyncWorker.java)의 `processNextJob()` 안, `markSucceeded` / `markFailureOrRetry` 직후가 논리적 훅 포인트다. **다만 그 자리에서 직접 발송 API를 호출하면 안 된다.**

### 6.2 왜 인라인 발송이면 안 되나

1. `pollAndProcess()`가 `while (processNextJob())` 루프로 큐를 연속 소진한다 → 외부 발송 API가 느리거나 죽으면 **분석 큐 전체가 막힌다**
2. 발송 실패 시 재시도 근거가 될 상태가 남지 않는다
3. 트랜잭션이 롤백돼도 **이미 나간 문자는 되돌릴 수 없다** (이메일도 마찬가지)

### 6.3 `notification_outbox` 설계

기존 `AnalysisJob` 패턴(`PENDING`/`RUNNING`/`RETRY_WAIT`/`SUCCEEDED`/`FAILED` + `findNextJobForUpdate` + `attempts`/`maxAttempts`/`nextRunAt`)을 **그대로 복제**한다. 새 개념을 만들 필요가 없다.

```
notification_outbox
  id                  BIGSERIAL
  order_id            BIGINT       NOT NULL
  channel             VARCHAR(20)  NOT NULL   -- EMAIL | SMS
  template_code       VARCHAR(50)  NOT NULL   -- ANALYSIS_SUCCEEDED | ANALYSIS_FAILED
  recipient           VARCHAR(255) NOT NULL
  payload             JSONB
  status              VARCHAR(30)  NOT NULL
  attempts            INT NOT NULL DEFAULT 0
  max_attempts        INT NOT NULL DEFAULT 3
  next_run_at         TIMESTAMP NOT NULL
  locked_at           TIMESTAMP
  provider_message_id VARCHAR(255)            -- 발송사 추적 ID, 사후 조회용
  error_message       TEXT
  created_at / modified_at                    -- BaseTimeEntity

  UNIQUE (order_id, channel)
```

**`UNIQUE(order_id, channel)`이 이 설계의 핵심이다.** 워커 재기동·중복 클레임·수동 재처리 어느 경로로도 같은 주문에 같은 채널 알림이 두 번 나가지 않는다. 문자는 중복 발송이 곧 비용이자 클레임이다.

**적재 시점:** [AnalysisExecutionService.java](../src/main/java/com/malgeum/geo/domain/domain/analysisjob/service/AnalysisExecutionService.java)의 `saveAnalysisReport()` **같은 트랜잭션 안에서** outbox row를 INSERT한다. 리포트 저장과 알림 예약이 원자적으로 묶여, "리포트는 있는데 알림이 안 갔다" / "알림은 갔는데 리포트가 없다"가 둘 다 불가능해진다. `@TransactionalEventListener(AFTER_COMMIT)`보다 이쪽이 단순하고 안전하다.

**소비:** 별도 `@Scheduled` 폴러가 `FOR UPDATE SKIP LOCKED`로 클레임해 발송한다. `AnalysisJobRepository.findNextJobForUpdate()` 쿼리를 그대로 베끼면 된다.

**실패 경로도 통지 대상이다.** `markFailureOrRetry`가 최종 `FAILED`로 떨어질 때 `ANALYSIS_FAILED` 템플릿을 outbox에 넣는다. 지금 구조에서는 분석이 3회 재시도 후 죽으면 **사용자가 영원히 기다린다.** 비동기 전환에서 이게 가장 눈에 띄는 UX 결함이 된다.

### 6.4 선행 조건 (코드)

| # | 항목 | 근거 |
|---|---|---|
| 1 | **`@Scheduled` 스레드 풀 크기 상향** — `spring.task.scheduling.pool-size: 2` 이상 | **현재 `application.yaml`에 설정이 없어 기본 풀이 스레드 1개다.** 알림 폴러를 그냥 추가하면 `pollAndProcess()`의 `while` 루프와 **같은 스레드를 두고 경합해 서로 막힌다.** 붙이기 전에 반드시 먼저 조치 |
| 2 | **`jobId`/`orderId` 혼용 수정** | `processNextJob()`이 `markSucceeded(jobId)`를 호출하는데 `AnalysisJobService.markSucceeded(Long orderId)`는 `findByOrderId`로 조회한다. [BACKEND_IMPLEMENTATION_FLOW.md](BACKEND_IMPLEMENTATION_FLOW.md) §6.3에 이미 기록된 결함이며, **알림 훅을 같은 자리에 붙이면 같은 버그를 그대로 상속한다** |
| 3 | **수신자 검증·폴백** | `Order.contactEmail`(255) / `Order.contactPhone`(50) 모두 nullable이고 형식 검증이 없다. 발송 전 형식 검증 + null이면 `Client.getEmail()` 폴백. 검증 실패는 발송 시도 없이 즉시 `FAILED`로 종결(재시도 무의미) |
| 4 | **Flyway 도입** | `ddl-auto: update`로 outbox 같은 운영 테이블을 만들면 되돌릴 수 없다. [INFRA_CLOUD_SELECTION.md](INFRA_CLOUD_SELECTION.md) §5에 이미 배포 전 필수로 올라가 있는 항목 |
| 5 | 자격증명 환경변수화 | SMTP 비밀번호·문자 API 키를 `.env`/환경변수로. 기존 `spring.config.import: optional:file:.env` 패턴 그대로 |

### 6.5 설정 키 (기존 `application.yaml` 컨벤션 유지)

```yaml
spring:
  task:
    scheduling:
      pool-size: 2          # 분석 폴러 + 알림 폴러 (선행조건 #1)
  mail:
    host: ${MAIL_HOST}
    port: ${MAIL_PORT:587}
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true

notification:
  poll-delay-ms: ${NOTIFICATION_POLL_DELAY_MS:2000}
  from-address: ${NOTIFICATION_FROM_ADDRESS}
  from-name: ${NOTIFICATION_FROM_NAME:말금 GEO}
  email:
    enabled: ${NOTIFICATION_EMAIL_ENABLED:true}
  sms:
    enabled: ${NOTIFICATION_SMS_ENABLED:false}   # 2단계까지 off
    api-key: ${SOLAPI_API_KEY:}
    api-secret: ${SOLAPI_API_SECRET:}
    sender: ${SOLAPI_SENDER:}                    # 사전등록된 발신번호
```

`geo.scraping.*`, `ai.server.base-url`과 동일한 네임스페이스 패턴이며, `analysis.job.poll-delay-ms`와 짝을 이루도록 `notification.poll-delay-ms`로 둔다.

---

## 7. 성장 트리거 요약

| 신호 | 조치 | 비용 변화 |
|---|---|---|
| 비동기 전환 완료 | outbox + 이메일 발송기 (Brevo Free) | 0원 |
| Brevo 300건/일 초과 | SES 전환 (SMTP 설정만 교체) | ~1,400원/월 |
| 바운스율 3% 접근 | 바운스 웹훅 수신 → 무효 주소 차단 목록 | 0원 (**방치 시 계정 정지**) |
| 첫 결제 고객 | 발신번호 사전등록 → SMS 옵트인 | ~9만원/월 |
| 사업자등록 + 월 10만건 | 알림톡 검토 | SMS와 동등 |
| Partner 일괄 스캔 출시 | 주문 N건 → **배치 다이제스트 1건**으로 묶음 | 알림 건수 10~200배 감소 |

---

## 8. 미확정 / 확인 필요

- **발신번호 사전등록 근거 조항 번호** — 자료마다 전기통신사업법 제84조 / 제84조의2로 엇갈린다. 법제처에서 확인할 것. 제도 존재와 서류 요건(통신서비스 이용증명원) 자체는 확정.
- **알림톡 비즈니스 인증의 사업자등록증 필수 여부** — 카카오 비즈니스 채널 인증 요건에서 직접 확인 필요. 검색된 가이드 문서에는 명시적 문구가 없었다.
- **NCP SENS 정확한 단가** — 공식 상품 페이지에 요금표가 노출되지 않는다. 네이버 크레딧을 실제로 확보하면 요금계산기로 재확인.
- **Solapi 구간 할인 적용 기준** — "최근 3개월 평균 발송량"이라 신규 계정은 표준 단가부터 시작한다. 월 1만건 도달 전까지는 13원(알림톡)·8원(SMS) 기준으로 계산할 것.
- **SES 서울 리전 샌드박스 해제 소요** — 통상 1영업일이나 계정별 편차 존재. **1단계 진입 전 미리 신청**해 둘 것.

---

## 9. 출처

**이메일**
- Amazon SES 가격($0.10/1,000건, 첨부 $0.12/GB, 전용 IP $24.95): https://smtpedia.com/amazon-aws-ses-pricing/
- SES 무료 티어 현황(신규 3,000건/월 12개월, 2025-07-15 이후 계정 제외): https://www.saaspricepulse.com/blog/amazon-ses-pricing-per-1000-emails-2026
- Brevo 무료 플랜(300건/일, 트랜잭션 포함): https://www.emailsoftwareinsights.com/reviews/brevo/pricing/free-plan/
- Brevo 가격 정책: https://help.brevo.com/hc/en-us/articles/208589409-About-Brevo-s-pricing-plans
- KISA 화이트도메인: https://spam.kisa.or.kr

**문자·알림톡**
- Solapi 가격 정책(SMS 8원 / LMS 14원 / MMS 22원 / 알림톡 13원 / 친구톡 29원, VAT 별도): https://solapi.com/pricing
- Solapi 발송 구간별 할인 단가표(알림톡 1만건↑ 10원, 10만건↑ 8원): https://solapi.com/guides/tiered-pricing/
- Solapi 문자 발송 가이드(발신번호 사전등록): https://solapi.com/guides/sms-howtosend
- 발신번호 사전등록 제도·통신서비스 이용증명원: https://www.smsko.co.kr/info_law/law_callback.html
- 발신번호 추가등록 서류 강화: http://help.bizmailer.co.kr/?p=578
- 카카오 알림톡 가이드(정보성 메시지 한정, 템플릿 검수): https://solapi.com/guides/kakao-ata-guide
- 알림톡 템플릿 검수 가이드(정보통신망법 광고성 정보 예외): https://static.godo.co.kr/download/echost/alimtalk_template_guide_171027.pdf
- NAVER Cloud SENS: https://www.ncloud.com/product/applicationService/sens (요금은 https://www.ncloud.com/charge/calc/ko 에서 재확인)

**내부 문서**
- 인프라 원가·환율 기준: [INFRA_CLOUD_SELECTION.md](INFRA_CLOUD_SELECTION.md)
- Pack 변동원가·Partner 처리량·매출 시나리오: [PRICING_MODEL.md](PRICING_MODEL.md)
- `jobId`/`orderId` 혼용 결함: [BACKEND_IMPLEMENTATION_FLOW.md](BACKEND_IMPLEMENTATION_FLOW.md) §6.3
