package net.edubovit.speedtest.api;

public record SpeedtestConfigResponse(
        Ping ping,
        Download download,
        Upload upload,
        int estimatedTotalDurationSeconds) {

    public record Ping(int warmupSamples, int measuredSamples, int intervalMillis) {
    }

    public record Download(int durationSeconds) {
    }

    public record Upload(
            int durationSeconds,
            int initialChunkBytes,
            int minChunkBytes,
            int maxChunkBytes,
            int adaptiveTargetMillis) {
    }
}
