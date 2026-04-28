# Network Speed Test

## Description

This repository contains `speedtest`, a small Spring Boot application that serves a browser-based network speed test. It measures browser-to-backend HTTP latency, download throughput, upload throughput, and displays live host CPU, RAM, and disk usage on the same dashboard.

The project exists as a self-contained speed test service: the executable jar serves both the backend API and the static HTML/CSS/JavaScript UI. It is intentionally simple and stateless. There is no authentication, user management, database, or history storage; each browser session runs live measurements against the current backend instance.

## Business logic

The application's domain is browser-to-server HTTP measurement, not general network diagnostics. The UI loads a test profile, runs phases in order, and calculates results in the browser:

- **Configuration phase**: `GET /api/speedtest/config` returns ping sample counts, transfer durations, upload chunk limits, and the estimated total duration.
- **Ping phase**: the browser sends repeated `GET /api/speedtest/ping` requests. Warmup samples are discarded, and measured samples are summarized as median latency, minimum latency, and jitter. This is HTTP round-trip time, not ICMP ping.
- **Download phase**: the backend streams random bytes from a reusable buffer for a configured number of seconds. The browser reads the streaming response and computes average Mbps from bytes received and elapsed time.
- **Upload phase**: the browser uploads generated binary blobs to `POST /api/speedtest/upload`. Chunk size adapts toward a target upload request duration, and the backend drains the request body and reports bytes received plus server elapsed time.
- **System metrics**: the dashboard polls `GET /api/system-metrics` every two seconds and renders gauges for current CPU, memory, and disk usage when the JVM/platform can provide those values.

The repository is responsible for the single-node web app, static dashboard, measurement endpoints, basic deployment examples, and tests around the HTTP API. It does not provide multi-server selection, persistent result storage, user accounts, public API versioning, or traffic shaping.

## Tech stack

- **Java 25** via the Gradle Java toolchain.
- **Spring Boot 4.0.5** with `spring-boot-starter-webmvc` for REST controllers and static resource serving.
- **Gradle 9.4.1** through the checked-in Gradle wrapper.
- **Plain HTML/CSS/JavaScript** in `src/main/resources/static`; there is no Node.js package manager, frontend framework, bundler, or separate frontend build.
- **JUnit 5 / Spring MockMvc** through `spring-boot-starter-webmvc-test` for integration-style controller tests.
- **JVM/platform management APIs** (`com.sun.management.OperatingSystemMXBean`, `java.nio.file.FileStore`) for optional system metrics.
- **nginx and systemd examples** under `deploy/` for a reverse-proxy and Linux service deployment.

## Build system

Use the Gradle wrapper from the repository root. The main build file is `build.gradle.kts`.

Common commands:

```bash
./gradlew test
./gradlew bootJar
java -jar build/libs/speedtest.jar
```

On Windows PowerShell:

```powershell
.\gradlew.bat test
.\gradlew.bat bootJar
java -jar build/libs/speedtest.jar
```

Build details:

- `bootJar` produces `build/libs/speedtest.jar`; the archive name is fixed in `build.gradle.kts`.
- The plain `jar` task is disabled, so the Spring Boot executable jar is the intended artifact.
- Tests run on JUnit Platform (`tasks.test { useJUnitPlatform() }`).
- Dependencies resolve from Maven Central.
- The default application port is `23080`.
- No CI workflow, Dockerfile, or container compose file is present in this repository.

The test suite was verified with `./gradlew.bat test` in this workspace.

## File structure

