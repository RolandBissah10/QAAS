# QAAS — QA as a Service

An automated web quality-assurance platform. Point it at any URL and it crawls the application, discovers pages and UI elements, generates and executes tests with AI assistance, detects bugs, captures HTTP session recordings, and produces reports — all organized around multi-tenant projects with team membership and role-based access.

---

## Features

### Analysis Pipeline
- **Web crawler** — BFS Playwright crawler that discovers pages from links and the target site's sitemap, stays on the exact application origin, and supports form-based login for authenticated areas; it does not guess common routes and fails the analysis when configured login cannot be completed
- **Bounded analysis execution** — limits concurrent pipeline workers and queued analyses; overloaded manual starts return HTTP 503 rather than leaving an analysis stuck in `RUNNING`, while interrupted analyses are marked failed on restart
- **AI test generation** — sends relevant page content and up to 80 discovered controls to the configured AI provider, creating multiple page-level scenarios plus up to three cross-page user journeys from the discovered page inventory
- **Test execution** — runs generated scripts inside the shared authenticated headless Chromium context; journeys are limited to pages discovered in that analysis and record pass/fail/error per test
- **Bug detection** — auto-creates bug records from failed executions, assigns severity (CRITICAL / HIGH), and tracks lifecycle (OPEN → RESOLVED)
- **Deep checks** — optional second-pass inspection covering security headers, accessibility, performance metrics, console errors, and broken links
- **API endpoint discovery** — network interception during crawling to catalog observed REST endpoints with method, URL, and status code; API tests do not follow redirects
- **UI element discovery** — HTML parsing to catalog interactive elements (buttons, inputs, forms) per page
- **Report generation** — compiles results into downloadable JSON or PDF reports
- **HAR recordings** — captures full HTTP traffic during analysis; also supports manual HAR upload for replay inspection with stop/cancel control

### Project Management
- Multi-project workspace with per-project settings (max pages, crawl exclusions, auth credentials)
- Team membership with roles: **OWNER**, **TESTER**, **VIEWER**
- Scheduled analyses via cron expressions

### Platform
- JWT authentication with access-token refresh
- Real-time analysis progress via Server-Sent Events
- Email notifications on analysis completion or failure
- Dashboard with trend charts (analysis history, bug severity, pass/fail rates)
- Swagger UI at `/swagger-ui.html`

---

## Architecture

```
┌─────────────────────────────────────────────────┐
│                  React Frontend                  │
│  Vite · React Router · TanStack Query · Recharts │
└────────────────────┬────────────────────────────┘
                     │ REST / SSE
┌────────────────────▼────────────────────────────┐
│              Spring Boot Backend                 │
│  Spring Security · Spring Data JPA · Async       │
│                                                  │
│  analysis → crawler → generator → execution      │
│  deeptest → bug → recording → report             │
└────────────────────┬────────────────────────────┘
                     │
        ┌────────────▼────────────┐
        │       PostgreSQL         │
        └─────────────────────────┘
```

---

## Tech Stack

### Backend
| Technology | Version | Purpose |
|---|---|---|
| Spring Boot | 3.3.5 | Application framework |
| Java | 17 | Runtime |
| Spring Security + jjwt | 0.12.6 | JWT authentication |
| Spring Data JPA | — | ORM / database access |
| PostgreSQL | 16 | Primary database |
| Microsoft Playwright | 1.44.0 | Headless browser (crawl + test execution) |
| jsoup | 1.16.1 | HTML parsing for UI element discovery |
| OpenPDF | 1.3.34 | PDF report generation |
| REST-Assured | 5.5.0 | API test execution |
| Spring Mail | — | SMTP email notifications |
| SpringDoc OpenAPI | 2.6.0 | Swagger UI |
| Anthropic Claude API | claude-haiku-4-5 | AI test generation |
| Testcontainers | 1.20.3 | Integration testing |

### Frontend
| Technology | Version | Purpose |
|---|---|---|
| React | 18.3.1 | UI framework |
| TypeScript | 5.6.3 | Type safety |
| Vite | 5.4.10 | Build tooling |
| React Router DOM | 6.27.0 | Client-side routing |
| TanStack Query | v5 | Data fetching and caching |
| Axios | 1.7.7 | HTTP client |
| Recharts | 3.9.2 | Dashboard charts |
| Tailwind CSS | 3.4.14 | Styling |
| Lucide React | — | Icons |

---

## Project Structure

### Backend (`src/main/java/com/qaas/`)

