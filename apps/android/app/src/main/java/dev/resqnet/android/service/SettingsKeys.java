package dev.resqnet.android.service;

/** Keys used in the local settings table. */
public final class SettingsKeys {

    /** Whether the user left the mesh running. Read by the boot receiver. */
    public static final String MESH_ENABLED = "mesh.enabled";

    /** Display label this device advertises to peers. */
    public static final String DISPLAY_NAME = "identity.display_name";

    /** Whether the user opted in to attaching location to messages. */
    public static final String LOCATION_ENABLED = "location.enabled";

    /** Advisory triage suggestions enabled. */
    public static final String TRIAGE_ENABLED = "triage.enabled";

    /** Endpoint of the coordination gateway, empty when unconfigured. */
    public static final String GATEWAY_URL = "gateway.url";

    private SettingsKeys() {
    }
}
