package net.edubovit.speedtest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SpeedtestApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpeedtestApplication.class, args);
    }
}
