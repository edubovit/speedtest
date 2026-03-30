package net.edubovit.speedtest.web;

import jakarta.servlet.http.HttpServletRequest;
import net.edubovit.speedtest.api.SpeedtestConfigResponse;
import net.edubovit.speedtest.api.UploadResponse;
import net.edubovit.speedtest.config.SpeedtestProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/speedtest")
public class SpeedtestController {

    private static final String CACHE_CONTROL_VALUE = "no-store, no-cache, max-age=0, must-revalidate, no-transform";
    private static final byte[] UPLOAD_DRAIN_BUFFER = new byte[65_536];

    private final SpeedtestProperties properties;
    private final byte[] downloadBuffer;

    public SpeedtestController(SpeedtestProperties properties) {
        this.properties = properties;
        this.downloadBuffer = new byte[Math.max(8_192, properties.getDownload().getBufferBytes())];
        ThreadLocalRandom.current().nextBytes(this.downloadBuffer);
    }

    @GetMapping("/config")
    public ResponseEntity<SpeedtestConfigResponse> config() {
        SpeedtestProperties.Ping ping = properties.getPing();
        SpeedtestProperties.Download download = properties.getDownload();
        SpeedtestProperties.Upload upload = properties.getUpload();

        SpeedtestConfigResponse body = new SpeedtestConfigResponse(
                new SpeedtestConfigResponse.Ping(
                        ping.getWarmupSamples(),
                        ping.getMeasuredSamples(),
                        ping.getIntervalMillis()),
                new SpeedtestConfigResponse.Download(download.getDurationSeconds()),
                new SpeedtestConfigResponse.Upload(
                        upload.getDurationSeconds(),
                        upload.getInitialChunkBytes(),
                        upload.getMinChunkBytes(),
                        upload.getMaxChunkBytes(),
                        upload.getAdaptiveTargetMillis()),
                properties.estimatedTotalDurationSeconds());

        return ResponseEntity.ok()
                .headers(speedtestHeaders())
                .body(body);
    }

    @GetMapping("/ping")
    public ResponseEntity<Void> ping() {
        return ResponseEntity.noContent()
                .headers(speedtestHeaders())
                .build();
    }

    @GetMapping(value = "/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> download(
            @RequestParam(name = "seconds", required = false) Integer requestedSeconds) {

        int durationSeconds = clampDuration(requestedSeconds, properties.getDownload().getDurationSeconds());

        StreamingResponseBody body = outputStream -> streamDownload(outputStream, durationSeconds);

        HttpHeaders headers = speedtestHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.add("X-Accel-Buffering", "no");

        return ResponseEntity.ok()
                .headers(headers)
                .body(body);
    }

    @PostMapping(value = "/upload", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UploadResponse> upload(HttpServletRequest request) throws IOException {
        long startedAt = System.nanoTime();
        long receivedBytes = drain(request.getInputStream());
        double elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000.0;

        return ResponseEntity.ok()
                .headers(speedtestHeaders())
                .body(new UploadResponse(receivedBytes, elapsedMillis));
    }

    private void streamDownload(OutputStream outputStream, int durationSeconds) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(durationSeconds);

        try {
            while (System.nanoTime() < deadline) {
                outputStream.write(downloadBuffer);
                outputStream.flush();
            }
        } catch (IOException ignored) {
            // Client disconnected before the timed stream completed.
        }
    }

    private long drain(InputStream inputStream) throws IOException {
        long totalBytes = 0;
        int read;
        while ((read = inputStream.read(UPLOAD_DRAIN_BUFFER)) != -1) {
            totalBytes += read;
        }
        return totalBytes;
    }

    private HttpHeaders speedtestHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CACHE_CONTROL_VALUE);
        headers.setPragma("no-cache");
        headers.setExpires(0);
        headers.add("Timing-Allow-Origin", "*");
        return headers;
    }

    private int clampDuration(Integer requestedSeconds, int defaultSeconds) {
        if (requestedSeconds == null) {
            return defaultSeconds;
        }
        return Math.max(1, Math.min(requestedSeconds, 60));
    }
}
