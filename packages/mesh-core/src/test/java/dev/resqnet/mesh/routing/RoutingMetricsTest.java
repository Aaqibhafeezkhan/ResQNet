
package dev.resqnet.mesh.routing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Routing metrics")
class RoutingMetricsTest {

    @Test
    @DisplayName("resets the directed originated counter")
    void resetClearsDirectedOriginatedCounter() {
        RoutingMetrics metrics = new RoutingMetrics();

        metrics.recordOriginated(true);

        assertThat(metrics.directedOriginatedCount()).isEqualTo(1);

        metrics.reset();

        assertThat(metrics.directedOriginatedCount())
                .as("directed originated count should be zero after reset")
                .isZero();
    }
}
