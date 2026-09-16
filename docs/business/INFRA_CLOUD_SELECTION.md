# 백엔드 클라우드 선정 리서치 (GEO 서비스)

조사일: 2026-09-10 · 환율 기준 1 USD = 1,390 KRW (당일 시세 1,386~1,395 구간) · 가격은 바뀌므로 각 표의 출처 링크로 재확인할 것.

## 0. 결론 (한 문단)

**지금 당장은 Oracle Cloud Always Free(서울 리전, ARM 2 OCPU/12 GB) 위에 Spring Boot + Postgres를 같이 올리고, 집의 RTX 4090 AI서버와는 Tailscale(무료)로 연결하며, JSON-LD 재작성은 Gemini 2.5 Flash-Lite 무료 티어(1,000 RPD)를 그대로 쓰는 구성이 월 0원이다.** Oracle 용량 부족으로 인스턴스를 못 만들면 AWS 신규 계정 크레딧($100~200, 6개월) → 만료 후 Vultr/Lightsail 서울 4 GB(월 약 3만원) 순으로 내려간다. 성장 시 가장 먼저 터지는 건 클라우드가 아니라 **동기 200 s 요청 구조**와 **Gemini 1,000건/일 상한**이며, 둘 다 코드/모델 작업(202 비동기 전환, 같은 4090 위 LoRA 어댑터 추가)으로 해결된다. GPU는 하루 1만건 근처까지 로컬 1대가 클라우드 GPU보다 압도적으로 싸고, 그 이후에도 "4090 한 대 더"가 클라우드 GPU보다 싸다. 클라우드 GPU는 비용이 아니라 **가용성(SLA)** 이 필요해질 때 가는 길이다.

---

## 1. 단계별 추천안

### 0단계 — 지금 (목표 월 0원)

