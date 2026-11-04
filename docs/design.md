# Commission Quote App: Design

This document is the spec the implementation is built against. Items marked **(assumption)** are not in the brief and would be confirmed with the business or the vendor in a real project.

## 1. Goal

Staff on the Lending Platform enter loan details, request a commission quote from the third party vendor Commission Quote API, and see the result, with clear loading and error states. The vendor API is still under construction, so a mock vendor implements the agreed contract.

## 2. Repository layout

```
commission-quote-app/
├── web/            UI (TypeScript)
├── bff/            BFF (Java)
├── mock-vendor/    vendor mock (Java)
├── docs/
│   ├── design.md
│   └── vendor-openapi.yaml
├── .github/workflows/ci.yml
├── start-all.sh
├── docker-compose.yml       (stretch)
└── README.md
```

Java 21 (assumption: match installed JDK), current stable Spring Boot, Maven wrapper in each Java project. Each part builds and tests on its own.

## 3. Architecture (as built)

```
Browser (web)  ──Bearer token──▶  BFF  ──api-key + Idempotency-Key──▶  Mock Vendor
port 5173 (Vite proxy /api)       8080                                   8081
```

Rules:

- The browser never sees the vendor `api-key`. Only the BFF holds it. Locally it comes from environment config; in production it comes from AWS Secrets Manager (see section 14).
- The BFF never imports code from `mock-vendor`. It knows the vendor only through `VENDOR_BASE_URL` and `VENDOR_API_KEY`. Switching to the real vendor is a config change.
- The UI calls the BFF through the Vite dev proxy, so there is a single origin and no CORS configuration locally.

### BFF internal layers

| Layer | Package | Responsibility |
|---|---|---|
| Web | `web` | HTTP only: controller, DTOs, request validation, exception handler, error responses |
| Service | `quote` | `QuoteService`: business rules and orchestration. No HTTP or Spring MVC types |
| Client | `vendor` | `VendorQuoteClient` interface and HTTP implementation: api-key, timeouts, retries, idempotency header, response mapping |
| Security | `security` | Authentication (local stub or JWT), scope enforcement, startup guard |

**Production note:** for scope, business logic lives in the BFF. Before production, `QuoteService` and the vendor client would be extracted into a separate Quote Service so the BFF holds no vendor secrets and other channels can reuse the integration. They are already isolated behind interfaces, so this is a move, not a rewrite.

## 4. Vendor contract (implemented by mock-vendor)

The contract is written first, contract first, as `docs/vendor-openapi.yaml` (OpenAPI 3). It is the source of truth: the mock vendor implements it, the BFF client is built against it, and it is the document we would hand the real vendor, including the proposed `Idempotency-Key` extension. A mock vendor test checks responses against the spec, so the mock cannot drift from the contract.

`POST /v1/commission-quotes` (assumption: path not given in the brief)

Headers:

| Header | Required | Notes |
|---|---|---|
| `api-key` | yes | Per the brief |
| `Idempotency-Key` | no | Proposed extension to the agreed contract; supported by the mock |
| `X-Correlation-Id` | no | Logged by the mock for tracing |

Request:
```json
{ "loanAmount": 500000.00, "loanTermInMonths": 360, "riskBand": "MEDIUM" }
```

Response 200:
```json
{ "quoteId": "b3f1c2e4-...", "commissionRate": 0.0075, "totalCommission": 3750.00 }
```

- `commissionRate` is a decimal fraction: 0.0075 means 0.75% (assumption).
- Money and rates use `BigDecimal` end to end, never `double`. JSON carries them as numbers, matching the contract as written.
- `totalCommission = loanAmount × commissionRate`, rounded to 2 decimal places with HALF_EVEN.

| Status | When |
|---|---|
| 200 | Valid key, valid payload |
| 400 | Invalid payload |
| 401 | Missing or invalid `api-key` (same response for both) |
| 409 | `Idempotency-Key` reused with a different payload |
| 500 / 503 | Random simulated failure |
| slow 200 | Random simulated delay longer than the BFF timeout |

## 5. Mock vendor behaviour

**Rates by risk band** (illustrative; the real vendor owns the calculation): LOW 0.50%, MEDIUM 0.75%, HIGH 1.00%.

**Security**
- Expected key from config (`VENDOR_API_KEY`); the app refuses to start without it.
- Key check runs in a filter before any controller logic.
- Comparison with `MessageDigest.isEqual` (constant time).

**Idempotency**
- Stores `Idempotency-Key` → (request payload, response) in memory with a TTL (1 hour).
- Same key and same payload: return the stored response, no new quote.
- Same key and different payload: 409.

**Failure simulation**
- `vendor.simulation.enabled` (default true), `failure-rate` (default 0.2), `slow-rate` (default 0.1), `slow-delay` (default 7s, longer than the BFF read timeout).
- Failure modes: 500, 503, slow response.
- Randomness behind an injectable interface so tests can force each mode deterministically.

## 6. BFF API

`POST /api/v1/quotes`

