package dev.resqnet.mesh.routing;

/** Receives routing events in emission order, on the calling thread. */
@FunctionalInterface
public interface MeshEventListener {

    void onEvent(MeshEvent event);

    /** No-op listener for tests that only care about return values. */
    static MeshEventListener ignoring() {
        return event -> {
        };
    }
}