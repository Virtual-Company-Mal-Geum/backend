# AI 서버 입력 계약 정합화 구현 계획

- 작성일: 2026-08-21
- 대상: Spring 백엔드의 `URL -> 크롤링 -> AI 입력 생성 -> /evaluate 호출` 흐름
- 기준 AI 서버: `../ai-server/geo_gateway.py`
- 상태: 구현 전 계획

## 1. 목표

Spring 백엔드가 AI 서버에 아래 4개 필드만 보내고, `html_text`를 학습·서빙 형식과 같은 라벨 구조로 생성하도록 정합화한다.

```json
{
  "url": "https://example.com/page",
  "domain": "education",
  "html_text": "[DOCUMENT_TYPE]\n\nEDUCATION_ARTICLE\n\n[TITLE]\n\n제목\n\n[MAIN_CONTENT]\n\n본문...",
  "json_ld": []
}
```

완료 후에는 다음이 보장되어야 한다.

- 요청 키는 `url`, `domain`, `html_text`, `json_ld` 네 개뿐이다.
- wire 요청에는 내부 프롬프트용 `category`와 폐기된 `meta_tags`를 넣지 않는다.
- `html_text`는 raw HTML이나 라벨 없는 Markdown이 아니다.
- `[DOCUMENT_TYPE]`이 항상 존재하고, 채점 요청에는 비어 있지 않은 `[MAIN_CONTENT]`가 존재한다.
- JSON-LD가 없거나 일부 스크립트가 잘못돼도 `json_ld`는 유효한 JSON 배열이다.
- 운영 요청에는 `news`, `ecommerce`, `education`만 사용한다.
- `insufficient_content`와 `success + content_warning`을 서로 다르게 처리한다.

## 2. 기준 소스와 적용 우선순위

계약 판단은 아래 순서를 따른다.

1. 실제 서빙 코드: `../ai-server/geo_gateway.py`
2. 현재 API 문서: `../ai-server/README.md`, `../ai-server/HandOff.md`, `../ai-server/md/GEO_api_spec.md`
3. 입력 생성 기준: `../ai-server/src/remake_html/ground_truth_builder/gateway_input.py`
4. 분류·포맷 기준:
   - `../ai-server/src/remake_html/ground_truth_builder/classification/document_type.py`
   - `../ai-server/src/remake_html/ground_truth_builder/formatting/html_text.py`
   - `../ai-server/src/remake_html/ground_truth_builder/formatting/templates.py`

다음 자료는 참고용일 뿐 골든 출력으로 간주하지 않는다.

- `C:/Users/user/Downloads/html_parsing/test_gateway_inputs.json`
- `../ai-server/src/remake_html/ver4/test_gateway_inputs.json`
- 버전 보관 폴더의 `vllm_fastapi.py`

이유: 저장된 예시는 현재 boilerplate 제거·포맷터 동작과 달라질 수 있고, 구 서버에는 폐기된 `meta_tags` 계약이 남아 있다.

fixture를 만들기 전에 AI 서버 포맷터의 FAQ 중복 처리 의도를 확정해야 한다. 현재
`formatting/html_text.py`는 `main_content`에 이미 포함된 FAQ를 건너뛴 뒤 `used`에 기록하지 않아
후속 반복문에서 `[FAQ]`를 다시 붙일 수 있고, `tests/test_preservation.py`의 비중복 기대와 충돌한다.
Spring이 우연히 이 버그까지 복제하지 않도록 AI 서버에서 의도 동작을 먼저 결정하고 기대값을 고정한다.

## 3. 현재 상태와 차이

| 영역 | 현재 Spring | 현재 AI 계약 | 판정 |
|---|---|---|---|
| 요청 필드 | 4필드 DTO | 4필드 | 외형 일치 |
| `html_text` | `article/main/body`를 일반 Markdown으로 변환 | 라벨이 있는 정제 텍스트 | **차단 이슈** |
| JSON-LD | 모든 스크립트를 문자열로 합친 뒤 한 번에 파싱, 실패 시 `null` 가능 | 스크립트별 파싱, 없으면 `[]` | **수정 필요** |
| 도메인 | `TECHBLOG` 선택 가능 | 3개만 지원, `tech_blog`는 400 | **수정 필요** |
| 정적/동적 선택 | Markdown이 비어 있지 않으면 Jsoup 결과 채택 | 렌더된 DOM 기준 파이프라인 | 품질 검증 필요 |
| 성공 응답 | `result`를 받지만 worker가 `detail` 중심으로 처리 | `result`가 평가 본체 | **통합 수정 필요** |
| 경고 응답 | 선택 필드 미수용 | `content_warning`, `main_content_length` 가능 | 수정 필요 |
| 테스트 | 이전 응답 DTO를 참조하여 컴파일 실패 | 최신 구조 필요 | **선행 수정** |

