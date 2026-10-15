package dev.resqnet.mesh.protocol;

/**
 * Traffic class. Priority drives relay queue eviction when a device is under
 * memory or battery pressure: {@link #SOS} traffic is never evicted to make room
 * for lower priority chatter.
 */
public enum Priority {

    /** Informational chatter that yields to everything. */
    LOW(0),
    /** Ordinary user messages. */
    NORMAL(1),
    /** User raised urgency, e.g. a follow-up on an open incident. */
    URGENT(2),
    /** Life safety traffic. Protected against queue eviction. */
    SOS(3);

    private final int level;

    Priority(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    /** True when this priority may not be dropped to make room for a lower one. */
    public boolean protectedFromEviction() {
        return this == SOS;
    }

    public static Priority fromLevel(int level) throws ProtocolException {
        for (Priority p : values()) {
            if (p.level == level) {
                return p;
            }
        }
        throw new ProtocolException(RejectReason.MALFORMED, "Invalid priority " + level);
    }
}