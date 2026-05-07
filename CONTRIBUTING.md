# Network Speed Test

## Description

This repository is a small self-hosted browser-to-backend network speed test. It packages a Spring Boot backend and a plain HTML/CSS/JavaScript dashboard into one executable jar named `speedtest.jar`.

The application measures HTTP round-trip latency, single-stream download throughput, and upload throughput between a user's browser and the server that runs the jar. The dashboard also shows live server CPU, RAM, and disk usage indicators. It exists as a lightweight operational utility rather than a multi-user product: there is no authentication, user management, historical storage, database, or background processing.

## Business domain

The bounded context is ad hoc network and host diagnostics. The main actor is a browser user who wants to understand the quality of the HTTP path to this specific backend host and see whether server resource pressure may affect the result.

The repository does not attempt to measure ICMP ping, multi-stream throughput, route-level diagnostics, ISP-grade speed tests, or long-term monitoring. All measurements are immediate, client-driven, and ephemeral.

## Key entities

- **Speed test configuration**: Exposed by `GET /api/speedtest/config` and backed by `SpeedtestProperties`. It contains ping sample counts and interval, download duration, upload duration, adaptive upload chunk sizes, and an estimated total duration.
- **Ping sample**: A browser-measured HTTP request/response round trip to `GET /api/speedtest/ping`. Warmup samples are discarded; measured samples are summarized as median, minimum, and jitter in the browser.
- **Download stream**: A timed `application/octet-stream` response from `GET /api/speedtest/download`. The backend repeatedly writes a random byte buffer until the configured or requested duration expires.
- **Upload chunk**: A browser-generated binary payload posted to `POST /api/speedtest/upload`. The backend drains the request body and returns `UploadResponse(receivedBytes, serverElapsedMillis)`.
- **System metrics snapshot**: `SystemMetricsResponse` from `GET /api/system-metrics`, containing timestamp, CPU percentage, memory used/total, disk used/total, and disk path. The service caches samples for one second.
- **UI home configuration**: `UiConfigResponse` from `GET /api/config`, controlled by `home.show` and `home.location`, which optionally displays a Home link in the top bar.

## Business logic

The UI workflow is implemented in `src/main/resources/static/app.js`:

1. Load speed test configuration and optional UI home-link configuration.
2. Poll server metrics every two seconds.
3. On `Start test`, run ping, download, then upload phases sequentially.
4. Render live progress, throughput, byte counts, ping statistics, and final summaries.

The backend intentionally keeps logic simple and stateless. Speed test endpoints use no-cache headers and add `Timing-Allow-Origin: *` for the speed test API so browser timing information is usable. The download endpoint disables proxy buffering with `X-Accel-Buffering: no` and catches client disconnects as expected behavior. Upload measurement is based on draining the request body; the browser computes displayed throughput from client-side bytes and elapsed time.

## Tech stack

- **Java 25** with Gradle toolchains.
- **Spring Boot 4.0.6** using `spring-boot-starter-webmvc`.
- **Gradle 9.5.0** via the checked-in wrapper.
- **JUnit 5 / Spring MockMvc** through `spring-boot-starter-webmvc-test`.
- **Plain static frontend**: HTML, CSS, and browser JavaScript served from the jar.
- **Deployment example**: root-level `speedtest.service` systemd unit.

There is no database, message broker, container image, frontend package manager, or generated API client in this repository.

## Build system

Use the Gradle wrapper from the repository root.

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

`bootJar` produces `build/libs/speedtest.jar`; the plain `jar` task is disabled. The project version is `0.1.0` and the Gradle root project name is `speedtest`.

No CI/CD workflow files were found. Before submitting changes, run `./gradlew test` or `.\gradlew.bat test`; this was verified successfully during this repository exploration.

## File structure

- `build.gradle` - Gradle Groovy DSL build definition, Java 25 toolchain, Spring Boot plugin, dependencies, and `speedtest.jar` artifact naming.
- `settings.gradle` - Gradle root project name.
- `gradle/wrapper/` and `gradlew*` - pinned Gradle 9.5.0 wrapper.
- `src/main/java/net/edubovit/speedtest/SpeedtestApplication.java` - Spring Boot entry point and custom `--config` argument expansion.
- `src/main/java/net/edubovit/speedtest/api/` - response records returned by JSON APIs.
- `src/main/java/net/edubovit/speedtest/config/` - typed configuration properties for `speedtest.*` and `home.*`.
- `src/main/java/net/edubovit/speedtest/web/` - REST controllers for speed tests, UI configuration, and system metrics.
- `src/main/java/net/edubovit/speedtest/service/` - host metrics sampling service.
- `src/main/resources/application.yml` - default application settings.
- `src/main/resources/static/` - static web dashboard served directly by Spring Boot.
- `src/test/java/net/edubovit/speedtest/` - Spring MVC endpoint tests and argument-expansion unit tests.
- `speedtest.service` - example systemd service unit.

## API and interfaces

### Browser UI

The main user interface is `src/main/resources/static/index.html`, with styling in `styles.css` and behavior in `app.js`. Assets and API URLs are intentionally relative to the script location so the application can be served at `/` or below a prefix such as `/speedtest/`.

### HTTP API

