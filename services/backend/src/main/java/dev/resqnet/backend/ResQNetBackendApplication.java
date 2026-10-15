package dev.resqnet.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Coordination gateway entry point.
 *
 * <p>What this service is: somewhere to look when you want a single screen
 * showing what several devices have reported.
 *
 * <p>What this service is not: part of the emergency network. A handset relays
 * and delivers with this process stopped, unreachable or deleted. Nothing on the
 * critical path depends on it, and a design that made it one would convert a
 * backend outage into a communications outage at exactly the wrong moment.
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class ResQNetBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResQNetBackendApplication.class, args);
    }
}