```text
.
├── build.gradle.kts                 # Gradle build, Java 25 toolchain, Spring Boot plugin, dependencies
├── settings.gradle.kts              # Gradle root project name: speedtest
├── README.md                        # User-facing overview and run/deploy notes
├── gradle/wrapper/                  # Gradle wrapper 9.4.1
├── deploy/
│   ├── nginx/speedtest.conf         # Example reverse proxy for localhost:23080
│   └── systemd/speedtest.service    # Example Linux service for /opt/speedtest.jar
└── src/
    ├── main/
    │   ├── java/net/edubovit/speedtest/
    │   │   ├── SpeedtestApplication.java
    │   │   ├── api/                 # Response records serialized as JSON
    │   │   ├── config/              # speedtest.* configuration properties
    │   │   ├── service/             # System metrics sampling logic
    │   │   └── web/                 # REST controllers
    │   └── resources/
    │       ├── application.yml      # Default Spring and speedtest configuration
    │       └── static/              # Browser UI served directly by Spring Boot
    └── test/java/net/edubovit/speedtest/
        └── SpeedtestApplicationTests.java
```

Important Java packages:

- `net.edubovit.speedtest.web`: HTTP endpoint definitions. `SpeedtestController` owns measurement endpoints, and `SystemMetricsController` owns dashboard metric polling.
- `net.edubovit.speedtest.api`: Java records used as JSON response contracts.
- `net.edubovit.speedtest.config`: `@ConfigurationProperties(prefix = "speedtest")` defaults and accessors.
- `net.edubovit.speedtest.service`: platform metric sampling and one-second cache.

## API and interfaces

The jar serves the static UI at `/` from `src/main/resources/static/index.html` and exposes these JSON/binary endpoints:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/speedtest/config` | Returns ping/download/upload settings and estimated total duration. |
| `GET` | `/api/speedtest/ping` | Returns `204 No Content` for HTTP RTT sampling. |
| `GET` | `/api/speedtest/download?seconds=N` | Streams `application/octet-stream` bytes for the requested duration. Request duration is clamped to 1-60 seconds; default comes from configuration. |
| `POST` | `/api/speedtest/upload` | Accepts `application/octet-stream`, drains the body, and returns `{"receivedBytes": ..., "serverElapsedMillis": ...}`. |
| `GET` | `/api/system-metrics` | Returns timestamp, CPU percent, memory used/total, disk used/total, and disk path. Metric fields may be `null` when unavailable. |

Speed test responses set no-cache headers. Speed test endpoints also include `Timing-Allow-Origin: *`, and downloads include `X-Accel-Buffering: no` to help reverse proxies avoid buffering streamed data.

The browser UI depends on modern browser APIs:

- streaming `fetch()` response readers for download measurement;
- `XMLHttpRequest.upload.onprogress` for upload progress;
- `crypto.getRandomValues()` and `Blob` for generated upload payloads.

## Configuration

Defaults live in `src/main/resources/application.yml`:

```yaml
server:
  port: ${SERVER_PORT:23080}
  compression:
    enabled: false

speedtest:
  ping:
    warmup-samples: 2
    measured-samples: 10
    interval-millis: 250
  download:
    duration-seconds: 12
    buffer-bytes: 262144
  upload:
    duration-seconds: 12
    initial-chunk-bytes: 262144
    min-chunk-bytes: 262144
    max-chunk-bytes: 16777216
    adaptive-target-millis: 750
