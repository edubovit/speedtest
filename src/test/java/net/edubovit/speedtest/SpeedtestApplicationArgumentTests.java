package net.edubovit.speedtest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpeedtestApplicationArgumentTests {

    @Test
    void expandsConfigArgumentWithSeparatePath() {
        assertThat(SpeedtestApplication.expandConfigArgument(new String[] {"--config", "config.yaml", "--server.port=9090"}))
                .containsExactly(
                        "--spring.config.additional-location=file:config.yaml",
                        "--server.port=9090");
    }

    @Test
    void expandsConfigArgumentWithEqualsPath() {
        assertThat(SpeedtestApplication.expandConfigArgument(new String[] {"--config=config.yaml"}))
                .containsExactly("--spring.config.additional-location=file:config.yaml");
    }

    @Test
    void failsWhenConfigPathIsMissing() {
        assertThatThrownBy(() -> SpeedtestApplication.expandConfigArgument(new String[] {"--config"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("--config requires a configuration file path");
    }
}