| 구성요소 | 선택 | 월 비용 | 근거 |
|---|---|---|---|
| 백엔드 VM | **Oracle Cloud Always Free, VM.Standard.A1.Flex 2 OCPU / 12 GB, 서울(South Korea Central)** | 0원 | 영구 무료. 2026-06-15부터 4→2 OCPU로 반감됐지만 JVM+Chromium엔 충분. 춘천 리전만 A1 불가, 서울은 가능. 블록 스토리지 200 GB, 아웃바운드 10 TB/월 무료 |
| Postgres | **같은 VM에 설치** | 0원 | 12 GB RAM이면 JVM 2 GB + Postgres 1 GB + Chromium 여유. pg_dump cron → R2 백업 |
| 집 GPU 연결 | **Tailscale Personal** | 0원 | 6유저·무제한 디바이스. 클라우드 VM은 공인 IP가 있어 홀펀칭이 거의 항상 직접 연결(P2P)로 성립. 타임아웃 제한 없음. `AI_SERVER_BASE_URL=http://100.x.y.z:8000` |
| JSON-LD 재작성 | **Gemini 2.5 Flash-Lite 무료 티어** | 0원 | 1,000 RPD / 15 RPM. 지금 쓰는 그대로 |
| 도메인/TLS | Cloudflare DNS(무료, **DNS only**) + Caddy(Let's Encrypt 자동) | 0원 | Cloudflare 프록시(주황 구름)를 켜면 125 s에 524 → 동기 200 s 요청과 충돌. 비동기 전환 전까지는 DNS only |
| 백업 저장소 | Cloudflare R2 | 0원 | 10 GB/월 무료, 이그레스 무료 |
| CI / 이미지 | GitHub Actions(프라이빗 2,000분/월) + GHCR | 0원 | Dockerfile부터 만들어야 함 (현재 없음) |
| 모니터링 | UptimeRobot 무료 + OCI 기본 메트릭 | 0원 | |

**Oracle이 "Out of capacity"면 (흔함):** ① 다른 AD/시간대에 재시도(스크립트 반복) ② 안 되면 AWS Free 플랜 신규 계정 크레딧 $100(+온보딩 과제로 $100 추가) → Lightsail 서울 4 GB($24/월 ≈ 3.3만원)로 6개월 ③ 그 뒤 Vultr 서울 2 vCPU/4 GB($20~24) 또는 Lightsail 유지.

**동시에 신청해 둘 크레딧 (심사 있음, 되면 1단계 비용이 사라짐):**
- Google for Startups **Start 티어 $2,000** — 무투자·MVP 보유·창업 5년 이내, 셀프 신청. e2-medium 서울($31.38/월)을 1년 내내 돌리고도 남음.
- AWS Activate **Founders $1,000** — 무투자, 직원 10명 미만, 웹사이트 필요.
- 네이버클라우드 **그린하우스** — AC/VC 산하가 아니면 오픈이노베이션 공모로 최대 2,000만원. 되면 한국 리전 + 한국어 지원이라는 이점이 생김.

### 1단계 — 프리티어 만료 또는 Gemini 1,000건/일 초과

| 트리거 | 조치 | 월 비용 |
|---|---|---|
| Gemini RPD 1,000 초과 | Gemini 결제 계정 연결(Tier 1) — 모델은 **2.5 Flash-Lite 유지**($0.10/$0.40 per 1M). 3.x Flash-Lite는 2.5~3배 비쌈 | 요청당 약 1.9원 → 5천건/일이면 약 29만원 |
| Gemini 월 비용 > 10만원 | **같은 4090에 JSON-LD 재작성용 LoRA 어댑터 추가**. vLLM `--enable-lora --lora-modules jsonld=...`로 news/ecommerce/education 어댑터 옆에 얹음. VRAM 추가 ≈ 0. 학습 데이터는 지금까지 쌓인 Gemini 출력(하루 1천건 × N일)으로 distill | 0원 (학습 시간만) |
| 백엔드 무료 VM 종료 | Vultr 서울 2 vCPU/4 GB 또는 Lightsail 서울 4 GB | 약 2.8~3.3만원 |
| DB > 5 GB | `rawScrapedData`(HTML 원본)를 R2로 이관, DB엔 키만 | R2 10 GB까지 0원, 이후 GB당 $0.015 |

### 2단계 — GPU 포화 (약 1만~1.5만건/일)

**선행 조건(코드):** `POST /order` → 202 + `analysis_job` 폴링으로 전환. 이미 있는 DB 큐(`findNextJobForUpdate`)로 충분하며 SQS/Kafka 불필요. 이걸 안 하면 VM을 늘려도 Tomcat 스레드가 200 s씩 묶여 의미 없음.

| 옵션 | 월 고정비 | 건당 비용(1만건/일) | 판단 |
|---|---|---|---|
| (a) 로컬 4090 1대 추가 | 전기 약 2.5만 + 감가 약 7만 = **약 10만원** | 0.3원 | **비용 최저.** 단, 집 회선/정전/단일 장소 리스크 |
| (b) RunPod Community 4090 상시 | $0.34/h × 730 h = $248 ≈ **34만원** | 1.1원 | 가용성 필요할 때 |
| (c) RunPod Serverless 4090 | $1.10/h × 실사용 3.5 h/일 ≈ $115 ≈ **16만원** | 0.5원 | 콜드스타트(수십 초) → 비동기 전환 후에만 가능 |
| (d) Vast.ai 4090 | $0.29~0.39/h ≈ 21~28만원 | 0.9원 | 신뢰성 편차 큼 |
| (e) 평가까지 Gemini Flash-Lite로 | 0 | 약 1.9원 → **58만원/월** | 로컬보다 6배 비쌈. 탈락 |

→ **2단계도 로컬 GPU 추가가 답.** 클라우드 GPU는 "집 GPU가 죽었을 때 큐가 멈추면 안 되는" 시점(유료 고객·SLA)에 (c) 서버리스를 **fallback**으로만 붙인다.

### 3단계 — 관리형 전환 트리거 (수치로 고정)
- 유료 고객 발생 또는 월 매출 > 인프라비 5배 → 관리형 Postgres(Lightsail DB $15 / Supabase Pro $25 / Naver Cloud DB), 백업 자동화
- 월 주문 > 30만건 → 백엔드 2대 + LB, 큐 폴링 워커 분리
- 집 GPU 장애로 실제 손실 발생 → (c) 서버리스 GPU fallback 상시 등록

---

## 2. 비교표

### 2-1. 집 GPU ↔ 클라우드 연결

| 후보 | 비용 | 요청 타임아웃 | 비고 | 판정 |
|---|---|---|---|---|
| **Tailscale Personal** | 0원 (6유저, 디바이스 무제한, 50 tagged) | 없음 (직접 WireGuard) | 서울 DERP 없음(도쿄·싱가포르·홍콩). 그러나 DERP는 핸드셰이크/폴백용이고 VM 쪽 공인 IP 덕에 직접 연결이 성립하면 서울↔집 직결 | **채택** |
| Cloudflare Tunnel | 0원 | **125 s** (비엔터프라이즈 고정) | 프록시 경유로 한 홉 추가. 200 s 읽기 타임아웃과 충돌 가능. AI서버 Swagger를 팀원에게 노출할 때는 유용 | 보조 |
| WireGuard 수동 | 0원 | 없음 | 집 쪽 공인 IP/포트포워딩 필요 | 탈락 |
| ngrok 무료 | 0원 | — | 상용/상시 사용 부적합 | 탈락 |

### 2-2. 백엔드 VM (2 vCPU / 4 GB급, 한국 리전)

| 제공자 | 사양 | 월 요금 | 무료/크레딧 | 판정 |
|---|---|---|---|---|
| **Oracle Always Free A1** | 2 OCPU(ARM) / 12 GB, 서울 | **0원 영구** | 2026-06 반감. 용량 부족 빈번. ARM64: Playwright Chromium 공식 지원(Ubuntu 22/24 arm64) | **1순위** |
| Oracle E2.1.Micro ×2 | 1/8 OCPU / 1 GB | 0원 영구 | Chromium 불가 수준 | 탈락 |
| AWS Lightsail 서울 | 2 vCPU / 4 GB / 80 GB / 4 TB | $24 ≈ 3.3만원 (2 GB는 $12) | 신규 계정 $100~200 크레딧 6개월(2025-07 개편, 이후 계정 자동 폐쇄 주의). Activate Founders $1,000 | 2순위 |
| AWS EC2 t4g.medium 서울 | 2 vCPU(ARM) / 4 GB | 약 $31 ≈ 4.3만원 (추정, 서울 프리미엄 반영) | 동일 | Lightsail보다 비쌈 |
| GCP e2-medium 서울 | 2 vCPU / 4 GB | **$31.38 ≈ 4.4만원** (1년 약정 $19.77) | $300/90일. e2-micro 무료는 **미국 리전만**. Startups Start $2,000 | 크레딧 되면 1순위 대체 |
| GCP e2-small 서울 | 2 vCPU / 2 GB | 약 $15.7 (추정, e2-medium의 절반) | | 2 GB는 Chromium에 빠듯 |
| Naver Cloud Micro | 1 vCPU / 1 GB / HDD 50 GB | 1년 무료 + 30만 크레딧 | 포트 제한(22 외 1024+), 공인 IP 4,032원/월 별도, 아웃바운드 20 GB | JVM+Chromium 불가 → 탈락 |
| Naver Cloud High CPU g3 | 2 vCPU / 4 GB | **약 7.2만원** (2024-11 기준, 추정 — 요금계산기로 재확인) | 그린하우스 최대 2,000만원 | 크레딧 없으면 2배 비쌈 |
| Vultr 서울 | 2 vCPU / 4 GB | $20~24 ≈ 2.8~3.3만원 | 없음 | 유료 최저가 후보 |
| Cloudflare Containers | 최대 4 vCPU/12 GB | Workers Paid $5 + 종량 | 포함량 25 GiB-h/월 = 4 GB 인스턴스 약 6시간. 상시 구동 설계 아님 | **탈락** (Cloudflare는 DNS/R2/Tunnel만) |
| GCP Cloud Run | — | 요청당 | Chromium 콜드스타트 + 200 s 동기 요청 → 부적합. 비동기 전환 후 재검토 | 보류 |

### 2-3. Postgres

| 후보 | 저장 | 리전 | 월 요금 | 판정 |
|---|---|---|---|---|
| **VM 내 설치** | VM 디스크(OCI 200 GB) | 서울 | 0원 | **채택** |
| Supabase Free | 500 MB, 2 프로젝트 | **서울 있음** | 0원 | 1주 미사용 시 일시정지 → 개발용만 |
| Neon Free | 0.5 GB/프로젝트, 100 CU-h | 싱가포르(서울·도쿄 없음) | 0원 | 지연 + 90일 미사용 삭제 → 탈락 |
| Lightsail DB | 1 GB / 40 GB | 서울 | $15 ≈ 2.1만원 | 3단계 |
| Supabase Pro | 8 GB | 서울 | $25 | 3단계 |
| Oracle Autonomous DB | 20 GB ×2 | 서울 | 0원 | Oracle DB지 Postgres 아님. JPA dialect 교체 필요 → 탈락 |

### 2-4. Gemini / JSON-LD 재작성

무료 티어 (2025-12-07 감축 이후, 2026-01 기준 커뮤니티 집계 — 정확한 값은 AI Studio 대시보드에서 확인):

| 모델 | 무료 RPD | 무료 RPM | 유료 입력 $/1M | 유료 출력 $/1M |
|---|---|---|---|---|
| **2.5 Flash-Lite** | **1,000** | 15 | **0.10** | **0.40** |
| 2.5 Flash | 250 | 10 | 0.30 | 2.50 |
| 3.1 Flash-Lite | (미확인) | | 0.25 | 1.50 |
| 3.5 Flash-Lite | (미확인) | | 0.30 | 2.50 |
| 3.8 Flash | (미확인) | | 0.75 (2027부터 1.50) | 3.75 (2027부터 7.50) |

요청당 비용 (가정: 입력 8k 토큰 = 스크래핑 마크다운 + 피드백, 출력 1.5k 토큰 = JSON-LD):

| 모델 | 요청당 | 1천건/일 (월) | 5천건/일 | 1만건/일 |
|---|---|---|---|---|
| 2.5 Flash-Lite | $0.0014 ≈ **1.9원** | $42 ≈ 5.8만원 | 29만원 | 58만원 |
| 3.1 Flash-Lite | $0.0043 ≈ 5.9원 | 18만원 | 89만원 | 177만원 |
| 2.5 Flash | $0.0062 ≈ 8.5원 | 26만원 | 128만원 | 256만원 |

→ 유료 전환 시 **2.5 Flash-Lite 고정**. 월 10만원 넘기 전에 LoRA distill로 전환하는 게 맞다 (아래 2-5).

### 2-5. 로컬 4090 vs 대안 (평가 + 재작성 모두 로컬일 때)

**4090 처리량 (Llama-3 8B AWQ, vLLM):** prefill 약 12k tok/s, 배치 디코드 약 550~600 tok/s 합산. 요청당 입력 8k + 출력 1k → 배치 시 실효 약 2.5 s/건 → 이론 최대 약 3.4만건/일, 현실적(50% 가동) **약 1.5만건/일**. 사용자의 "1만건" 상한 주장과 부합.

**로컬 고정비:**
- 전기: 추론 시 360~410 W, 유휴 약 33 W(GPU) + 시스템 약 50 W. 1만건/일 = 약 3.5 h 부하 → 약 3 kWh/일 ≈ 90 kWh/월. 가정용 누진 2~3구간(215~307원/kWh) 한계 단가로 **약 2~2.8만원/월**
- 감가: 4090 약 250만원 / 36개월 ≈ **7만원/월** (이미 보유 = 매몰비용이면 0)
- 합계 약 **9.5~10만원/월**, 건수와 무관

**손익분기 (Gemini Flash-Lite 1.9원/건 대비):**
- 감가 포함: 9.5만 ÷ 1.9원 ≈ **5만건/월 ≈ 1,700건/일** 이상이면 로컬이 이득
- 감가 제외(이미 보유): 2.5만 ÷ 1.9원 ≈ 1.3만건/월 ≈ **440건/일**
- 즉 GPU를 이미 갖고 있으면 지금 트래픽에서도 로컬이 맞고, 1만건/일이면 로컬이 6배 싸다. "1만건까지 로컬이 싸다"가 아니라 **"1만건이 로컬 1대의 용량 상한"** 이 정확한 표현.

### 2-6. 부대 서비스

| 항목 | 선택 | 무료 한도 |
|---|---|---|
| 오브젝트 스토리지 | Cloudflare R2 | 10 GB/월, Class A 100만, Class B 1,000만, 이그레스 무료. 초과 $0.015/GB |
| CI | GitHub Actions | 프라이빗 2,000분/월 Linux, 아티팩트 500 MB. 지출 한도 기본 $0 → 초과 시 중단 |
| 컨테이너 레지스트리 | GHCR | Packages 저장 500 MB(Actions와 공유) |
| DNS | Cloudflare | 무료 |
| 업타임 | UptimeRobot | 무료 50 모니터 |

---

## 3. 비용 모델 (월, KRW)

전제: OCI 무료 VM 확보, GPU 이미 보유(감가 제외), Flash-Lite 입력 8k/출력 1.5k.

| 주문/일 | 백엔드 VM | DB | 연결 | GPU 전기 | Gemini(재작성) | **합계** | 비고 |
|---|---|---|---|---|---|---|---|
| 1천 | 0 | 0 | 0 | 약 1만 | 0 (무료 티어 내) | **약 1만** | 0단계 |
| 5천 | 0 | 0 | 0 | 약 1.8만 | 29만 (또는 LoRA 시 0) | **31만 → LoRA 시 2만** | 1단계 |
| 1만 | 0 | 0 | 0 | 약 2.5만 | 58만 (LoRA 시 0) | **60만 → LoRA 시 2.5만** | GPU 용량 상한 |
| 5만 | 3만 (유료 VM) | 0~2만 | 0 | 약 10만 (4090 ×4) 또는 서버리스 GPU 약 60만 | LoRA 0 | **약 15만 (로컬 증설) / 약 70만 (클라우드 GPU)** | 2단계 |

OCI 못 받고 AWS/Vultr 유료 VM이면 각 행에 +3만원. 감가를 넣으면 GPU 1대당 +7만원.

---

## 4. 성장 트리거 요약

| 신호 | 먼저 할 일 | 인프라 변경 |
|---|---|---|
| 동시 주문 5건 이상 시 응답 지연 | 202 비동기 전환 (`GeoAsyncWorker` 폴링 경로 사용) | 없음 |
| Gemini 429 발생 | 결제 연결(Flash-Lite) | 없음 |
| Gemini 월 10만원 초과 | JSON-LD LoRA 학습·서빙 | 없음 (같은 4090) |
| GPU 큐 대기 > 5분 지속 | 4090 추가 | 집 PC 1대 |
| DB > 5 GB | HTML 원본 R2 이관 | R2 |
| OCI 용량/정책 변경 | Vultr/Lightsail 서울로 이전 (Docker 이미지 있으면 30분) | VM |
| 유료 고객 SLA | 서버리스 GPU fallback + 관리형 DB | RunPod/Lightsail DB |

---

## 5. 배포 전 필수 (클라우드와 무관한 코드 작업, 별도 진행)

- SSRF 이그레스 차단 (`targetUrl`이 사설 IP/메타데이터 엔드포인트로 가지 않게) — 클라우드 VM에선 `169.254.169.254` 메타데이터 노출 위험이 실제로 커짐
- AI 서버 앞 동시성 게이트 (Tomcat 스레드 고갈 방지)
- Flyway 도입 (`ddl-auto: update` 제거)
- Dockerfile(Playwright 포함, arm64/amd64 멀티아치) + GitHub Actions
- `application.yaml` 내 자격증명 → 환경변수, 로테이션
- 깨진 `docker-compose.yml` 정리

---

## 6. 사용자 수동 확인 항목

집 PC에서 후보 리전 지연 측정 (문서엔 명령만 남김):

```bash
curl -o /dev/null -s -w "%{time_connect}\n" https://objectstorage.ap-seoul-1.oraclecloud.com
curl -o /dev/null -s -w "%{time_connect}\n" https://ec2.ap-northeast-2.amazonaws.com
curl -o /dev/null -s -w "%{time_connect}\n" https://asia-northeast3-run.googleapis.com
```

Gemini 실제 무료 한도: https://aistudio.google.com/rate-limit 에서 프로젝트별 확인.

---

## 7. 출처

**연결**
- Cloudflare 524 / 125 s 타임아웃: https://developers.cloudflare.com/support/troubleshooting/http-status-codes/cloudflare-5xx-errors/error-524/
- Tailscale 무료 플랜: https://tailscale.com/docs/account/manage-plans/free-plans-discounts
- Tailscale DERP 위치: https://tailscale.com/docs/reference/derp-servers
- Tailscale 연결 유형: https://tailscale.com/docs/reference/connection-types

**VM**
- Oracle Always Free 리소스(2 OCPU/12 GB, 춘천 제외, 200 GB, 10 TB): https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm
- Oracle A1 반감 보도: https://www.infoq.com/news/2026/07/oracle-cloud-free-tier-limits/
- AWS 프리티어 개편($100~200, 6개월): https://aws.amazon.com/about-aws/whats-new/2025/07/aws-free-tier-credits-month-free-plan/
- AWS Lightsail 가격: https://aws.amazon.com/lightsail/pricing/
- AWS Activate Founders: https://cloudkompas.com/blog/aws-activate-complete-guide-2026
- GCP 무료 티어(e2-micro 미국 한정, $300): https://cloud.google.com/free
- GCP e2-medium 서울 $31.38: https://gcloud-compute.com/e2-medium.html
- GCP Startups Start $2,000: https://cloud.google.com/startup/pre-funded
- 네이버 Micro 서버 정책: https://hoing.io/archives/4778 , https://www.ncloud.com/main/creditEvent
- 네이버 그린하우스: https://greenhouse.oopy.io/ , https://www.nextunicorn.kr/support-programs/52630829fd9bc4c8
- 네이버 서버 스펙: https://guide.ncloud-docs.com/docs/server-spec-vpc (요금은 https://www.ncloud.com/charge/calc/ko 에서 재확인)
- Vultr 가격: https://costbench.com/software/cloud-infrastructure/vultr/
- Cloudflare Containers 가격: https://developers.cloudflare.com/containers/pricing/
- Playwright arm64 지원: https://playwright.dev/docs/intro

**DB**
- Neon 리전(싱가포르·시드니만): https://neon.com/docs/introduction/regions
- Supabase 무료(500 MB, 1주 정지): https://supabase.com/pricing

**AI**
- Gemini 가격: https://ai.google.dev/gemini-api/docs/pricing
- Gemini 티어 승급 조건: https://ai.google.dev/gemini-api/docs/rate-limits
- Gemini 무료 RPD 집계(2026-01): https://www.aifreeapi.com/en/posts/gemini-api-free-tier-rate-limits
- vLLM multi-LoRA: https://docs.vllm.ai/en/latest/features/lora.html
- 4090 vLLM 벤치마크: https://gigagpu.com/rtx-4090-24gb-llama-3-8b-benchmark/ , https://www.databasemart.com/blog/vllm-gpu-benchmark-rtx4090
- 4090 전력: https://www.servethehome.com/nvidia-geforce-rtx-4090-founders-edition-review-the-gpu/7/ , https://gigagpu.com/gpu-power-consumption-ai-inference/
- RunPod/Vast 4090 시간당: https://gpuhosted.com/en/runpod-review/ , https://www.synpixcloud.com/blog/vast-ai-vs-runpod-rtx-4090-pricing

**기타**
- R2 가격: https://developers.cloudflare.com/r2/pricing/
- GitHub Actions 과금: https://docs.github.com/billing/managing-billing-for-github-actions/about-billing-for-github-actions
- 한전 주택용 요금표: https://home.kepco.co.kr/kepco/front/html/CY/E/E/CYEEHP00101.html
- 환율: https://www.xe.com/en-us/currencyconverter/convert/?Amount=1&From=USD&To=KRW