현재 `html_text`에는 `[MAIN_CONTENT]`가 없으므로, AI 서버가 본문 길이를 0으로 계산하여 `insufficient_content`로 차단할 수 있다. 이것이 첫 번째로 해결할 문제다.

## 4. 구현 원칙

- 새 마이크로서비스를 만들지 않는다.
- 우선 기존 `GeoScrapingService`의 공통 `Document -> ScrapedData` 경로를 수정한다.
- Jsoup와 Playwright는 모두 같은 포맷터를 통과시킨다.
- Playwright는 렌더러이고 Markdown 변환기는 아니므로, 렌더 후에도 Jsoup/Flexmark 변환을 사용한다.
- Python의 Trafilatura/Readability와 Java 추출기의 byte 단위 동일성은 주장하지 않는다. 고정 fixture로 계약과 정보 보존을 검증한다.
- JSON-LD에서 읽은 값을 `html_text`의 제목·본문에 주입하지 않는다. `html_text`는 렌더된 HTML에서 복구한 정보만 담는다.
- 기존 사용자 변경 파일인 `GeoAsyncWorker.java`, `application.yaml`, `.gitignore`는 구현 시 덮어쓰지 않는다.

## 5. 작업 순서

### 0단계. 현재 테스트 기준선 복구

수정 대상:

- `src/test/java/com/malgeum/geo/service/GeoAIServiceTest.java`
- `src/test/java/com/malgeum/geo/service/GeoAsyncWorkerQueueIntegrationTest.java`

작업:

- [ ] `GeoAIServiceTest`의 폐기된 `content()` 검증을 `result()` 구조 검증으로 바꾼다.
- [ ] mock 응답을 문자열 `result`가 아니라 JSON 객체 `result`로 바꾼다.
- [ ] queue 통합 테스트의 구형 3인자 `GeoEvaluationResponse` 생성을 현재 생성자에 맞춘다.
- [ ] 새 기능을 추가하기 전에 `compileTestJava`가 통과하는지 확인한다.

현재 검증 결과:

- `compileJava`: 통과
- `compileTestJava`: 실패
- 실패 원인: 위 두 테스트가 이전 응답 DTO를 사용함

완료 조건:

- 신규 입력 포맷 변경 전 상태에서 테스트 소스 전체가 컴파일된다.

### 1단계. 고정 계약 fixture 확정

추가 대상:

- `src/test/resources/geo/gateway-input/static-page.html`
- `src/test/resources/geo/gateway-input/expected-request.json`

fixture에 반드시 포함할 요소:

- `<title>` 및 Open Graph 제목
- `article` 내부의 `h1/h2`, 문단, 순서/비순서 목록, 표, `pre/code`
- 유효한 JSON-LD 객체 한 개
- 유효한 JSON-LD 배열 한 개
- 잘못된 JSON-LD 스크립트 한 개
- 페이지 공통 `nav/footer/script/style`

작업:

- [ ] 동일한 고정 HTML을 AI 서버의 현재 분류·포맷 규칙에 통과시켜 기대 JSON을 만든다.
- [ ] FAQ 등 전문 섹션의 중복 여부를 AI 서버에서 먼저 확정한다.
- [ ] 기대 JSON에는 정확히 4개 키만 둔다.
- [ ] 기존 실사이트 기반 예시 JSON은 구조 참고용으로만 사용한다.
- [ ] LF(`\n`)와 섹션 사이 빈 줄 두 개를 계약에 포함한다.

완료 조건:

- fixture 하나만으로 문서 유형, 제목, 본문의 필수 정보, JSON-LD 배열 형태를 결정론적으로 확인할 수 있다.

### 2단계. `html_text` 라벨 포맷 구현

주 수정 대상:

- `src/main/java/com/malgeum/geo/service/GeoScrapingService.java`

최소 구현:

1. 렌더된 `Document`에서 메타데이터를 추출한다.
   - 제목: `og:title` -> `twitter:title` -> `<title>` 순서
   - 선택 필드: author, published, publisher
