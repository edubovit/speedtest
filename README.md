# Network Speed Test

Simple browser-to-backend speed test built with:

- Java 25
- Spring Boot 4.0.6
- Gradle 9.5.0
- plain HTML/CSS/JS

The application measures:

- HTTP latency (ping)
- download throughput
- upload throughput
- live server CPU, RAM, and disk usage indicators on the dashboard

There is no authentication and no history storage.

## Project layout

- `src/main/java/net/edubovit/speedtest` - Spring Boot backend
- `src/main/resources/static` - plain web UI served directly by the jar
- `build.gradle` and `settings.gradle` - Gradle Groovy DSL build configuration
- `speedtest.service` - example systemd unit

## Run locally

Build the executable jar:

```bash
./gradlew bootJar
```

On Windows:

```powershell
.\gradlew.bat bootJar
```

Run it:

```bash
java -jar build/libs/speedtest.jar
```

Default port is `20003`.

Open:

- `http://localhost:20003/`

## Change the port

Environment variable:

```bash
SERVER_PORT=24080 java -jar build/libs/speedtest.jar
```

Windows PowerShell:

```powershell
$env:SERVER_PORT = 24080
java -jar build/libs/speedtest.jar
```

Command-line override:

```bash
java -jar build/libs/speedtest.jar --server.port=24080
```

## Reverse proxy notes

No nginx configuration is shipped with the project. If you put nginx or another reverse proxy in front of the jar, avoid compression, buffering, request buffering, and caching on the speed-test endpoints so throughput measurements are not distorted.

### Serving below a path prefix

The browser UI uses relative asset and API URLs, so it can also be published below a prefix such as `/speedtest/`. Keep the external URL directory-like with a trailing slash, and configure nginx to either strip the prefix or run Spring Boot with a matching context path.

Prefix-stripping nginx example:

```nginx
location = /speedtest {
    return 308 /speedtest/;
}

location /speedtest/ {
    proxy_pass http://127.0.0.1:20003/;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_buffering off;
    proxy_request_buffering off;
    gzip off;
}
```

The trailing slash on `proxy_pass` is intentional: browser requests for `/speedtest/api/...` are forwarded to the backend as `/api/...`.

## systemd service example

The root-level `speedtest.service` unit starts the application as:

```bash
java -Xms16m -Xmx64m -Xss256k -XX:+UseSerialGC -XX:MaxHeapFreeRatio=50 -XX:MinHeapFreeRatio=10 -XX:-ShrinkHeapInSteps -XX:ActiveProcessorCount=1 -XX:MaxDirectMemorySize=16m -XX:ReservedCodeCacheSize=32m -XX:MaxMetaspaceSize=64m -XX:+UseCompactObjectHeaders -XX:-UsePerfData -XX:TieredStopAtLevel=1 -XX:CICompilerCount=1 -Djava.awt.headless=true -XX:+ExitOnOutOfMemoryError -jar /opt/speedtest/speedtest.jar
```

It runs under:

```bash
User=monitoring
```

Example installation:

1. Copy `build/libs/speedtest.jar` to `/opt/speedtest/speedtest.jar`.
2. Install Java 25 and ensure `java` is available on the service manager's `PATH`.
3. Ensure the `monitoring` user can read the jar, or adjust `User=` in `speedtest.service`.

4. Install the unit:

```bash
sudo cp speedtest.service /etc/systemd/system/speedtest.service
sudo systemctl daemon-reload
sudo systemctl enable --now speedtest
```

5. Check status:

```bash
sudo systemctl status speedtest
```

## Notes

- Ping is **HTTP round-trip time**, not ICMP.
- Throughput is measured with a **single HTTP stream** per phase.
- For accurate results, avoid extra proxy compression/caching on speed test endpoints.
- A modern browser is recommended because the UI uses streaming fetch and XHR upload progress.
