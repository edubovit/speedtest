package net.edubovit.speedtest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Random;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SpeedtestApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pingEndpointReturnsNoContentAndNoCacheHeaders() throws Exception {
        mockMvc.perform(get("/api/speedtest/ping"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store, no-cache, max-age=0, must-revalidate, no-transform"));
    }

    @Test
    void configEndpointReturnsDefaultSettings() throws Exception {
        mockMvc.perform(get("/api/speedtest/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ping.warmupSamples").value(2))
                .andExpect(jsonPath("$.download.durationSeconds").value(12))
                .andExpect(jsonPath("$.upload.initialChunkBytes").value(262_144))
                .andExpect(jsonPath("$.estimatedTotalDurationSeconds").value(27));
    }

    @Test
    void systemMetricsEndpointReturnsDashboardMetrics() throws Exception {
        mockMvc.perform(get("/api/system-metrics"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, no-cache, max-age=0, must-revalidate, no-transform"))
                .andExpect(content().string(allOf(
                        containsString("\"timestamp\":"),
                        containsString("\"cpuUsagePercent\":"),
                        containsString("\"memoryUsedBytes\":"),
                        containsString("\"memoryTotalBytes\":"),
                        containsString("\"diskUsedBytes\":"),
                        containsString("\"diskTotalBytes\":"),
                        containsString("\"diskPath\":"))));
    }

    @Test
    void uploadEndpointCountsReceivedBytes() throws Exception {
        byte[] payload = new byte[4_096];
        new Random(7L).nextBytes(payload);

        mockMvc.perform(post("/api/speedtest/upload")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receivedBytes").value(payload.length));
    }

    @Test
    void staticIndexUsesRelativeAssetPaths() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"favicon.svg\"")))
                .andExpect(content().string(containsString("href=\"styles.css\"")))
                .andExpect(content().string(containsString("src=\"app.js\"")))
                .andExpect(content().string(not(containsString("href=\"/favicon.svg\""))))
                .andExpect(content().string(not(containsString("href=\"/styles.css\""))))
                .andExpect(content().string(not(containsString("src=\"/app.js\""))));
    }

    @Test
    void clientScriptBuildsApiUrlsFromApplicationBase() throws Exception {
        mockMvc.perform(get("/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("const APP_BASE_URL = new URL('.', scriptUrl);")))
                .andExpect(content().string(containsString("appUrl('api/speedtest/config'")))
                .andExpect(content().string(containsString("appUrl('api/system-metrics'")))
                .andExpect(content().string(not(containsString("'/api/"))))
                .andExpect(content().string(not(containsString("\"/api/"))))
                .andExpect(content().string(not(containsString("`/api/"))));
    }
}
