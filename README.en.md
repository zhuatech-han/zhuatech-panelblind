[中文](README.md) | [English](README.en.md)

# PanelBlind · Blinded Product Evaluation and Rating Workflow

![ZhiHua Technology logo](frontend/public/brand/logo.jpg)

**ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)** · [Official website](https://www.zhuatech.cn/).

**Public source for learning / non-commercial use.** The project's own code is governed by the [ZhuaTech Non-Commercial Source License 1.0](LICENSE). It is limited to personal learning, technical research and non-commercial exchange. Commercial use requires prior written authorization from Shanghai Rujing Zhihua Information Technology Co., Ltd. Third-party components retain their own copyrights and licenses. Publicly readable source is not an OSI-approved open-source license.

## Introduction and intended scenarios

PanelBlind is a Java 21 / Spring Boot / Vue 3 / MySQL application for internal comparisons of packaging appearance, product design and material feel. Its workflow covers protocol design, independent approval, sample-identity custody, blinded presentation, personal ratings, completeness review, sealing and two-person unblinding. It demonstrates identity separation, actual database persistence and explicit role constraints at both API and interface levels.

Detailed documentation is currently in Chinese: [User manual](docs/操作手册.md), [API reference](docs/接口说明.md), [Architecture and data](docs/架构与数据.md), and [Security](SECURITY.md).

## From protocol to frozen results

```text
Draft / returned → Submit → Independently approve and generate blind codes
  → Start rating → Each rater acknowledges → Scores or explicit missing responses
  → Submit and lock → Independent completeness review: accept / return / exclude
  → Finish normally or abort → Independently seal → Custodian requests unblinding
  → Assigned independent reviewer authorizes unblinding → Results freeze
```

The [public abstract of ISO 11136](https://www.iso.org/standard/50125.html) provides background on product-preference feedback in controlled settings. PanelBlind defines its own workflow and presentation algorithm, does not copy the standard's text, and does not claim compliance, certification or valid consumer research.

## Implemented functions

| Area | Actual behavior |
|---|---|
| Protocols | Unique reference, responsible department, category, instructions and assigned reviewer/custodian; drafts/returned protocols are editable, but reference/department are fixed |
| Samples and scales | 2–8 internal samples; 1–8 integer scales bounded within 0–10, explicit endpoints and at least one required scale |
| Internal roster | 4–32 distinct accounts, a multiple of sample count; protocol editors, custodian and reviewer cannot act as raters |
| One-time presentation | Approval generates random three-digit blind codes and cyclic presentation sequences; each sample occurs equally often in each position across the complete roster |
| Identity separation | Ordinary details omit actual sample names and internal sample IDs; a separate identity-key endpoint checks roles and audits reads |
| Rater workspace | Only the rater's own sheet, blind codes, order and scales; acknowledgment first, then integer scores or explicit missing responses, never missing-as-zero |
| Sealing and completeness | Unsubmitted ratings are readable only by their author; submitted sheets lock; assigned reviewer accepts completeness, returns or excludes without replacing subjective scores |
| Final sealing/unblinding | Normal completion requires at least two accepted sheets and all sheets terminal; aborted outcome remains separate; two-person unblinding makes results immutable |
| Descriptive statistics | After unblinding: valid N, explicit missing count, mean, minimum and maximum by sample/scale; withdrawn/excluded records are retained but not included |
| Queries and exports | Authorized search, status filters, sorting, pagination, JSON and CSV; exports preserve sealing and personal scope and protect against CSV formula injection |
| Accounts/admin | BCrypt passwords, session login/logout/password changes, roles, departments, menus, permission catalogs, product dictionaries, settings and audit |
| Interface | Chinese/English, desktop/mobile layouts, empty states, actionable errors, actual statistics and a ZhiHua consultation entry |

## Business users and administrators

The business interface supports design, identity custody, personal ratings, completeness review, final sealing, unblinding and authorized results. The administration interface maintains accounts, roles, departments, menus, permission names, dictionaries and settings; administrator status does not bypass business assignments or confidentiality.

| Role | Default scope and restrictions |
|---|---|
| Administrator | Identity/permission administration and authorized protocol reading; does not automatically gain every rating or identity mapping |
| Protocol designer | Department definitions, samples, scales, roster, workflow and exports; cannot independently approve a protocol previously edited by the designer |
| Independent reviewer | Assigned approval, submitted-sheet completeness review, sealing and unblinding authorization; no identity mapping before unblinding |
| Sample custodian | Assigned real-sample/blind-code mapping and unblinding requests; no authority to approve ratings |
| Internal rater | Assigned protocols and own sheet only; no other raters' sheets, full personnel catalog or identity mapping |

APIs read live account, role and data scopes on every request. Roster membership takes priority over ALL scope, administrator status and mixed permissions: a participating rater always receives only their own sheet. After unblinding, authorized protocol members can read frozen aggregates, but cannot use that access to obtain another rater's details. Reviewers/custodians outside the department gain access only through explicit assignment.

Backend checks protect approval, scoring and unblinding; hidden menus are not authorization. Password hashes are excluded from account responses. Disabling, password changes and revocation immediately affect applicable sessions/actions.

## Presentation, scoring and statistics

- `CYCLIC-SHUFFLE-1` uses server-side `SecureRandom` permutations of samples and raters, followed by cyclic shifts for balanced positions and independently random three-digit codes. Codes are unique within a protocol but may recur across protocols. The random source is not published; frozen database rows and digests record the layout.
- Position balance applies only to the complete initial roster. First-order carryover balance and balance after withdrawal are not guaranteed. The application does not determine headcount, study power, product suitability or consumer representativeness.
- Scores are integers within the scale. A number and missing reason are mutually exclusive. Every required scale for every presented sample needs an explicit response; optional scales may remain blank.
- Submitted sheets cannot be edited by their author until returned. Revisions and resubmission leave an operation trail. Accepted/withdrawn/excluded sheets are terminal; entered data is retained.
- The reviewer checks completeness and protocol execution, does not replace unpopular scores with favorable ones and does not score for the rater.
- Normal ending requires all sheets accepted or withdrawn and at least two accepted sheets. An abort can have zero accepted sheets, but pending reviews must be handled first. Aborted outcomes are not presented as normal completion.
- Means include only nonmissing scores from accepted sheets and display four decimal places. Missing responses do not enter the denominator. All-missing means/ranges are null. There is no ANOVA, significance test, ranking recommendation or automatic product selection.
- After sealing, the assigned custodian requests unblinding and the independent reviewer approves it. Seal/result digests persist; protocol, roster, scores and mapping freeze after unblinding.
- Organizers must prevent sample identities leaking through names, scale descriptions, comments and physical labels. API separation cannot eliminate physical recognition, prior knowledge or database-administrator access.

## Actual running pages

Screenshots are from the running system with isolated acceptance `TEST` records. The source release starts with an empty business database.

| Login and rater workspace | Protocols and blind codes |
|---|---|
| ![Login](docs/screenshots/login.jpg)<br>**Login:** session authentication into the workspace. | ![Rater workspace](docs/screenshots/rater-home.jpg)<br>**Workspace:** own assigned protocols and rating sheets. |
| ![Protocol and scales](docs/screenshots/plan.jpg)<br>**Definitions:** unfrozen protocol, ranges and scale anchors. | ![Blind rating sheet](docs/screenshots/blind-ratings.jpg)<br>**Ratings:** integer scores or explicit missing responses in presentation order. |
| ![Unblinded results](docs/screenshots/unblinded.jpg)<br>**Results:** frozen valid counts, means and ranges. | ![Account administration](docs/screenshots/users.jpg)<br>**Accounts:** users, departments and enabled status. |
| ![Roles and permissions](docs/screenshots/roles.jpg)<br>**Roles:** permissions and data scopes. | ![Evaluation statistics](docs/screenshots/dashboard.jpg)<br>**Dashboard:** actual progress within authorized scope. |
| ![System settings](docs/screenshots/settings.jpg)<br>**Settings:** supported workspace parameters. | ![English interface](docs/screenshots/english.jpg)<br>**English UI:** English operation pages. |
| ![Mobile interface](docs/screenshots/mobile.jpg)<br>**Mobile UI:** narrow-screen rating workflow. | |

## Technology and repository structure

| Component | Version/approach |
|---|---|
| Backend | Java 21, Maven 3.9, Spring Boot 4.0.7, Spring Security, JPA, Flyway, MariaDB JDBC 3.5.10 |
| Frontend | Node 24.19.0, npm 11, Vue 3.5.40, Vite 8.1.5, Lucide 1.48.0, Prettier and ESLint |
| Database | MySQL 8.4, in the MySQL 8 family; versioned SQL, foreign keys, unique constraints and JPA schema validation |
| Deployment | Docker Compose: MySQL, Java and Nginx frontend; database/backend have no host ports |
| Time | UTC microsecond facts; interface fixed to Asia/Shanghai; settings do not alter stored facts |
| Validation | JUnit HTTP/JPA and algorithm tests, Node tests, separate MySQL workflow and restore checks |

```text
backend/src/main/java/cn/zhuatech/panelblind/ Identity, workflow, algorithm and API
backend/src/main/resources/db/migration/    V1 identity / V2 blinded evaluation
backend/src/test/                           HTTP/JPA and algorithm tests
frontend/src/                              Pages, bounded forms, API and tests
frontend/public/brand/                     Original brand assets
docs/                                     Operations, API, architecture and actual screenshots
scripts/                                  Random local configuration, isolated acceptance, release checks
compose.yaml                              Complete local deployment
```

## First run with Docker Compose

Requirements: Docker, Docker Compose 2 and Python 3.11+. Network builds need official images, Maven Central and npm. No AI, instruments or external business accounts are required.

```bash
python3 scripts/init-env.py
docker compose config --quiet
docker compose up --build -d --wait
```

Open **[http://127.0.0.1:8131/](http://127.0.0.1:8131/)**. The initial username is `admin`; read `ADMIN_PASSWORD` from the local, Git-ignored `.env`. The script generates random passwords, saves mode 0600 and refuses to overwrite an existing file. There is no fixed public password. Changing an environment variable does not reset existing database accounts.

Initialization creates headquarters, five roles, ten permissions, ten menus, four product dictionary values and three settings. It creates no protocols, samples or ratings. Create independent reviewer, custodian and rater accounts before designing protocols. Do not use real customer identities/ratings in public demonstrations.

### Configuration

| Variable | Purpose |
|---|---|
| `DATABASE_PASSWORD` | Required Compose database application password |
| `MYSQL_ROOT_PASSWORD` | Required local database administration password |
| `ADMIN_PASSWORD` | Required only for first administrator initialization in an empty database |
| `WEB_PORT` | Frontend port, default 8131 |
| `BIND_ADDRESS` | Default 127.0.0.1, loopback |
| `COOKIE_SECURE` | `false` for local HTTP; `true` behind trusted external HTTPS |
| `DATABASE_URL`, `DATABASE_USER`, `DATABASE_CATALOG` | Optional backend-process overrides for source development; Compose configures an internal MySQL connection |

The [health endpoint](http://127.0.0.1:8131/actuator/health) normally returns `{"status":"UP"}` without database credentials or business counts. Complete image builds run backend tests without skipping them. `docker compose down` preserves volumes. Use `down -v` only for explicitly disposable local test data, never production or someone else's database.

## Source development

For the backend, provide Java 21, Maven 3.9 and migrated local MySQL, and set `DATABASE_URL`, `DATABASE_USER`, `DATABASE_CATALOG`, `DATABASE_PASSWORD` and `ADMIN_PASSWORD`:

```bash
cd backend
mvn spring-boot:run
```

In another terminal, start at the repository root with Node 24.19.0 / npm 11:

```bash
cd frontend
npm ci
npm run dev
```

Vite uses a loopback development port and proxies `/api` and `/actuator` to 127.0.0.1:8080. Frontend and backend are deployed on the same origin; both business/admin APIs require authorization. Configuration files, lockfiles and documentation define supported versions and names.

## Database initialization, migrations and backups

`V1__identity.sql` and `V2__blind_panels.sql` create 18 identity/business tables plus Flyway history, 19 total. Foreign keys retain referenced accounts/departments. Unique constraints protect protocol references, sample/scale codes, roster membership, presentation positions and rating cells.

Flyway applies versioned migrations at startup. Add new versions when upgrading; do not edit published migrations. Initialization only occurs when the account table is empty; restarts do not clear or rebuild business data. Back up before upgrades and verify migrations/restoration in an independent environment.

```bash
umask 077
mkdir -p private-backups
chmod 700 private-backups
docker compose exec -T mysql sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --no-tablespaces --set-gtid-purged=OFF "$MYSQL_DATABASE"' > private-backups/panelblind.sql
chmod 600 private-backups/panelblind.sql
# Restore only into an independently created empty database after checking the target.
# Never overwrite an existing business database.
# docker compose -p panelblind-restore exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' < private-backups/panelblind.sql
```

Backups contain sample identities, employee accounts and ratings. The directory is ignored; never publish backups. After restoration, verify login, original blind codes, sealing, accepted ratings, layout digests, released results and idempotent responses.

## Security and deployment boundaries

Sessions use HttpOnly/SameSite Strict cookies and writes use CSRF. BCrypt, login rate limiting, live permissions, assignments, departments, versions and UUID idempotency protect workflow actions. Fixed tables and parameterized queries are used; DTOs do not accept client-forged states, identity mappings or approval facts.

Business writes use READ_COMMITTED transactions and a shared identity-directory lock to serialize this instance's writes. This is a small-team learning implementation, not a high-throughput multi-tenant architecture. `maxRecords` limits protocols to 100–1,000; roster and rating rows are each capped at 10,000. Details return the latest 200 related events and audit shows at most 500 authorized records; full history remains in the database. Capacity requires an appropriate future archiving design, not deletion of frozen results to bypass audit.

Database passwords, initialization credentials and QA state stay outside Git. Logs/audit do not record complete passwords or sample-identity payloads; ordinary events do not expose internal rating snapshots. Database administrators can still read raw mappings; there is no database-level encrypted blinding or external tamper-proof evidence.

External deployment requires trusted HTTPS, `COOKIE_SECURE=true`, restricted database accounts, network policies, backups and filtering forged proxy headers. The default is loopback-only with no public database port. Follow [SECURITY](SECURITY.md) for private disclosures. This release does not claim independently verified production readiness.

## Validation commands

```bash
# H2 backend tests use actual Flyway/JPA and a separate random test password.
export TEST_ADMIN_PASSWORD="Aa9$(python3 -c 'import secrets; print(secrets.token_urlsafe(24))')"
mvn -B -f backend/pom.xml spotless:check test package
unset TEST_ADMIN_PASSWORD

cd frontend
npm ci
npm run format:check
npm run lint
npm test
npm run build
cd ..

# Complete isolated deployment. Initialize only when .env does not exist.
if [ ! -f .env ]; then python3 scripts/init-env.py; fi
docker compose -p panelblind-check config --quiet
docker compose -p panelblind-check build
docker compose -p panelblind-check up -d --wait
python3 scripts/smoke.py --allow-test-writes
python3 scripts/smoke.py --capture
docker compose -p panelblind-check restart backend
docker compose -p panelblind-check up -d --wait backend
docker compose -p panelblind-check restart frontend
docker compose -p panelblind-check up -d --wait
python3 scripts/smoke.py --verify
python3 scripts/release-check.py
git diff --check
```

`--allow-test-writes` is only for an isolated empty local database and creates clearly marked TEST roles/data. Random QA accounts are saved in ignored `output/qa-state.json` with mode 0600, not preloaded into source. `--capture` refreshes stable comparison responses after page operations; `--verify` compares restart/independent-restore consistency. An independent restore can set `TEST_URL`, such as loopback port 18131.

Backend tests cover position counts/code uniqueness, complete workflow, mixed administrator/rater isolation, identity-key authorization, sealed reads, missing responses, returns, freezing, abortion, concurrent versions, idempotency/deletion retries, exports and migrations. Frontend tests cover permitted role actions, acknowledgment, missing-as-null, bounded payloads and same-origin errors. Actual MySQL, screenshots and full restoration need separate isolated acceptance; unit tests do not replace deployment verification.

`TEST_ADMIN_PASSWORD` is a temporary random password for backend tests only, not the application's administrator password. Do not store it in source or reuse real business-account credentials.

For restart verification, wait for the backend to become healthy before restarting the frontend so the proxy resolves the project's service address again. Run `--verify` after all services are healthy.

## Troubleshooting

- **Protocol submission rejected:** require 2–8 samples, at least one required scale and a roster of at least four whose count is a multiple of samples. The reviewer cannot have edited the protocol or be its custodian/rater.
- **Actual product names hidden:** ordinary details retain blind codes. The assigned custodian or authorized past editor uses the separate identity-key entry; raters cannot use it.
- **Designer cannot read scores:** before unblinding, unsubmitted scores are author-only; submitted scores are available to the assigned completeness reviewer.
- **Ending rejected:** resolve all sheets as accepted/withdrawn; normal completion needs at least two accepted. Never turn missing values into zero to fill gaps.
- **Accepted results cannot change:** accepted, withdrawn and unblinded data freeze. Only submitted pending-review sheets can be returned for revision/resubmission.
- **Changed password variable has no effect:** initialization applies only to empty databases. Use the application's password-change flow for existing accounts; do not delete the database to change a password.
- **Port occupied:** override local `WEB_PORT`; do not stop another project's containers/processes.
- **Image build failed:** inspect restricted build/backend logs, fix tests/network and retry; do not add test-skipping flags.

## Not implemented and external configuration

There is no external consumer enrollment, demographic/health data processing, medical/food/cosmetic safety judgment, randomized clinical trial, automatic representativeness/power assessment, significance testing, product recommendation, site equipment, IoT, notifications, attachments, physical logistics, inventory, payments, multi-tenancy, SSO or HA. The system does not determine sample-use safety, allergies or employee capability. Intended scenarios are internal nonclinical material/packaging evaluations.

No external business accounts, models, SMS or vendor credentials are needed. The deployer configures external HTTPS, backup storage and physical sample preparation. No reserved external interface is presented as an implemented integration. Read [CONTRIBUTING](CONTRIBUTING.md) and [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES.md) before contributing.

## License and contact ZhiHua

The root [LICENSE](LICENSE) governs the project's own code. Personal learning, technical research and non-commercial exchange are permitted; commercial use requires prior written authorization from Shanghai Rujing Zhihua Information Technology Co., Ltd. Brand/contact information does not change source or third-party licenses.

For commercial licensing, private deployment, source customization, system integration or in-depth custom development, contact **ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)**:

- Website: [https://www.zhuatech.cn/](https://www.zhuatech.cn/)
- Email: [han@zhuatech.cn](mailto:han@zhuatech.cn)
- Email: [jack@zhuatech.cn](mailto:jack@zhuatech.cn)
- WhatsApp: [+86 17521234993](https://wa.me/8617521234993)

© 2026 Shanghai Rujing Zhihua Information Technology Co., Ltd.