| Package | Description |
|---|---|
| `analysis` | Pipeline orchestration — runs crawl → generate → execute → deep test → report as an async job with SSE progress and cancellation |
| `crawler` | BFS Playwright web crawler restricted to the target origin, with sitemap support, discovered-link extraction, and network interception |
| `playwright` | Playwright wrappers: generic browser automation, crawler service, and scripted test runner |
| `generator` | AI test generation via Claude — produces Playwright and REST-Assured scripts from page HTML |
| `apitest` | REST-Assured test generation and execution for discovered API endpoints |
| `execution` | Test execution engine — runs scripts in a shared browser context, persists results |
| `discovery` | UI element discovery via HTML parsing (jsoup) |
| `bug` | Bug detection from failed tests; severity assignment and status lifecycle |
| `deeptest` | Deep quality checks: security headers, accessibility, performance, console errors, broken links |
| `report` | Report compilation to JSON or PDF (OpenPDF) |
| `recording` | HAR session recordings — auto-captured during analysis and manually uploaded; entry-level HTTP inspector with stop/cancel support |
| `page` | Discovered page storage with URL, title, and page-type classification |
| `evidence` | Screenshot capture and storage during test runs |
| `ai` | Claude API client + HTML summarizer (strips boilerplate before sending to LLM) |
| `auth` | JWT login, registration, token refresh |
| `security` | JWT filter chain, token signing/validation |
| `user` | User management, profile updates, admin listing |
| `project` | Project CRUD, team membership, per-project settings |
| `dashboard` | Cross-analysis KPI aggregation and trend data |
| `notification` | SMTP email notifications on analysis events |
| `config` | Swagger, CORS, async thread pool, app properties |
| `exception` | Global exception handler mapping domain exceptions to structured API errors |
| `common` | Shared utilities: `BaseEntity`, `PagedResponse<T>`, JSON helpers |

### Frontend (`frontend/src/pages/`)

| Page | Description |
|---|---|
| `AuthPage` | Login and registration |
| `DashboardPage` | Project-filtered dashboard: trend charts, bug severity breakdown, pass/fail rates |
| `AnalysisPage` | Launch analyses (URL + deep-test toggle), view history, real-time SSE progress bar |
| `AnalysisDetailPage` | Per-analysis drill-down: pages, tests, executions, bugs, deep findings |
| `ProjectsPage` | Project creation, settings, team member management |
| `PagesPage` | Crawled pages list with page-type classification |
| `TestsPage` | AI-generated test script browser |
| `BugsPage` | Bug tracker with severity and status filtering |
| `ExecutionsPage` | Test execution history with pass/fail/error breakdown |
| `ApiEndpointsPage` | Discovered API endpoints with method badges and status codes |
| `UIElementsPage` | Discovered UI elements per page |
| `ReportsPage` | Download or preview generated reports (PDF / JSON) |
| `RecordingsPage` | HAR recording viewer — request/response inspector; manual upload + auto-capture with stop control |
| `ProfilePage` | User profile editing |
| `UsersPage` | Admin-only user management |

---

## Getting Started

