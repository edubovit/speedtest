package net.edubovit.speedtest.api;

public record UploadResponse(long receivedBytes, double serverElapsedMillis) {
}
