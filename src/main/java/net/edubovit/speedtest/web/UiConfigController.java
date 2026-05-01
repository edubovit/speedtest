package net.edubovit.speedtest.web;

import net.edubovit.speedtest.api.UiConfigResponse;
import net.edubovit.speedtest.config.HomeProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UiConfigController {

    private final HomeProperties homeProperties;

    public UiConfigController(HomeProperties homeProperties) {
        this.homeProperties = homeProperties;
    }

    @GetMapping("/config")
    public UiConfigResponse config() {
        return new UiConfigResponse(new UiConfigResponse.HomeConfig(
                homeProperties.show() && !homeProperties.location().isBlank(),
                homeProperties.location()
        ));
    }
}