2. 문서 유형을 분류한다.
   - JSON-LD `@type` 우선
   - fallback: `news -> NEWS_ARTICLE`, `ecommerce -> PRODUCT`
   - education: 교육기관 schema/키워드 -> `EDUCATIONAL_ORGANIZATION`, 커리큘럼 신호 -> `COURSE`, 나머지 -> `EDUCATION_ARTICLE`
3. 현재 `article -> main -> body` 선택과 Flexmark 변환을 이용해 본문 Markdown을 만든다.
4. 비어 있지 않은 섹션만 아래 순서로 조립한다.
   - `[DOCUMENT_TYPE]`
   - `[TITLE]`
   - `[AUTHOR]`
   - `[PUBLISHED]`
   - `[PUBLISHER]`
   - `[MAIN_CONTENT]`(비어 있으면 채점 요청을 만들지 않고 크롤링 실패로 분류)
5. 모든 구분은 `\n\n`으로 고정하고, 마지막 결과만 `strip()`에 해당하는 정리를 한다.

초기 구현에서는 새 인터페이스·factory·추출 서비스 계층을 만들지 않는다. 포맷 로직이 다른 소비자에게 재사용되거나 `GeoScrapingService`가 감당하기 어려워질 때만 별도 클래스로 추출한다.

AI 서버의 본문 정본은 Trafilatura/Readability 결과이므로 “완전한 GFM 보존”은 현재 계약이 아니다.
라벨·순서·줄바꿈은 exact parity 대상으로 삼고, 본문은 fixture에서 필수 정보가 남는지를 semantic parity로 검증한다.

완료 조건:

- 정적 Jsoup 경로와 Playwright 경로가 동일한 라벨 포맷을 반환한다.
- fixture의 필수 제목·문단·목록 항목·표 셀·코드 내용이 `[MAIN_CONTENT]` 안에 의미상 보존된다.
- raw HTML 태그와 script/style/nav/footer 위주 텍스트는 결과에 남지 않는다.
- `[MAIN_CONTENT]` 누락 상태로 요청을 만들 수 없다.

### 3단계. JSON-LD 수집을 배열 계약으로 수정

수정 대상:

- `src/main/java/com/malgeum/geo/service/GeoScrapingService.java`
- `src/main/java/com/malgeum/geo/dto/ScrapedData.java`

작업:

- [ ] `script[type=application/ld+json]`을 각각 독립적으로 파싱한다.
- [ ] 객체는 배열에 추가하고, 배열은 객체 항목만 평탄화한다.
- [ ] 깨진 스크립트 하나 때문에 정상 스크립트까지 버리지 않는다.
- [ ] 결과가 없으면 `NullNode`가 아니라 빈 `ArrayNode`를 사용한다.
- [ ] `GeoEvaluationRequest` 직렬화 결과에서 `json_ld`가 문자열 `"[...]"`이 아닌 실제 JSON 배열인지 검증한다.

완료 조건:

- 없음, 일부 오류, 여러 객체, 중첩 `@graph` 입력에서 요청 JSON 자체가 항상 유효하다.
- `json_ld: null`과 문자열형 JSON-LD가 전송되지 않는다.

### 4단계. 도메인 경계를 운영 계약에 맞춤

검토 대상:

- `src/main/java/com/malgeum/geo/domain/domain/order/entity/Order.java`
- `src/main/java/com/malgeum/geo/domain/domain/order/dto/GeoOrderRequest.java`
- `src/main/java/com/malgeum/geo/dto/GeoEvaluationRequest.java`

작업:

- [ ] 신규 요청에서 `TECHBLOG`를 선택하거나 AI 서버로 보내지 못하게 한다.
- [ ] 기존 DB 값과의 호환 때문에 enum을 즉시 삭제하지는 않는다.
- [ ] AI 요청 생성 직전에 허용 도메인 3개를 한 번 더 검증한다.
- [ ] 대문자 enum 이름이 아니라 현재 AI 라우팅 키 소문자가 직렬화되는지 테스트한다.

완료 조건:

- `news`, `ecommerce`, `education` 외 값은 크롤링/AI 호출 전에 명확한 애플리케이션 오류로 종료된다.

### 5단계. Jsoup 우선 경로의 채택 기준 보강

수정 대상:

- `src/main/java/com/malgeum/geo/service/GeoScrapingService.java`
- `src/test/java/com/malgeum/geo/service/GeoScrapingServiceTest.java`

작업:

