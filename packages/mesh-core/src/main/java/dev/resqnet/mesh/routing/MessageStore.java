package dev.resqnet.mesh.routing;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import dev.resqnet.mesh.protocol.DeliveryState;

/**
 * Durable queue of messages this device must not lose.
 *
 * <p>Store-and-forward is the difference between a mesh and a walkie-talkie. A
 * message composed while every peer is out of range has to survive until a peer
 * comes back, survive the process being killed, and survive the phone being
 * powered off. That only works if the queue is a real database, which is why
 * this is an interface with a SQLite implementation on Android and an in-memory
 * implementation in the simulator.
 *
 * <p>Every method is synchronous and must be fast: on Android these run on a
 * single background executor against a local database, never on the main thread.
 */
public interface MessageStore extends AutoCloseable {

    /** Inserts or replaces a record, returning it with its assigned row id. */
    StoredMessage upsert(StoredMessage message);

    /** Looks up a single message by its protocol message id. */
    Optional<StoredMessage> find(String messageId);

    /** All messages that are not in a terminal state, soonest deadline first. */
    List<StoredMessage> pending();

    /** All messages, newest first. Used by the inbox and history screens. */
    List<StoredMessage> recent(int limit);

    /**
     * Records a state transition. Implementations must keep
     * {@link StoredMessage#lastDetail()} for the audit trail.
     */
    StoredMessage updateState(String messageId, DeliveryState state, String detail);

    /** Permanently removes a record once it is no longer useful. */
    boolean delete(String messageId);

    /** Count of non-terminal records, used for queue pressure decisions. */
    int pendingCount();

    /** Count of records in a given state, used for dashboard metrics. */
    int countByState(DeliveryState state);

    /**
     * Marks expired records as {@link DeliveryState#EXPIRED} and drops their
     * payloads.
     *
     * @return number of records expired
     */
    int expireStale(long nowMillis);

    /**
     * Enforces the capacity bound by dropping the lowest priority, oldest
     * records first. {@link dev.resqnet.mesh.protocol.Priority#SOS} records are
     * protected.
     *
     * @return number of records evicted
     */
    int enforceCapacity(int maxRecords);

    /** Every record, for diagnostics and the responder dashboard. */
    Collection<StoredMessage> all();

    @Override
    void close();
}