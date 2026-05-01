package net.edubovit.speedtest.api;

public record UiConfigResponse(HomeConfig home) {

    public record HomeConfig(boolean show, String location) {
    }
}
