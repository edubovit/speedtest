# Network Speed Test

Simple browser-to-backend speed test built with:

- Java 25
- Spring Boot 4.0.5
- Gradle 9.4.1
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
- `deploy/nginx/speedtest.conf` - example nginx setup
- `deploy/systemd/speedtest.service` - example systemd unit

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

Default port is `23080`.

Open:

- `http://localhost:23080/`

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

## nginx example

The included nginx config assumes:

- Spring Boot runs on `127.0.0.1:23080`
- nginx reverse-proxies the whole application through a single `/` location

Example reverse-proxy deployment:

1. Start the Spring Boot jar on the backend host.
2. Place `deploy/nginx/speedtest.conf` into your nginx site configuration.
3. Reload nginx.

## systemd service example

The included unit file starts the application as:

```bash
java -Xms32m -Xmx128m -XX:+UseSerialGC -Djava.awt.headless=true -jar /opt/speedtest.jar
```

and sets the default port through:

```bash
SERVER_PORT=23080
```

Example installation:

1. Copy `build/libs/speedtest.jar` to `/opt/speedtest.jar`.
2. Create a dedicated service account:

```bash
sudo useradd --system --home /nonexistent --shell /usr/sbin/nologin speedtest
```

3. Install the unit:

```bash
sudo cp deploy/systemd/speedtest.service /etc/systemd/system/speedtest.service
sudo systemctl daemon-reload
sudo systemctl enable --now speedtest
```

4. Check status:

```bash
sudo systemctl status speedtest
```

## Notes

- Ping is **HTTP round-trip time**, not ICMP.
- Throughput is measured with a **single HTTP stream** per phase.
- For accurate results, avoid extra proxy compression/caching on speed test endpoints.
- A modern browser is recommended because the UI uses streaming fetch and XHR upload progress.
