package dev.resqnet.mesh.protocol;

/**
 * Highest version of the ResQNet wire protocol implemented by this engine.
 *
 * <p>Every envelope carries the version that produced it. Receivers MUST reject
 * envelopes whose major version they do not implement rather than guessing.
 */
public final class ProtocolVersion {

    /** Wire protocol family. Breaking changes bump this number. */
    public static final int MAJOR = 1;

    /** Additive, backwards compatible revision within {@link #MAJOR}. */
    public static final int MINOR = 0;

    /** Current version stamped onto freshly created envelopes. */
    public static final int CURRENT = (MAJOR << 16) | MINOR;

    private ProtocolVersion() {
    }

    /** Returns the major component of a packed protocol version. */
    public static int major(int version) {
        return (version >>> 16) & 0xFFFF;
    }

    /** Returns the minor component of a packed protocol version. */
    public static int minor(int version) {
        return version & 0xFFFF;
    }

    /**
     * True when this engine can process the supplied packed version. Minor
     * versions are additive so any minor of the same major is accepted.
     */
    public static boolean isSupported(int version) {
        return version >= 0 && major(version) == MAJOR;
    }
}