package dev.resqnet.mesh.simulator.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The demonstration is a claim, so it is tested like one.
 *
 * <p>{@code DemoScenario} reports a list of stages that did not behave as
 * described. Running it from a test means a regression in the routing engine
 * becomes a failed build rather than an awkward moment in front of a room.
 *
 * <p>The first version of this scenario was wrong about the engine. It cut the
 * relay link after the first message had already been acknowledged, then
 * asserted that the acknowledgement arrived in a later stage. It did not: the
 * real router completed the round trip during the earlier pump, and the demo
 * correctly reported that the origin had claimed confirmation before one
 * arrived. The stages were rewritten to partition a message that is genuinely
 * in flight, which is the case store-and-forward actually exists for.
 */
@DisplayName("Demonstration scenario")
@Tag("slow")
class DemoScenarioTest {

    @Test
    @DisplayName("every stage behaves as the demonstration claims")
    void allStagesBehave() {
        assertThat(DemoScenario.run(false))
                .as("stages that did not behave as described")
                .isEmpty();
    }

    @Test
    @DisplayName("the transcript never implies the hardware has been tested")
    void transcriptIsHonestAboutSimulation() {
        String transcript = DemoScenario.transcript(false);
        assertThat(transcript)
                .as("the most damaging thing this project could ship is a demo that"
                        + " reads as though the radio path had been verified")
                .contains("SIMULATED RADIO");
        assertThat(transcript).contains("has not been run");
    }

    @Test
    @DisplayName("the transcript shows the two delivery states apart")
    void transcriptDistinguishesDeliveryFromConfirmation() {
        String transcript = DemoScenario.transcript(false);
        assertThat(transcript)
                .as("SENT_TO_PEER and ACKNOWLEDGED are the distinction the whole"
                        + " project rests on, so the demo has to show it")
                .contains("ACKNOWLEDGED");
        assertThat(transcript).contains("proves one radio hop");
    }
}
