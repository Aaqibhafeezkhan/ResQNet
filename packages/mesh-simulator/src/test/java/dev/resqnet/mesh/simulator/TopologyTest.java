package dev.resqnet.mesh.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Topology construction.
 *
 * <p>Each shape is asserted structurally rather than by eyeballing a diagram,
 * because a silently disconnected topology makes every downstream routing result
 * meaningless.
 */
@DisplayName("Topology")
class TopologyTest {

    @Test
    @DisplayName("builds a line with exactly n-1 links")
    void buildsLine() {
        Topology topology = Topology.of(Topology.Shape.LINE, 6, 3, 1L);

        assertThat(topology.linkCount()).isEqualTo(5);
        assertThat(topology.neighbours(0)).containsExactly(1);
        assertThat(topology.neighbours(3)).containsExactlyInAnyOrder(2, 4);
        assertThat(topology.componentOf(0)).hasSize(6);
    }

    @Test
    @DisplayName("builds a ring where every node has degree two")
    void buildsRing() {
        Topology topology = Topology.of(Topology.Shape.RING, 5, 3, 1L);

        assertThat(topology.linkCount()).isEqualTo(5);
        topology.adjacency().values().forEach(neighbours ->
                assertThat(neighbours).hasSize(2));
    }

    @Test
    @DisplayName("builds a star with a single hub")
    void buildsStar() {
        Topology topology = Topology.of(Topology.Shape.STAR, 7, 3, 1L);

        assertThat(topology.neighbours(0)).hasSize(6);
        assertThat(topology.linkCount()).isEqualTo(6);
        assertThat(topology.nodesByDegreeDescending().get(0)).isZero();
    }

    @Test
    @DisplayName("builds a mesh where every node has several neighbours")
    void buildsMesh() {
        Topology topology = Topology.of(Topology.Shape.MESH, 8, 3, 1L);

        topology.adjacency().values().forEach(neighbours ->
                assertThat(neighbours).hasSizeGreaterThanOrEqualTo(3));
        assertThat(topology.componentOf(0)).hasSize(8);
    }

    @Test
    @DisplayName("builds exactly two disconnected clusters")
    void buildsTwoCluster() {
        Topology topology = Topology.of(Topology.Shape.TWO_CLUSTER, 10, 3, 1L);

        assertThat(topology.componentOf(0)).hasSize(5);
        assertThat(topology.componentOf(5)).hasSize(5);
    }

    @Test
    @DisplayName("builds a connected random graph from a seed")
    void buildsRandomGraph() {
        Topology topology = Topology.of(Topology.Shape.RANDOM, 12, 3, 99L);

        assertThat(topology.componentOf(0)).hasSize(12);
        assertThat(topology.linkCount()).isGreaterThanOrEqualTo(11);
    }

    @Test
    @DisplayName("produces the same random graph for the same seed")
    void randomGraphIsSeedStable() {
        Topology first = Topology.of(Topology.Shape.RANDOM, 15, 4, 12345L);
        Topology second = Topology.of(Topology.Shape.RANDOM, 15, 4, 12345L);

        assertThat(first.adjacency()).isEqualTo(second.adjacency());
    }

    @Test
    @DisplayName("produces a different graph for a different seed")
    void randomGraphVariesWithSeed() {
        Topology first = Topology.of(Topology.Shape.RANDOM, 20, 3, 1L);
        Topology second = Topology.of(Topology.Shape.RANDOM, 20, 3, 2L);

        assertThat(first.adjacency()).isNotEqualTo(second.adjacency());
    }

    @Test
    @DisplayName("lists every edge exactly once")
    void listsEdgesOnce() {
        Topology topology = Topology.of(Topology.Shape.MESH, 9, 3, 5L);

        assertThat(topology.edges()).hasSize(topology.linkCount());
        topology.edges().forEach(edge -> assertThat(edge[0]).isLessThan(edge[1]));
    }

    @Test
    @DisplayName("rejects a mesh with fewer than two nodes")
    void rejectsDegenerateMesh() {
        assertThatThrownBy(() -> Topology.of(Topology.Shape.LINE, 1, 3, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least two nodes");
    }

    @Test
    @DisplayName("finds isolated nodes when every link is cut")
    void isolatedNodesAreTheirOwnComponent() {
        Topology topology = Topology.of(Topology.Shape.STAR, 4, 3, 1L);

        assertThat(topology.componentOf(0)).hasSize(4);
        assertThat(topology.componentOf(1))
                .as("a leaf reaches the whole star through the hub")
                .hasSize(4);
    }
}