- [ ] 현재의 단순 `isBlank()` 판정을 제거한다.
- [ ] `[MAIN_CONTENT]` 실제 글자 수와 `Loading...`, 빈 app root 같은 shell 신호로 정적 결과의 충분성을 판단한다.
- [ ] 정적 결과가 채택 기준에 미달할 때만 Playwright를 사용한다.
- [ ] `networkidle` 실패는 하드 실패로 만들지 않는다.
- [ ] 고정 동적 HTML fixture로 브라우저 렌더 후 텍스트가 들어오는지 검증한다.

초기 기준은 AI 서버의 하드 차단선인 `[MAIN_CONTENT] < 50자`와 명백한 shell 문자열로 시작한다. 300자 기준은 AI 서버에서 경고를 붙이는 품질 기준이지 무조건 재크롤해야 하는 기준이 아니므로 혼동하지 않는다.

AI 서버의 canonical 입력 생성기는 Playwright 렌더 DOM을 사용한다. Jsoup 우선 경로는 Spring의 성능 최적화이므로,
동일 fixture에서 렌더 경로와 의미상 동등하다는 근거가 있을 때만 유지한다. 근거가 없으면 정확성을 위해 Playwright 경로를 기준으로 삼는다.

완료 조건:

- `Loading...`만 있는 정적 shell은 Playwright로 넘어간다.
- 충분한 서버 렌더 HTML은 브라우저를 띄우지 않는다.
- 라이브 URL 성공 여부가 아니라 로컬 fixture가 회귀 테스트의 합격 기준이다.

### 6단계. 응답과 worker 처리 정합화

수정 대상:

- `src/main/java/com/malgeum/geo/dto/GeoEvaluationResponse.java`
- `src/main/java/com/malgeum/geo/service/GeoAsyncWorker.java`
- `src/main/java/com/malgeum/geo/service/GeoAiService.java`

작업:

- [ ] `result`를 평가 결과 본체로 저장·전달한다.
- [ ] 선택 응답인 `content_warning`, `main_content_length`, `cjk_normalized`, `jsonld_input`을 null 허용으로 수용한다.
- [ ] `success + content_warning`은 성공으로 처리하고 품질 경고만 기록한다.
- [ ] `insufficient_content`일 때만 재크롤 또는 사용자 오류 정책을 적용한다.
- [ ] `parse_error`, HTTP 400, 422, 429, 5xx를 같은 `blank-text` 오류로 뭉개지 않고 구분한다.
- [ ] `/evaluate`를 기본 경로로 유지한다. Gemini를 호출하는 `/diagnose` 전환은 별도 요구가 있을 때만 한다.

`GeoAsyncWorker.java`에는 현재 사용자 변경이 있으므로 구현 시 해당 diff를 기준으로 병합하고 덮어쓰지 않는다.

완료 조건:

- AI 서버의 구조화 `result`가 유실되지 않는다.
- 경고가 있는 정상 응답과 채점 불가 응답이 구분된다.

### 7단계. 계약·회귀 테스트

필수 테스트:

- [ ] 요청 직렬화 결과의 키가 정확히 4개인지 검사
- [ ] `meta_tags`가 없는지 검사
- [ ] `html_text`의 라벨 순서와 LF 구분 검사
- [ ] fixture의 제목/문단/목록 항목/표 셀/코드 내용이 의미상 보존되는지 검사
- [ ] JSON-LD 개별 파싱 및 빈 배열 fallback 검사
- [ ] `TECHBLOG` 요청 차단 검사
- [ ] 정적 페이지 채택 검사
- [ ] SPA shell의 Playwright fallback 검사
- [ ] `success + content_warning` 응답 파싱 검사
- [ ] `insufficient_content` 응답 파싱 검사
- [ ] `[MAIN_CONTENT]` 길이 0/49/50/299/300/301 경계에서 hard block과 warning 처리 구분 검사
- [ ] 외부 URL 없이 전체 테스트가 재현되는지 검사

실행 순서:

```powershell
.\gradlew.bat test --tests "com.malgeum.geo.service.GeoScrapingServiceTest"
.\gradlew.bat test --tests "com.malgeum.geo.service.GeoAIServiceTest"
.\gradlew.bat test --tests "com.malgeum.geo.service.GeoAsyncWorkerQueueIntegrationTest"
.\gradlew.bat test
```

실제 AI 서버 smoke test는 위 테스트 통과 후 별도로 수행한다. 서버·LoRA·네트워크가 필요한 결과는 `EXTERNAL_UNVERIFIED`로 구분하고 로컬 테스트 통과와 섞지 않는다.

