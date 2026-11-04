# Commission Quote App

`docs/design.md` is the spec and `docs/vendor-openapi.yaml` is the vendor contract. Build against them. If a task conflicts with either, stop and ask rather than choosing.

## Git and attribution

- Do not run `git commit` or `git push`. The developer reviews every change and commits it herself.
- Never add Co-Authored-By trailers, "Generated with Claude Code" lines, session links, or any other AI attribution to commit messages, code, comments, or docs.

## Working style

- Work only on the phase you are asked to do. Do not start the next phase.
- Use plan mode first; wait for approval before writing code.
- End each phase with all tests passing, then summarise what changed and anything that deviates from the spec.
- Keep changes small and readable. Prefer clear code over clever code. No unused abstractions, no speculative features.
- Do not add dependencies, frameworks or tooling beyond what the spec calls for without asking first.

## Contract first

- `docs/vendor-openapi.yaml` is the source of truth for the vendor API. Change the spec first, then the code.
- The mock vendor must match the spec; a test checks its responses against it.

## Security guardrails

- The vendor `api-key` lives only in BFF config. Never in the `web` folder, never in a `VITE_` variable, never in source, never logged.
- No secrets committed. Only `.env.example` with placeholder values; real `.env` files are git ignored.
- Never log the vendor api-key, the `Authorization` header, bearer tokens, or full request or response bodies.
- Error responses to the browser never reveal the cause. Use `ProblemDetail` with a generic title, `retryable`, and `correlationId`. No stack traces or vendor details in responses.
- The local auth stub must be impossible to run under the `prod` profile (startup guard).
- `POST /api/v1/quotes` always requires the `create:quotes` scope, in every profile.
- Compare secrets with `MessageDigest.isEqual`, never `String.equals`.
- No CORS configuration. The UI uses relative `/api` URLs through the Vite dev proxy.

## Correctness guardrails

- Money and rates: `BigDecimal` only, never `double` or `float`. Rounding HALF_EVEN, 2 decimal places for amounts.
- The BFF never recalculates the commission; it passes the vendor's values through.
- Retries only on vendor 5xx, timeouts and connection errors; never on 4xx. Every retry reuses the same `Idempotency-Key`.
- Validate input in both the UI and the BFF.

## Structure

- `web/`: plain TypeScript + Vite, no UI framework (no React, Vue or Angular), plain CSS. Keep it small: form handling, validation, API client and rendering in a few clearly named modules.
- `bff/`: Spring Boot, packages `web`, `quote`, `vendor`, `security`. `QuoteService` has no HTTP or Spring MVC types.
- `mock-vendor/`: Spring Boot, separate process, port 8081.
- `bff` never imports from `mock-vendor`. It reaches the vendor only via `VENDOR_BASE_URL`.

## Scope

- Do not build observability beyond the correlation ID (no tracing, metrics or JSON logging dependencies). Those are documented in the spec as the production plan.
- Docker Compose, circuit breaker and vendor response validation are stretch items. Only build them when asked.

## Testing

- Java: JUnit 5 and AssertJ; WireMock for BFF tests against the vendor; Spring Security test support for auth tests.
- Web: Vitest with jsdom and DOM Testing Library.
- Randomness in the mock vendor sits behind an injectable interface so tests are deterministic.
- Each test checks one behaviour and has a name that says what it proves.