```

Operational notes:

- Change the port with `SERVER_PORT`, for example `SERVER_PORT=24080 java -jar build/libs/speedtest.jar`, or with Spring's command-line override `--server.port=24080`.
- `speedtest.*` settings are bound by Spring Boot configuration properties, so they can be overridden through normal Spring configuration sources.
- HTTP compression is disabled by default because compression would distort transfer measurements.
- There are no application secrets in the current configuration.
- Keep reverse proxies from compressing, caching, or buffering the speed test paths. The included nginx example sets `proxy_buffering off`, `proxy_request_buffering off`, and `gzip off`.

## Tests

Run tests from the repository root:

```bash
./gradlew test
```

or on Windows:

```powershell
.\gradlew.bat test
```

Current tests are in `SpeedtestApplicationTests` and start a Spring application context with MockMvc. They cover:

- `GET /api/speedtest/ping` status and cache headers;
- `GET /api/speedtest/config` default values and estimated duration;
- `GET /api/system-metrics` status, cache headers, and expected JSON field names;
- `POST /api/speedtest/upload` byte counting.

Coverage is intentionally narrow. There are no frontend tests, no browser automation tests, no dedicated unit tests for upload chunk adaptation or ping statistics, and no assertion of actual platform metric values because those are environment-dependent. The download streaming endpoint is not directly covered by the current tests.

## Architecture

This is a single Spring Boot process with a static frontend and REST endpoints in the same artifact.

Request/data flow:

1. The browser loads `index.html`, `styles.css`, `app.js`, and `favicon.svg` from Spring Boot static resources. Static references are relative so the UI works when published below a path prefix such as `/speedtest/`.
2. `app.js` derives the application base URL from its own script URL, loads `api/speedtest/config`, initializes the UI, and starts polling `api/system-metrics` relative to that base.
3. When the user clicks **Start test**, the browser runs ping, download, and upload phases sequentially.
4. Controllers return minimal responses and streaming bodies; most statistical calculations and UI updates happen client-side.
5. `SystemMetricsService` samples host metrics at most once per second and returns cached values for more frequent calls.

Backend layering is deliberately thin:

- `SpeedtestApplication` enables Spring Boot auto-configuration and configuration property scanning.
- Controllers perform request handling, response header construction, download streaming, and upload body draining.
- DTO records define the JSON response shape without mapping layers.
- `SystemMetricsService` encapsulates platform-specific metric sampling and tolerates unavailable metrics by returning `null` values.

The app is stateless between requests except for immutable download buffer data in `SpeedtestController` and a short-lived cached metrics sample in `SystemMetricsService`.

## External integrations and dependencies

- **Spring Boot WebMVC**: HTTP server, REST controller model, static resource serving, JSON serialization.
- **JVM operating system metrics**: host CPU and memory values come from `com.sun.management.OperatingSystemMXBean`; disk values come from `Files.getFileStore(...)` on the detected root path.
- **nginx**: optional reverse proxy. The example proxies all paths to `127.0.0.1:23080` and disables buffering/compression for measurement accuracy.
- **systemd**: optional service unit for running `/opt/speedtest.jar` as `User=nobody` with constrained JVM memory/CPU-related options and `SERVER_PORT=23080`.
- **Modern browsers**: required for streaming download reads and upload progress events.

There are no database, queue, cache server, cloud SDK, or third-party API integrations in the current codebase.

## Conventions

- Keep the project self-contained: backend, static UI, configuration, and deployment examples are all in this repository.
- Use the Gradle wrapper instead of a system Gradle installation.
- Keep API response contracts in Java records under `api/` and controller routes under `web/`.
- Use constructor injection for Spring components, as the existing controllers and services do.
- Preserve no-cache/no-transform behavior on measurement endpoints. Caching, compression, or buffering can make results misleading.
- Keep frontend code dependency-free unless there is a strong reason to introduce a frontend build step.
- When changing measurement behavior, update both the backend configuration/response contract and `static/app.js`, because the browser drives most phase orchestration and calculations.
- Do not commit Gradle build outputs, IDE metadata, or local wrapper caches; `.gitignore` already excludes `.gradle/`, `build/`, `out/`, IntelliJ files, and Eclipse metadata.

## Unclear areas, risks, technical debt

- **Public exposure risk**: the app has no authentication and exposes bandwidth-consuming download and upload endpoints. That matches the README, but a public deployment should rely on network-level controls, reverse-proxy limits, or other operational protections.
- **Measurement limitations**: results are based on single HTTP streams per phase and browser-side timing. They are useful for browser-to-this-backend checks, not a full replacement for multi-stream or lower-level network testing.
- **Limited tests**: backend smoke/integration tests exist, but frontend behavior, download streaming, edge cases, deployment files, and JavaScript calculations are not covered.
- **Configuration validation is minimal**: `SpeedtestProperties` does not use Bean Validation. Invalid values such as non-positive durations or inconsistent upload chunk limits may lead to confusing runtime behavior.
- **System metrics are platform-dependent**: CPU/memory/disk values can be unavailable or differ by JVM and OS. The UI handles unavailable metrics, but contributors should avoid assuming all fields are non-null.
