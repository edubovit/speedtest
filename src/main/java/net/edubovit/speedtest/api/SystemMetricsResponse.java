package net.edubovit.speedtest.api;

import java.time.Instant;

public record SystemMetricsResponse(
        Instant timestamp,
        Double cpuUsagePercent,
        Long memoryUsedBytes,
        Long memoryTotalBytes,
        Long diskUsedBytes,
        Long diskTotalBytes,
        String diskPath) {
}
