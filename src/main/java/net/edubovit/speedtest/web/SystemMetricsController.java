package net.edubovit.speedtest.web;

import net.edubovit.speedtest.api.SystemMetricsResponse;
import net.edubovit.speedtest.service.SystemMetricsService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SystemMetricsController {

    private static final String CACHE_CONTROL_VALUE = "no-store, no-cache, max-age=0, must-revalidate, no-transform";

    private final SystemMetricsService systemMetricsService;

    public SystemMetricsController(SystemMetricsService systemMetricsService) {
        this.systemMetricsService = systemMetricsService;
    }

    @GetMapping("/api/system-metrics")
    public ResponseEntity<SystemMetricsResponse> metrics() {
        return ResponseEntity.ok()
                .headers(noCacheHeaders())
                .body(systemMetricsService.currentMetrics());
    }

    private HttpHeaders noCacheHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CACHE_CONTROL_VALUE);
        headers.setPragma("no-cache");
        headers.setExpires(0);
        return headers;
    }
}
