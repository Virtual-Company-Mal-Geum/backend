# Graph Report - backend  (2026-08-29)

## Corpus Check
- Corpus is ~20,637 words - fits in a single context window. You may not need a graph.

## Summary
- 446 nodes · 1324 edges · 16 communities
- Extraction: 90% EXTRACTED · 10% INFERRED · 0% AMBIGUOUS · INFERRED: 133 edges (avg confidence: 0.81)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- AI Service Tests
- JPA Domain Model
- Analysis Job Workflow
- API and JWT Auth
- Spring Security Config
- Client OAuth Profile
- Web Scraping Pipeline
- Architecture Documentation
- Auth DTO Tests
- Job Status Lifecycle
- Application Bootstrap
- Gradle Wrapper
- OAuth Properties
- Report Status Lifecycle

## God Nodes (most connected - your core abstractions)
1. `Client` - 45 edges
2. `Order` - 32 edges
3. `GeoScrapingService` - 27 edges
4. `AnalysisJob` - 26 edges
5. `ClientRepository` - 26 edges
6. `DomainStatus` - 22 edges
7. `ScrapedData` - 21 edges
8. `JwtTokenProvider` - 21 edges
9. `AnalysisJobService` - 19 edges
10. `GeoAsyncWorker` - 19 edges

## Surprising Connections (you probably didn't know these)
- `PostgreSQL OAuth JWT Scraping and AI Server Settings` --semantically_similar_to--> `PostgreSQL Service`  [INFERRED] [semantically similar]
  src/main/resources/application.yaml → docker-compose.yml
- `Metadata Content JSON-LD and Boilerplate Cases` --semantically_similar_to--> `Labeled HTML Text and JSON-LD Array`  [INFERRED] [semantically similar]
  src/test/resources/geo/gateway-input/static-page.html → docs/AI_SERVER_INPUT_CONTRACT_IMPLEMENTATION_PLAN.md
- `PostgreSQL OAuth JWT Scraping and AI Server Settings` --conceptually_related_to--> `Synchronous Order and Scheduled Retry Flow`  [INFERRED]
  src/main/resources/application.yaml → docs/BACKEND_IMPLEMENTATION_FLOW.md
- `AI Server Input Contract Implementation Plan` --references--> `Static Gateway Input Fixture`  [EXTRACTED]
  docs/AI_SERVER_INPUT_CONTRACT_IMPLEMENTATION_PLAN.md → src/test/resources/geo/gateway-input/static-page.html
- `Legacy Three-Field Swagger Payload` --semantically_similar_to--> `Four-Field AI Request Contract`  [INFERRED] [semantically similar]
  docs/Swagger AI 서버 테스트 방법.md → docs/AI_SERVER_INPUT_CONTRACT_IMPLEMENTATION_PLAN.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **AI Gateway Contract** — docs_ai_server_input_contract_implementation_plan_four_field_contract, docs_ai_server_input_contract_implementation_plan_labeled_html_jsonld, src_test_resources_geo_gateway_input_static_page_contract_cases [INFERRED 0.85]
- **Verified Backend Documentation** — docs_backend_plan__verification_method, docs_backend_plan_runtime_history_separation, docs_backend_implementation_flow_document [INFERRED 0.85]

## Communities (16 total, 0 thin omitted)

### Community 0 - "AI Service Tests"
Cohesion: 0.07
Nodes (24): AssertTrue, com.fasterxml.jackson.databind.JsonNode, com.fasterxml.jackson.databind.ObjectMapper, org.junit.jupiter.api.Disabled, org.junit.jupiter.api.DisplayName, org.junit.jupiter.api.Test, org.junit.jupiter.params.ParameterizedTest, org.junit.jupiter.params.provider.CsvSource (+16 more)

### Community 1 - "JPA Domain Model"
Cohesion: 0.09
Nodes (23): jakarta.persistence.Entity, jakarta.persistence.EntityListeners, jakarta.persistence.MappedSuperclass, jakarta.persistence.Table, lombok.Builder, lombok.Getter, lombok.NoArgsConstructor, org.springframework.data.jpa.domain.support.AuditingEntityListener (+15 more)

### Community 2 - "Analysis Job Workflow"
Cohesion: 0.09
Nodes (23): lombok.RequiredArgsConstructor, org.springframework.data.jpa.repository.Query, org.springframework.scheduling.annotation.Scheduled, org.springframework.stereotype.Service, org.springframework.test.context.ActiveProfiles, org.springframework.transaction.annotation.Transactional, org.springframework.web.bind.annotation.ResponseStatus, AnalysisJob (+15 more)

