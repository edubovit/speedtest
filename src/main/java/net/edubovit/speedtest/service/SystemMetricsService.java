package net.edubovit.speedtest.service;

import com.sun.management.OperatingSystemMXBean;
import net.edubovit.speedtest.api.SystemMetricsResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.FileSystems;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;

@Service
public class SystemMetricsService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(1);

    private final Clock clock;
    private final OperatingSystemMXBean operatingSystemMXBean;
    private final Path diskPath;

    private Instant cachedAt = Instant.EPOCH;
    private SystemMetricsResponse cachedMetrics;

    public SystemMetricsService() {
        this(Clock.systemUTC(), resolveOperatingSystemMxBean(), detectDiskPath());
    }

    SystemMetricsService(Clock clock, OperatingSystemMXBean operatingSystemMXBean, Path diskPath) {
        this.clock = clock;
        this.operatingSystemMXBean = operatingSystemMXBean;
        this.diskPath = diskPath;
    }

    public synchronized SystemMetricsResponse currentMetrics() {
        Instant now = clock.instant();
        if (cachedMetrics != null && Duration.between(cachedAt, now).compareTo(CACHE_TTL) < 0) {
            return cachedMetrics;
        }

        cachedMetrics = sample(now);
        cachedAt = now;
        return cachedMetrics;
    }

    private SystemMetricsResponse sample(Instant timestamp) {
        Double cpuUsagePercent = null;
        Long memoryUsedBytes = null;
        Long memoryTotalBytes = null;

        if (operatingSystemMXBean != null) {
            double cpuLoad = operatingSystemMXBean.getCpuLoad();
            if (cpuLoad >= 0) {
                cpuUsagePercent = cpuLoad * 100.0;
            }

            long totalMemorySize = operatingSystemMXBean.getTotalMemorySize();
            long freeMemorySize = operatingSystemMXBean.getFreeMemorySize();
            if (totalMemorySize > 0 && freeMemorySize >= 0) {
                memoryTotalBytes = totalMemorySize;
                memoryUsedBytes = Math.max(0, totalMemorySize - freeMemorySize);
            }
        }

        Long diskUsedBytes = null;
        Long diskTotalBytes = null;
        try {
            FileStore fileStore = Files.getFileStore(diskPath);
            long totalSpace = fileStore.getTotalSpace();
            long unallocatedSpace = fileStore.getUnallocatedSpace();
            if (totalSpace >= 0 && unallocatedSpace >= 0) {
                diskTotalBytes = totalSpace;
                diskUsedBytes = Math.max(0, totalSpace - unallocatedSpace);
            }
        } catch (IOException ignored) {
            // Disk metrics are optional and may be unavailable on some platforms.
        }

        return new SystemMetricsResponse(
                timestamp,
                cpuUsagePercent,
                memoryUsedBytes,
                memoryTotalBytes,
                diskUsedBytes,
                diskTotalBytes,
                diskPath.toString());
    }

    private static OperatingSystemMXBean resolveOperatingSystemMxBean() {
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof OperatingSystemMXBean operatingSystemMXBean) {
            return operatingSystemMXBean;
        }
        return null;
    }

    private static Path detectDiskPath() {
        Path workingDirectory = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath();
        if (workingDirectory.getRoot() != null) {
            return workingDirectory.getRoot();
        }

        Iterator<Path> roots = FileSystems.getDefault().getRootDirectories().iterator();
        if (roots.hasNext()) {
            return roots.next();
        }

        return workingDirectory;
    }
}