Headers: `Authorization: Bearer <token>` (required), `Idempotency-Key` (generated by the UI; the BFF generates one if absent), `X-Correlation-Id` (optional; generated if absent).

**Validation** (Bean Validation, 400 with field messages) (assumption: bounds):

| Field | Rule |
|---|---|
| `loanAmount` | required, 1,000 to 10,000,000, at most 2 decimal places |
| `loanTermInMonths` | required, whole number, 1 to 360 |
| `riskBand` | required, exactly LOW, MEDIUM or HIGH (case sensitive) |

Malformed JSON and wrong types also return 400 with a clear message.

**Response 200:** `quoteId`, `commissionRate`, `totalCommission`, passed through from the vendor. The BFF never recalculates.

## 7. Authentication and authorization

**Local (as built)**
- The UI sends `Authorization: Bearer <token>` exactly as in production. Locally the token is a fixed dev value chosen in a dev only "Signed in as" selector: `dev-staff` (has `create:quotes`) or `dev-readonly` (no scopes).
- In the `local` profile, a filter maps the dev token to a Spring Security principal with authorities.
- `POST /api/v1/quotes` requires `SCOPE_create:quotes` in every profile. This check is real, not stubbed.
- No token or unknown token: 401. Valid token without the scope: 403.
- Startup guard: the app refuses to start if the local auth stub is active together with the `prod` profile.

**Production (documented, config ready)**
- `prod` profile configures Spring Security as an OAuth2 resource server: Auth0 issuer URI and audience from config, asymmetric signature (RS256) verified against cached JWKS.
- A gateway API key filter exists, enabled only in `prod`.

## 8. Calling the vendor: timeouts, retries, idempotency

- Connect timeout 2s, read timeout 5s per attempt.
- Resilience4j Retry: 1 initial attempt plus up to 2 retries; exponential backoff from 300ms, multiplier 2, full jitter, capped at 2s.
- Retry on: vendor 5xx, timeouts, connection errors. Never on 4xx.
- Overall deadline 20s: no new attempt starts if it cannot finish within the remaining budget. Worst case is about 17s, under the API Gateway integration limit.
- Every attempt sends the same `Idempotency-Key`, so retries can never create duplicate quotes.

**Stretch: response validation.** Reject a vendor 200 with a missing `quoteId`, missing fields, or negative values; treat it as a vendor failure.

**Stretch: circuit breaker (Resilience4j).** Sliding window of 20 calls, minimum 10; opens at 50% failures or 50% slow calls over 4s; open for 30s; 3 trial calls when half open. Retry wraps the breaker, and `CallNotPermittedException` is never retried. No fallback: a quote cannot be faked or reused, so failing fast is the fallback.

## 9. Error responses

The UI is never told what actually failed. Every error uses `ProblemDetail` (RFC 9457) with a generic title, `retryable`, and `correlationId`. The cause lives only in BFF logs.

| Situation | Status | retryable | Logged as |
|---|---|---|---|
| Validation failure | 400 | false | info, with field errors returned |
| Missing or invalid token | 401 | false | info |
| Missing scope | 403 | false | warn |
| Vendor 5xx after retries | 502 | true | warn |
| Vendor timeout or deadline exceeded | 504 | true | warn |
| Circuit open (stretch) | 503 | true | warn |
| Vendor 401 (our key is wrong) | 502 | false | error: configuration problem |
| Vendor 400 (our validation missed it) | 502 | false | error: bug |
| Unexpected exception | 500 | true | error, with stack trace in logs only |

## 10. Observability, logging and config

**Built (minimal)**
- `X-Correlation-Id` on every request: generated if absent, in the logging MDC so it appears on every log line, returned in error bodies, shown to the user as the reference, and forwarded to the vendor.
- Plain, readable logs at sensible levels, including the user's subject and `quoteId` as an audit trail.
- Never logged: the vendor `api-key`, the `Authorization` header, bearer tokens, full request or response bodies.

**Production plan (documented, not built)**

The aim is to be observability ready using open standards, so connecting a platform such as Datadog is configuration, not code.
- **Tracing:** Micrometer Tracing with the OpenTelemetry bridge; W3C Trace Context (`traceparent`) propagated BFF → vendor; `traceId` and `spanId` in the MDC so every log line links to its trace. The correlation ID stays as the business reference staff quote to support.
- **Structured logs:** JSON in production, each line tagged `service`, `env`, `version` (matching Datadog unified service tagging) plus trace and correlation IDs; consistent named events such as `quote.request.received`, `vendor.call.attempt`, `vendor.call.retry_scheduled`, `quote.generated`, `quote.failed`.
- **Metrics (Micrometer):** quote requests by outcome, vendor call latency with percentiles, retry counts, and Resilience4j's own retry and circuit breaker metrics.
- **Integration:** export traces and metrics over OTLP to the Datadog Agent (or the Datadog Java tracer); ship JSON logs from ECS via the agent or FireLens.
- **Dashboards and alerts:** vendor error rate and latency, retry rate, circuit breaker state, 5xx rate, and an alert on vendor 401, since that means our key is wrong.