### Community 3 - "API and JWT Auth"
Cohesion: 0.10
Nodes (22): Claims, jakarta.transaction.Transactional, jakarta.validation.constraints.AssertTrue, javax.crypto.SecretKey, org.springframework.http.ResponseEntity, org.springframework.security.access.prepost.PreAuthorize, org.springframework.security.core.Authentication, org.springframework.security.core.userdetails.UserDetails (+14 more)

### Community 4 - "Spring Security Config"
Cohesion: 0.08
Nodes (33): jakarta.servlet.FilterChain, jakarta.servlet.http.HttpServletRequest, jakarta.servlet.http.HttpServletResponse, lombok.extern.slf4j.Slf4j, org.springframework.boot.CommandLineRunner, org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest, org.springframework.boot.test.context.TestConfiguration, org.springframework.boot.web.client.RestClientCustomizer (+25 more)

### Community 5 - "Client OAuth Profile"
Cohesion: 0.08
Nodes (21): jakarta.servlet.http.HttpSession, org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest, org.springframework.security.oauth2.client.userinfo.OAuth2UserService, org.springframework.security.oauth2.core.user.OAuth2User, ClientProfileResponse, Client, ClientPlan, BASIC (+13 more)

### Community 6 - "Web Scraping Pipeline"
Cohesion: 0.19
Nodes (6): com.fasterxml.jackson.databind.node.ArrayNode, com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter, java.util.regex.Pattern, org.jsoup.nodes.Document, GeoScrapingService, GeoScrapingServiceContractTest

### Community 7 - "Architecture Documentation"
Cohesion: 0.09
Nodes (23): Docker Compose Infrastructure, PostgreSQL Service, Redis Kafka and OpenSearch Services, AI Server Input Contract Implementation Plan, Four-Field AI Request Contract, Labeled HTML Text and JSON-LD Array, Semantic Parity Instead of Byte Identity, Client Order AnalysisJob and AnalysisReport (+15 more)

### Community 8 - "Auth DTO Tests"
Cohesion: 0.17
Nodes (11): AuthServiceTest.TestConfig, lombok.Data, lombok.Setter, org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase, org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest, org.springframework.test.annotation.Rollback, ClientUpdateForm, LoginRequest (+3 more)

### Community 9 - "Job Status Lifecycle"
Cohesion: 0.17
Nodes (12): AnalysisJobStatus, FAILED, PENDING, RETRY_WAIT, RUNNING, SUCCEEDED, ExternalStatus, ACCEPTED (+4 more)

### Community 10 - "Application Bootstrap"
Cohesion: 0.27
Nodes (7): org.springframework.boot.autoconfigure.SpringBootApplication, org.springframework.boot.test.context.SpringBootTest, org.springframework.data.jpa.repository.config.EnableJpaAuditing, org.springframework.scheduling.annotation.EnableAsync, org.springframework.scheduling.annotation.EnableScheduling, GeoApplication, GeoApplicationTests

### Community 11 - "Gradle Wrapper"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 12 - "OAuth Properties"
Cohesion: 0.67
Nodes (3): org.springframework.boot.context.properties.ConfigurationProperties, OAuth2ProviderProperties, ProviderConfig

### Community 13 - "Report Status Lifecycle"
Cohesion: 0.50
Nodes (4): ReportStatus, AVAILABLE, DELETED, EXPIRED

## Knowledge Gaps
- **32 isolated node(s):** `ORDER_LOADING`, `SCRAPING`, `AI_REQUEST`, `AI_RESPONSE_PARSING`, `REPORT_SAVING` (+27 more)
  These have ≤1 connection - possible missing edges or undocumented components.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Client` connect `Client OAuth Profile` to `JPA Domain Model`, `Analysis Job Workflow`, `API and JWT Auth`, `Spring Security Config`, `Auth DTO Tests`?**
  _High betweenness centrality (0.130) - this node is a cross-community bridge._
- **Why does `AnalysisJob` connect `Analysis Job Workflow` to `JPA Domain Model`, `API and JWT Auth`, `Job Status Lifecycle`?**
  _High betweenness centrality (0.068) - this node is a cross-community bridge._
- **Why does `Order` connect `JPA Domain Model` to `Analysis Job Workflow`, `API and JWT Auth`, `Client OAuth Profile`?**
  _High betweenness centrality (0.064) - this node is a cross-community bridge._
- **What connects `ORDER_LOADING`, `SCRAPING`, `AI_REQUEST` to the rest of the system?**
  _32 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `AI Service Tests` be split into smaller, more focused modules?**
  _Cohesion score 0.07467630231857875 - nodes in this community are weakly interconnected._
- **Should `JPA Domain Model` be split into smaller, more focused modules?**
  _Cohesion score 0.08571428571428572 - nodes in this community are weakly interconnected._
- **Should `Analysis Job Workflow` be split into smaller, more focused modules?**
  _Cohesion score 0.09200603318250378 - nodes in this community are weakly interconnected._