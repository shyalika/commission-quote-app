# Commission Quote App

## Quick start

```bash
cp mock-vendor/.env.example mock-vendor/.env
cp bff/.env.example bff/.env
./start-all.sh
```

Then open http://localhost:5173. Requires Java 21 or newer and Node 20 or newer. Ctrl+C stops everything.

## Overview

Staff enter loan details and get a commission quote from a third party vendor, the Commission Quote API, with clear loading and error states. The vendor API is still under construction, so a mock vendor implements the agreed contract.

Three parts, each building and testing on its own:

- `web/`: the UI, plain TypeScript and Vite.
- `bff/`: the backend for frontend, Spring Boot. It holds the vendor API key and talks to the vendor.
- `mock-vendor/`: a stand in for the vendor, Spring Boot, implementing [`docs/vendor-openapi.yaml`](docs/vendor-openapi.yaml).

The spec is [`docs/design.md`](docs/design.md); this README summarises it and describes what was built.

## Architecture

```
Browser (web)  ──Bearer token──▶  BFF  ──api-key + Idempotency-Key──▶  Mock vendor
port 5173 (Vite proxy /api)       8080                                   8081
```

- The browser calls relative `/api` URLs. The Vite dev server proxies them to the BFF, so there is one origin and no CORS configuration.
- The BFF has four packages: `web` (HTTP, validation, errors), `quote` (`QuoteService`, no HTTP types), `vendor` (client, timeouts, retries), `security` (dev token stub, scope check, startup guard).
- The BFF never imports code from `mock-vendor`. It knows the vendor only through `VENDOR_BASE_URL` and `VENDOR_API_KEY`, so switching to the real vendor is a config change.
- Every request gets an `X-Correlation-Id`. It appears on every BFF log line, is forwarded to the vendor, and is returned in error responses as the reference shown to the user.

## Prerequisites

- Java 21 or newer. The code targets Java 21; it was built and tested on JDK 26.
- Node 20 or newer.
- No Maven install: each Java part includes the Maven wrapper (`./mvnw`).

## Configuration

Each Java part reads its settings from a `.env` file, loaded by `start-all.sh`. The `.env` files are git ignored; only the `.env.example` files are committed.

```bash
cp mock-vendor/.env.example mock-vendor/.env
cp bff/.env.example bff/.env
```