### Prerequisites
- Java 17+
- Node.js 20+
- PostgreSQL 16 (or use Docker Compose)
- A Claude API key from [console.anthropic.com](https://console.anthropic.com)

### 1. Clone the repository

```bash
git clone <repo-url>
cd QAAS
```

### 2. Start the database

```bash
docker compose up -d postgres
```

Set `POSTGRES_PASSWORD` in the environment first; Compose intentionally has no default
database password.

Or point the app at an existing PostgreSQL instance. Fresh schemas are created by Flyway;
existing non-empty schemas without Flyway history are baselined at version 1 and validated
by Hibernate without automatic schema changes.

### 3. Configure the backend

Set environment variables or configure an active Spring profile:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/qaas
    username: qaas
    password: ${SPRING_DATASOURCE_PASSWORD}

qaas:
  jwt:
    secret: <at least 32 random bytes>
  crypto:
    secret: <at least 32 random bytes; keep stable to decrypt stored crawl credentials>
  claude:
    api-key: sk-ant-...       # required for AI test generation
  mail:
    enabled: false            # set to true and configure SMTP for email alerts
```

### 4. Run the backend

```bash
./mvnw spring-boot:run
```

Backend starts on **port 8090**. Swagger UI: `http://localhost:8090/swagger-ui.html`

### 5. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend starts on **http://localhost:5173** and proxies API calls to port 8090.

---

## Docker Compose (full stack)

```bash
docker compose up --build
```

Before starting Compose, provide `POSTGRES_PASSWORD`, `QAAS_JWT_SECRET`, and
`QAAS_CRYPTO_SECRET` in the environment. The JWT and crypto secrets must each contain at
least 32 UTF-8 bytes; generate them with a cryptographically secure secret generator and
keep the crypto secret stable, or previously encrypted project credentials cannot be read.
SMTP notifications are disabled by default; set `QAAS_MAIL_ENABLED=true` and supply
`QAAS_MAIL_USERNAME` and `QAAS_MAIL_PASSWORD` only when needed. Do not commit credentials
or secrets to the Compose file or repository.

This starts:
- **PostgreSQL 16** on port 5432
- **Backend** on port 8090

Set `QAAS_CLAUDE_API_KEY` in your environment before starting if AI test generation is
needed. If the SMTP credential previously present in this repository was active, revoke
and replace it; removing it from the working file does not invalidate the credential or
remove it from prior Git history.

### Database migrations and backups

Flyway creates a fresh database from `src/main/resources/db/migration`. Hibernate runs
with `ddl-auto: validate` and fails startup if the database schema does not match the
entities; it will not silently change a production schema.

For a database that already contains QAAS tables but has no Flyway history, Flyway
baselines the non-empty schema at version 1 and does not replay the initial schema
creation. Back up the database before the first deployment with Flyway enabled. After
baseline, deliver future schema changes as new, ordered Flyway migrations.

Use PostgreSQL client tools from a trusted host to create and restore a custom-format
backup. Test restores before relying on backups:

```powershell
pg_dump --format=custom --file=qaas-backup.dump --host=localhost --port=5432 --username=qaas qaas
pg_restore --verbose --no-owner --dbname=qaas_restore --host=localhost --port=5432 --username=qaas qaas-backup.dump
```

`qaas_restore` should be a separate, empty database for restore testing; do not restore
over the live database unless that operation is explicitly planned and backed up. Store
backup files securely because they contain application data.

---

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `PORT` | `8090` | Backend server port |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/qaas` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `qaas` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | — | Required database password |
| `QAAS_JWT_SECRET` | — | Required signing secret, at least 32 UTF-8 bytes |
| `QAAS_CRYPTO_SECRET` | — | Required stable secret for encrypting project crawl credentials |
| `QAAS_CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | Exact allowed browser origins; wildcards are rejected |
| `QAAS_AI_PROVIDER` | `auto` | AI provider: `claude`, `gemini`, or `auto` (prefers Claude when both keys are configured) |
| `QAAS_CLAUDE_API_KEY` | — | Optional Anthropic Claude API key |
| `QAAS_GEMINI_API_KEY` | — | Google Gemini API key |
| `QAAS_GEMINI_MODEL` | `gemini-3.5-flash-lite` | Gemini model ID, without the `models/` prefix |
| `QAAS_MAIL_ENABLED` | `false` | Enable SMTP email notifications |
| `QAAS_MAIL_HOST` | — | SMTP host |
| `QAAS_MAIL_PORT` | — | SMTP port |
| `QAAS_MAIL_USERNAME` | — | SMTP username |
| `QAAS_MAIL_PASSWORD` | — | SMTP password |

---

## API Overview

All endpoints are secured with JWT. Include `Authorization: Bearer <token>` on every request after login.

| Resource | Base Path |
|---|---|
| Auth | `POST /api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout` |
| Projects | `/api/projects`, `/api/projects/{id}/members`, `/api/projects/{id}/settings` |
| Environments | `/api/projects/{id}/environments` (list/create), `/api/projects/{id}/environments/{environmentId}` (update/delete) |
| Analysis | `/api/analysis/start`, `/api/analysis/{id}`, `/api/analysis/{id}/stop`, `/api/analysis/{id}/progress` (SSE) |
| Pages | `/api/pages/analysis/{id}` |
| Tests | `/api/tests/analysis/{id}` |
| Test Suites | `/api/projects/{id}/tests`, `/api/projects/{id}/test-suites` (create/list), `/api/projects/{id}/test-suites/{suiteId}` (delete), `/api/projects/{id}/test-suites/{suiteId}/tests` (replace membership), `/api/projects/{id}/test-suites/{suiteId}/runs` (queue/list history), `/api/projects/{id}/test-suites/{suiteId}/runs/{runId}` (status/results) |
| Executions | `/api/executions/analysis/{id}` |
| Bugs | `/api/bugs/analysis/{id}` |
| API Endpoints | `/api/api-endpoints/analysis/{id}` |
| UI Elements | `/api/ui-elements/page/{id}` |
| Reports | `/api/reports/analysis/{id}` |
| Recordings | `/api/recordings`, `/api/recordings/upload`, `/api/recordings/capture`, `/api/recordings/{id}/stop` |
| Dashboard | `/api/dashboard/summary`, `/api/dashboard/trends` |
| Screenshots | `/api/screenshots/analysis/{id}` |
| Users | `/api/users`, `/api/users/me` |

Full interactive docs available at `/swagger-ui.html`.

Suite runs execute in the background and return HTTP 202 with a run ID. Poll the run
endpoint for `QUEUED`, `RUNNING`, `PASSED`, or `FAILED` status and per-test results.
Suite membership is project-scoped; tests from another project are rejected.
Only project owners can delete suites. Deletion also removes that suite's run history
but does not delete the generated tests; a suite with an active run cannot be deleted.
The web app provides suite management at **Test Suites** in the navigation. Project owners
can configure public HTTP(S) origins at **Environments** and select one for a suite run.
Environment credentials use the configured crypto secret, are encrypted at rest, and are
never returned by the API or included in run results. The origin may not contain a path,
query, or fragment; each generated test's path and query are retained when targeting it.

---

## Roles and Permissions

| Action | OWNER | TESTER | VIEWER |
|---|---|---|---|
| View all data | ✓ | ✓ | ✓ |
| Start / stop analysis | ✓ | ✓ | — |
| Upload recordings / capture | ✓ | ✓ | — |
| Manage project settings | ✓ | — | — |
| Manage team members | ✓ | — | — |
| Delete resources | ✓ | ✓ | — |