- `GET /api/config` returns optional UI home-link configuration.
- `GET /api/system-metrics` returns a cached host metrics snapshot.
- `GET /api/speedtest/config` returns test timings and upload chunk limits.
- `GET /api/speedtest/ping` returns `204 No Content` for HTTP RTT sampling.
- `GET /api/speedtest/download?seconds=<n>` streams random bytes for a clamped duration from 1 to 60 seconds.
- `POST /api/speedtest/upload` consumes `application/octet-stream` and returns received byte count and server-side elapsed milliseconds.

Reverse proxy guidance should continue to discourage gzip, proxy buffering, request buffering, caching, and prefix mistakes because those can distort throughput measurements. No nginx configuration is shipped with the repository.

## Configuration

Default configuration lives in `src/main/resources/application.yml`:

- `server.port`: currently `20003` in the checked-in application config.
- `server.compression.enabled`: `false`; compression should stay disabled for speed test accuracy.
- `home.show` and `home.location`: control whether the UI displays a Home link.
- `speedtest.ping.*`: warmup samples, measured samples, and interval.
- `speedtest.download.duration-seconds` and `speedtest.download.buffer-bytes`.
- `speedtest.upload.duration-seconds`, initial/min/max chunk sizes, and adaptive target duration.

Spring Boot property overrides work through environment variables and command-line arguments, for example `SERVER_PORT=24080` or `--server.port=24080`. The application also supports a convenience argument:

```bash
java -jar build/libs/speedtest.jar --config=/path/to/application.yml
```

`--config` is expanded by `SpeedtestApplication` to `--spring.config.additional-location=file:<path>`. Missing or blank config paths throw `IllegalArgumentException` before startup.

No secrets are required by the current code. If future configuration introduces credentials, do not commit them; use external Spring configuration or environment-specific deployment files.

## Tests

Run all tests with:

```bash
./gradlew test
```

The existing suite covers:

- speed test ping/config/upload endpoints with MockMvc;
- system metrics endpoint shape and no-cache headers;
- static asset URL conventions needed for prefix deployments;
- client script API URL construction conventions;
- `--config` argument expansion and error handling.

There are no dedicated browser automation tests, load tests, nginx/systemd deployment tests, or focused tests for download streaming behavior. Changes to browser throughput calculations should be manually checked in a modern browser because the UI depends on streaming `fetch` and XHR upload progress.

## Architecture

The application is a single Spring Boot process with static files and JSON/binary endpoints in the same jar.

Controllers form the HTTP boundary:

- `SpeedtestController` owns speed test configuration, ping, download streaming, and upload draining.
- `SystemMetricsController` exposes host metrics from `SystemMetricsService`.
- `UiConfigController` exposes minimal UI configuration.

Configuration is bound with `@ConfigurationProperties` and discovered through `@ConfigurationPropertiesScan` on the application class. API responses use Java records to keep response contracts explicit and immutable.

The frontend owns orchestration and presentation. It times HTTP RTT with `performance.now()`, reads the download response stream with `ReadableStreamDefaultReader`, sends upload chunks with `XMLHttpRequest` so upload progress is available, and adapts upload chunk sizes to target roughly 750 ms requests by default.

`SystemMetricsService` samples host data using `com.sun.management.OperatingSystemMXBean` when available and Java NIO file-store APIs for disk usage. It synchronizes access and caches one sample for one second to avoid excessive polling overhead.

## External integrations and dependencies

- **Browser Fetch/XHR APIs**: required for streaming downloads and upload progress.
- **JDK management APIs**: `com.sun.management.OperatingSystemMXBean` is used when the runtime exposes it; metrics may be `null` on unsupported platforms.
- **Reverse proxy**: optional; configuration must avoid buffering, request buffering, gzip, caching, and prefix mistakes.
- **systemd**: optional Linux service manager example at `speedtest.service` for running the jar with constrained JVM memory and CPU options.
- **Maven Central / Gradle services**: needed to resolve dependencies and the Gradle distribution unless already cached.

## Conventions

- Keep API paths relative in the browser code. Tests assert that `app.js` does not hard-code root-relative `/api/...` URLs.
- Keep speed-test responses non-cacheable and avoid proxy/browser transformations (`no-transform`, gzip off, buffering off).
- Prefer small immutable API records in `api/` and typed property classes in `config/`.
- Do not add persistence or authentication assumptions without updating the documentation and tests; the current product model is stateless and unauthenticated.
- Preserve single-stream semantics unless intentionally changing what the tool measures.
- Use Gradle wrapper commands rather than a globally installed Gradle.
- The repository ignores local build output, Gradle state, and common IDE files via `.gitignore`.

## Unclear areas, risks, technical debt

- **Deployment example may be environment-specific**: `speedtest.service` uses `User=monitoring` and `/opt/speedtest/speedtest.jar`; adjust those values for the target host.
- **No authentication or rate limiting**: exposing the app publicly allows arbitrary users to generate upload/download traffic and view coarse host resource metrics.
- **Measurement scope is intentionally limited**: results are HTTP, browser, proxy, and single-stream dependent; nginx buffering/compression or intermediary caches can invalidate measurements.
- **System metrics are best effort**: CPU and memory depend on JDK-specific management APIs; disk path selection uses the working directory root or first filesystem root.
- **Frontend behavior is mostly untested by automation**: existing tests inspect static files for URL conventions but do not execute the browser workflow.