Set the same `VENDOR_API_KEY` in both files; any value works locally. `bff/.env` also sets `SPRING_PROFILES_ACTIVE=local`, which turns on the dev token stub (see [Dev tokens](#dev-tokens)).

The mock vendor simulates failures. These variables can be added to `mock-vendor/.env`:

| Variable | Default | Effect |
|---|---|---|
| `VENDOR_SIMULATION_ENABLED` | `true` | `false` makes every call fast and successful |
| `VENDOR_SIMULATION_FAILURE_RATE` | `0.2` | Share of calls answered with 500 or 503 |
| `VENDOR_SIMULATION_SLOW_RATE` | `0.1` | Share of calls delayed by 7s, past the BFF's 5s read timeout |

With the defaults, the BFF's retries hide almost every failure, so the UI rarely shows an error. To see the error screen, set `VENDOR_SIMULATION_FAILURE_RATE=0.9` (most quotes end in 502), or set the failure rate to 0 and `VENDOR_SIMULATION_SLOW_RATE=0.9` (some end in 504).

## Running

### All services

```bash
./start-all.sh
```

This starts the mock vendor, the BFF and the UI, installing the UI's dependencies on the first run. Open http://localhost:5173. Ctrl+C stops all three.

### Mock vendor

```bash
cd mock-vendor
set -a; . ./.env; set +a
./mvnw spring-boot:run
```

Listens on port 8081. It refuses to start without `VENDOR_API_KEY`.

### BFF

```bash
cd bff
set -a; . ./.env; set +a
./mvnw spring-boot:run
```

Listens on port 8080. It refuses to start without `VENDOR_BASE_URL` or `VENDOR_API_KEY`, and under the `prod` profile.

### Web

```bash
cd web
npm install
npm run dev
```

Serves the UI on port 5173 and proxies `/api` to the BFF on 8080.

## Testing

One command per part:

```bash
(cd mock-vendor && ./mvnw verify)
(cd bff && ./mvnw verify)
(cd web && npm test)
```

Everything at once (run `npm install` in `web` first):

```bash
(cd mock-vendor && ./mvnw -q verify) && (cd bff && ./mvnw -q verify) && (cd web && npm test)
```

- **mock-vendor:** api-key checks, the calculation per risk band, payload validation, idempotency, failure and slow modes, and a contract test against `docs/vendor-openapi.yaml`.
- **bff:** validation and error bodies, authentication and scopes (401, 403, 200), the startup guard, and WireMock tests for vendor success, error mapping, retries, timeouts and `Idempotency-Key` reuse.
- **web:** field validation, loading, result and error states, Try again with the same `Idempotency-Key`, and clearing a stale quote after an edit.

## Dev tokens

Locally, under the `local` profile, the BFF accepts two fixed bearer tokens:

| Token | Scopes | `POST /api/v1/quotes` |
|---|---|---|
| `dev-staff` | `create:quotes` | 200 |
| `dev-readonly` | none | 403 |
| none or anything else | | 401 |

The UI has a "Signed in as" selector for these. It exists only in the dev server; a production build doesn't contain it.

```bash
curl -s -X POST localhost:8080/api/v1/quotes \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer dev-staff' \
  -d '{"loanAmount": 500000.00, "loanTermInMonths": 360, "riskBand": "MEDIUM"}'
# {"quoteId":"...","commissionRate":0.0075,"totalCommission":3750.00}

curl -s -X POST localhost:8080/api/v1/quotes \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer dev-readonly' \
  -d '{"loanAmount": 500000.00, "loanTermInMonths": 360, "riskBand": "MEDIUM"}'
# 403: {"title":"Access denied","retryable":false,"correlationId":"..."}
```

With no profile active, there is no way to authenticate, so every API call gets 401. Under the `prod` profile the BFF refuses to start, because prod authentication is not built yet and the dev stub must never run there.

## Key decisions and trade offs

- **Contract first.** The vendor contract was written as OpenAPI before any code, and a test checks the mock's responses against it, so the mock cannot drift. Cost: contract changes start in the spec.
- **The BFF holds the vendor key and passes values through.** The browser never sees the key, and the BFF never recalculates the commission. Cost: the BFF also holds the business logic for now; extracting a separate Quote Service is planned before production.
- **Retries only where they can help.** Vendor 5xx, timeouts and connection errors are retried (3 attempts, exponential backoff from 300ms with full jitter, capped at 2s); 4xx never is. Total time never exceeds the 20s deadline: a new attempt starts only if a full attempt (7s) still fits, so if every attempt times out, only two run.
- **One Idempotency-Key per submission.** The UI creates it, Try again reuses it, any edit replaces it, and every retry sends it, so retries never create duplicate quotes. Cost: the real vendor would need to support this proposed extension.
- **Generic errors.** Every error is a ProblemDetail with a generic title, `retryable` and `correlationId`. The cause is only in the BFF logs; field messages are returned for validation errors only.
- **Fail closed.** Authorization rules are per endpoint and end in `denyAll()`, so a new endpoint is blocked until its rule is written. No profile means 401; `prod` refuses to start.
- **Validation in both places.** The UI validates for fast feedback; the BFF stays the source of truth.
- **Exact money.** `BigDecimal` throughout the Java services, HALF_EVEN rounding, and strict JSON that rejects strings as numbers and fractional terms. The UI does no arithmetic on money.
- **Plain TypeScript UI.** No framework, three small modules (validation, API client, rendering). Cost: more manual DOM code, acceptable for one form.
- **HTTP/1.1 to the vendor.** On plain `http` the JDK client tries an HTTP/2 upgrade that some servers reject on a POST. HTTP/2 gains little for one small request per quote.
- **Resilience4j core module.** Used directly, configured from the BFF's own properties.

## Assumptions

These are not in the brief and would be confirmed with the business or the vendor:

- The vendor endpoint is `POST /v1/commission-quotes`.
- `commissionRate` is a decimal fraction: 0.0075 means 0.75%.
- BFF validation bounds: loan amount 1,000 to 10,000,000 with at most 2 decimal places, term 1 to 360 months.
- The vendor's error body is `{ "code", "message" }`.
- The vendor would support `Idempotency-Key`, keeping keys for 1 hour. This is proposed as an extension to the contract.
- The mock's rates (LOW 0.50%, MEDIUM 0.75%, HIGH 1.00%) are illustrative; the real vendor owns the calculation.

## Production architecture

Documented, not built. Details are in [section 14 of the design](docs/design.md).

```
Browser ──JWT──▶ WAF ──▶ AWS API Gateway (HTTP API) ──VPC link──▶ private ALB ──▶ BFF ──api-key──▶ Vendor
                          validates JWT, checks scope              verifies JWT again, checks scope
```

Secrets live in AWS Secrets Manager, and the BFF runs on ECS Fargate behind a private ALB. For a lending platform, the next step would be the token handler pattern, where tokens stay on the server and the browser holds only an HttpOnly session cookie.

## With more time

- Extract the Quote Service from the BFF, so the BFF holds no vendor secrets.
- Real Auth0 integration in the `prod` profile, then the token handler pattern.
- AWS Secrets Manager for both keys.
- Contract tests against the real vendor.
- Traces, metrics and JSON logs connected to Datadog, with dashboards and alerts (planned in section 10 of the design).
- Rate limiting.
- Stretch items not built: a circuit breaker, validation of the vendor's 200 responses, and Docker Compose.
- Gaps from the build: the CI workflow listed in the design, a test for the 20s deadline, and per service log files from `start-all.sh`.

## Time spent

About 6 hours: around 2 on design (the spec, the vendor contract, and decisions on authentication, resilience and idempotency) and around 4 on the phased build, review of every change, and this README.

## AI usage

AI tools were used throughout this project. All design and scope decisions were made by the developer.

### Tools used

- **Claude (chat):** design discussion and review of each phase against the spec.
- **Claude Code:** implementation, phase by phase, guided by `CLAUDE.md`.

### How the tools were used

- **Design first:** architecture, authentication, retries, idempotency, error handling and scope were worked through in collaboration with Caulde before any code was written. The outcome is `docs/design.md`.
- **Contract first:** the vendor contract (`docs/vendor-openapi.yaml`) was written before any code, and a test checks the mock vendor against it.
- **Phased implementation:** each phase started in plan mode. Every change was reviewed and approved before being written, tests were run, and every commit was made by the developer.
- **Guardrails:** `CLAUDE.md` defines the rules Claude Code works within: BigDecimal for money, the vendor API key never in the UI or logs, opaque errors, deny by default security, and no new dependencies without approval.

### Review, changes and rejections

- Code moved out of a company specific package name.
- Error handling fixed so framework errors (404, 405, 415) are no longer turned into a retryable 500, with a test added.
- An assumed UUID format for `quoteId` removed from the contract, and the vendor's own input rules added.
- URL based, deny by default security rules chosen over method annotations.
- The UI changed to clear a displayed quote when the form is edited, so a stale figure cannot be mistaken for a current one.
- Each new dependency (the OpenAPI validator, the RestClient starter) was proposed by Claude Code and approved or rejected deliberately.

### Decisions made by the developer

- Authentication modelled on a production pattern: the gateway validates the JWT and downstream services enforce scopes.
- Idempotency keys and exponential backoff with jitter, so retries are safe.
- Business logic kept inside the BFF for time, with extraction planned before production.
- Production authentication, the gateway, observability and Docker documented rather than built.
- A plain TypeScript UI with no framework, kept deliberately thin.
