# Service Level Objectives (ARV Shop Manager)

> Standards: pipeline_ops Module 4 (monitoring, SLOs). Start simple; tighten with data.

## Signals we emit today

- **Structured JSON logs** to stdout (one line per request): `ts`, `level`, `logger`,
  `msg`, `request_id`, `method`, `path`, `status`, `duration_ms`. Plus `startup` and
  `unhandled_error` events. (`app/core/observability.py`)
- **Correlation id** per request: propagated from `X-Request-ID` (or generated) and returned
  in the `X-Request-ID` response header; included in 500 bodies for support reference. The
  front-end surfaces it on `ApiError.requestId`.

## SLOs (initial targets — review monthly)

| SLO | Target | Measured from |
|-----|--------|---------------|
| **Availability** (owner API) | 99.5% of requests non-5xx / 30d | `status` in request logs |
| **Latency** (read endpoints) | p95 `duration_ms` < 400ms | `duration_ms` for GET routes |
| **Latency** (write endpoints) | p95 `duration_ms` < 800ms | `duration_ms` for POST/PUT/DELETE |
| **Auth abuse** | <1% of auth requests rate-limited (429) | `RATE_LIMITED` count |

**Error budget:** at 99.5% availability the monthly budget is ~3.6h of 5xx. Burning >50%
of the budget in a week → freeze risky changes and investigate.

## How to derive these from logs

The JSON log lines are designed to be queried directly (e.g. by the log platform):
availability = `count(status<500)/count(*)`; latency percentiles from `duration_ms`
grouped by method/path; rate-limit ratio from `status==429` on `/auth/*`.

## Gaps / next (tracked)

- Ship logs to a platform with dashboards + alerting (e.g. Grafana/Loki, Datadog).
- Client-side error reporting (e.g. Sentry) keyed by `request_id` for end-to-end traces.
- **FCM push** for the owner app (low-stock / new-order alerts) — needs the TWA + FCM
  credentials; deployment-gated (mobile roadmap Phase 18).
- Uptime/synthetic checks against `/api/v1/health`.