## 6. 구현 파일 예상 목록

최소 변경 파일:

- `src/main/java/com/malgeum/geo/service/GeoScrapingService.java`
- `src/main/java/com/malgeum/geo/dto/ScrapedData.java`
- `src/main/java/com/malgeum/geo/dto/GeoEvaluationResponse.java`
- `src/main/java/com/malgeum/geo/service/GeoAsyncWorker.java`
- `src/main/java/com/malgeum/geo/domain/domain/order/dto/GeoOrderRequest.java`
- 관련 GEO 테스트 3개
- 고정 HTML/JSON fixture 2개

가능하면 변경하지 않을 파일:

- `build.gradle.kts`: 현재 Jsoup, Playwright, Flexmark, Jackson으로 첫 구현이 가능하다.
- `GeoEvaluationRequest.java`: 현재 4필드 외형을 유지하고 검증만 필요 시 보강한다.
- `GeoAiService.java`: 엔드포인트와 timeout 설정이 이미 요구를 충족하면 오류 매핑 외에는 건드리지 않는다.

## 7. 완료 정의

다음 항목을 모두 만족해야 작업 완료로 본다.

- [ ] Spring 요청 JSON이 AI 서버의 4필드 계약과 일치한다.
- [ ] `[DOCUMENT_TYPE]`이 항상 생성되고, 채점 요청에는 비어 있지 않은 `[MAIN_CONTENT]`가 생성된다.
- [ ] 고정 fixture의 구조와 핵심 텍스트가 보존된다.
- [ ] JSON-LD가 실제 배열로 전송된다.
- [ ] 미지원 도메인이 AI 서버 호출 전에 차단된다.
- [ ] 최신 success/warning/insufficient 응답을 모두 역직렬화한다.
- [ ] `compileTestJava`와 전체 테스트가 통과한다.
- [ ] 기존 사용자 변경을 보존한 diff 검토가 끝난다.
- [ ] 실제 AI 서버 `/evaluate` smoke test 결과가 별도로 기록된다.

## 8. 의도적으로 보류할 항목

- Python의 Trafilatura + readability-lxml 알고리즘을 Java로 그대로 복제
- Crawl4AI 도입
- OCR 또는 이미지 본문 추출
- 새 크롤링 마이크로서비스
- Playwright 브라우저 풀·다중 동시성 최적화
- `/diagnose`를 운영 기본 경로로 전환
- 근거 없이 임의의 3,500자/5,000자 `substring` 적용

위 항목은 fixture에서 실제 품질 부족이나 운영 병목이 측정된 뒤 별도 작업으로 연다. 길이 제한이 필요해지면 전체 문자열을 자르지 말고 라벨 구조를 유지한 채 `[MAIN_CONTENT]`만 제한한다.

## 9. 알려진 위험과 확인 필요 사항

1. **추출 동등성은 아직 확정되지 않음**  
   Jsoup/Flexmark는 Python의 Trafilatura/Readability와 알고리즘이 다르다. 구조 계약은 맞출 수 있지만 동일 본문을 보장하려면 도메인별 fixture 비교가 필요하다.

2. **기존 예시 파일은 현재 golden이 아님**  
   과거 예시에는 현재 정제기가 제거하는 UI/boilerplate가 포함돼 있을 수 있다.

3. **SSRF 방어는 별도 출시 게이트가 필요함**  
   외부 URL을 서버에서 가져오므로 http(s) 절대 URL, 사용자 정보 포함 URL, localhost/private/link-local 주소, redirect 대상 검증이 필요하다. 입력 계약 구현과 별개로 공개 배포 전 반드시 검토한다.

4. **민감 설정 파일 처리**  
   현재 `application.yaml`의 사용자 변경은 이 작업에서 수정하지 않는다. 저장소 공유·배포 전에는 포함된 인증정보를 환경변수로 옮기고 노출된 값은 회전해야 한다.

## 10. 권장 첫 구현 단위

한 번에 2~7단계를 모두 구현하지 않는다. 첫 PR 또는 첫 작업 단위는 아래만 포함한다.

1. 오래된 테스트 컴파일 수정
2. 고정 fixture 추가
3. `GeoScrapingService`에서 라벨 형식 `html_text` 생성
4. JSON-LD를 항상 배열로 생성
5. 요청 직렬화 계약 테스트

이 단위가 통과하면 도메인 차단, fallback 품질, 응답/worker 정합화를 순서대로 진행한다.
