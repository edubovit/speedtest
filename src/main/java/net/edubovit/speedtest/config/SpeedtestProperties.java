package net.edubovit.speedtest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "speedtest")
public class SpeedtestProperties {

    private final Ping ping = new Ping();
    private final Download download = new Download();
    private final Upload upload = new Upload();

    public Ping getPing() {
        return ping;
    }

    public Download getDownload() {
        return download;
    }

    public Upload getUpload() {
        return upload;
    }

    public int estimatedTotalDurationSeconds() {
        int pingMillis = (ping.getWarmupSamples() + ping.getMeasuredSamples()) * ping.getIntervalMillis();
        return Math.round(pingMillis / 1000.0f) + download.getDurationSeconds() + upload.getDurationSeconds();
    }

    public static class Ping {

        private int warmupSamples = 2;
        private int measuredSamples = 10;
        private int intervalMillis = 250;

        public int getWarmupSamples() {
            return warmupSamples;
        }

        public void setWarmupSamples(int warmupSamples) {
            this.warmupSamples = warmupSamples;
        }

        public int getMeasuredSamples() {
            return measuredSamples;
        }

        public void setMeasuredSamples(int measuredSamples) {
            this.measuredSamples = measuredSamples;
        }

        public int getIntervalMillis() {
            return intervalMillis;
        }

        public void setIntervalMillis(int intervalMillis) {
            this.intervalMillis = intervalMillis;
        }
    }

    public static class Download {

        private int durationSeconds = 12;
        private int bufferBytes = 262_144;

        public int getDurationSeconds() {
            return durationSeconds;
        }

        public void setDurationSeconds(int durationSeconds) {
            this.durationSeconds = durationSeconds;
        }

        public int getBufferBytes() {
            return bufferBytes;
        }

        public void setBufferBytes(int bufferBytes) {
            this.bufferBytes = bufferBytes;
        }
    }

    public static class Upload {

        private int durationSeconds = 12;
        private int initialChunkBytes = 262_144;
        private int minChunkBytes = 262_144;
        private int maxChunkBytes = 16_777_216;
        private int adaptiveTargetMillis = 750;

        public int getDurationSeconds() {
            return durationSeconds;
        }

        public void setDurationSeconds(int durationSeconds) {
            this.durationSeconds = durationSeconds;
        }

        public int getInitialChunkBytes() {
            return initialChunkBytes;
        }

        public void setInitialChunkBytes(int initialChunkBytes) {
            this.initialChunkBytes = initialChunkBytes;
        }

        public int getMinChunkBytes() {
            return minChunkBytes;
        }

        public void setMinChunkBytes(int minChunkBytes) {
            this.minChunkBytes = minChunkBytes;
        }

        public int getMaxChunkBytes() {
            return maxChunkBytes;
        }

        public void setMaxChunkBytes(int maxChunkBytes) {
            this.maxChunkBytes = maxChunkBytes;
        }

        public int getAdaptiveTargetMillis() {
            return adaptiveTargetMillis;
        }

        public void setAdaptiveTargetMillis(int adaptiveTargetMillis) {
            this.adaptiveTargetMillis = adaptiveTargetMillis;
        }
    }
}