**Config**
- Fail fast at startup if required config (`VENDOR_BASE_URL`, `VENDOR_API_KEY`) is missing.
- Locally, secrets come from environment variables (`.env`, git ignored). In production, the vendor API key and the gateway API key come from AWS Secrets Manager, never from environment files or source. Code reads the key through Spring configuration, so switching the source is a config change, not a code change.
- Spring Actuator health endpoints on both Java services.

## 11. UI

Plain TypeScript with Vite, no UI framework. It is a single form, and the weight of this assessment is on the integration, so the UI is kept small and transparent: separate modules for form validation, the API client and rendering.

- Form: loan amount, term in months, risk band (select). Browser validation for fast feedback; the BFF stays the source of truth.
- One `Idempotency-Key` per submission (`crypto.randomUUID()`). "Try again" reuses it; editing any field creates a new one on the next submit.
- Generate Quote is disabled while a request is in flight; a loading state shows, and "Still working…" appears after 3 seconds.
- Client timeout 25s (above the BFF's 20s deadline) via `AbortController`.
- Success: quote ID, rate as a percentage, total commission formatted as AUD.
- Error: the generic message plus "Reference: <correlationId>". "Try again" shows only when `retryable` is true. Form values are kept.
- Dev only "Signed in as" selector (staff or read only).
- Accessibility: labelled inputs, errors linked to fields with `aria-describedby`, results and errors announced via `aria-live`. Responsive layout, plain CSS.

## 12. Tests (the ones that matter)

**mock-vendor:** missing key 401; wrong key 401; correct calculation per band; invalid payload 400; same idempotency key returns the same quote; same key with a different payload 409; forced failure and slow modes.

**bff:**
- Controller slice: validation messages; 401 without token; 403 with `dev-readonly`; 200 with `dev-staff`.
- `QuoteService` unit tests.
- Vendor client against WireMock: success; api-key header sent; 503 then success; repeated 503 gives 502; slow response times out and the retry carries the same `Idempotency-Key`; vendor 401 maps to 502 with `retryable: false` and no detail leaked.
- Startup guard test.

**web (Vitest with jsdom and DOM Testing Library):** invalid numbers show field messages and block submit (empty, negative, non numeric, too many decimals, term out of range or not whole); valid input submits once. Stretch: loading state; error with Try again only when retryable.

## 13. Running

- Prerequisites: Java 21 and Node 20+. Maven wrapper included, so no Maven install needed.
- Primary: `./start-all.sh` starts the mock vendor, the BFF and the UI. Each can also run alone (`./mvnw spring-boot:run`, `npm run dev`), documented in the README.
- The Vite dev proxy routes `/api` to the BFF, so the UI uses relative URLs and no CORS configuration exists.
- Stretch: Docker Compose, with nginx serving the built UI and proxying `/api` to the BFF (the same routing rule as the Vite proxy and as CloudFront in production).
- Tests: one command per part plus one for everything.
- Config via `.env.example`; real `.env` files are git ignored.

## 14. Production architecture (documented, not built)

```
Browser ──JWT──▶ WAF ──▶ AWS API Gateway (HTTP API) ──VPC link──▶ private ALB ──▶ BFF ──api-key──▶ Vendor
                          validates JWT, checks scope              verifies JWT again, checks scope
```

- Auth0 issues access tokens signed with an asymmetric key; API Gateway validates signature, issuer, audience, expiry, and required scopes at the edge.
- The BFF is reachable only through the VPC link and a private ALB, and also requires a gateway API key.
- The UI is served from S3 via CloudFront, which routes `/api/*` to API Gateway under the same domain, so there is still no CORS. The BFF runs as containers on ECS Fargate.
- Defence in depth: the BFF verifies the JWT again against cached JWKS and enforces `create:quotes`.
- Service identity uses API keys at both hops; the user's token is never forwarded to the vendor.
- **Secrets:** the vendor API key and the gateway API key live in AWS Secrets Manager. The BFF reads them at startup through its IAM role (no credentials in config), caches them, and refreshes on rotation. Rotation is scheduled, and keys are never in environment files, images, logs, or source control.

**Evolution for a lending platform:** move the web BFF to the token handler (BFF) pattern from current IETF best practice guidance for browser apps. The BFF becomes the confidential OAuth client, keeps tokens server side, and gives the browser only an HttpOnly, Secure, SameSite session cookie. Costs: CSRF protection and a shared session store. It runs either in front of the gateway or as a token handler hosted in the gateway. Mobile clients keep bearer tokens in the Keychain or Keystore, ideally sender constrained with DPoP.

## 15. Scope and next steps

**Built:** vendor OpenAPI contract (first), core flow, UI validation with tests, auth stub with real scope checks, timeouts, retries with backoff, idempotency, tests for these.

**Built if time allows:** vendor response validation, circuit breaker, UI tests for loading and error states, Docker Compose.

**Next steps:** extract the Quote Service; real Auth0 integration; token handler BFF; secrets manager; contract tests against the real vendor; connect traces, metrics and logs to Datadog with dashboards and alerts; rate limiting.
