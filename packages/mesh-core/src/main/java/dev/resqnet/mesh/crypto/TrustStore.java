package dev.resqnet.mesh.crypto;

import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Records which peers this device has met and how much it trusts them.
 *
 * <p>Keys enter the store through an explicit, user-visible exchange. A key that
 * has been seen once but never confirmed in person stays {@link Trust#UNVERIFIED}:
 * it may carry informational traffic, but the router refuses to relay
 * life-safety envelopes on its word alone. Confirmation is the two humans
 * comparing eight fingerprint characters on two screens. That is the entire
 * authentication model, and it is honest about its limits: in a disaster,
 * devices you have never met can still tell you a flood is coming, but they
 * cannot impersonate the responder you already verified.
 */
public final class TrustStore {

    /** How much this device trusts a key. */
    public enum Trust {
        /** Key seen, never confirmed by a human. Informational traffic only. */
        UNVERIFIED,
        /** Fingerprint confirmed in person by the device owner. */
        VERIFIED,
        /** Explicitly revoked. All traffic from this origin is dropped. */
        BLOCKED
    }

    /** One known peer key and its trust level. */
    public static final class Entry implements PublicKeyHolder {
        private final String deviceId;
        private final PublicKey publicKey;
        private final String label;
        private final Trust trust;
        private final Instant firstSeen;
        private final Instant lastSeen;

        Entry(String deviceId, PublicKey publicKey, String label, Trust trust,
              Instant firstSeen, Instant lastSeen) {
            this.deviceId = deviceId;
            this.publicKey = publicKey;
            this.label = label;
            this.trust = trust;
            this.firstSeen = firstSeen;
            this.lastSeen = lastSeen;
        }

        @Override
        public String deviceId() {
            return deviceId;
        }

        @Override
        public String keyFingerprint() {
            return DeviceIdentity.fingerprint(publicKey);
        }

        @Override
        public PublicKey publicKey() {
            return publicKey;
        }

        public String label() {
            return label;
        }

        public Trust trust() {
            return trust;
        }

        public Instant firstSeen() {
            return firstSeen;
        }

        public Instant lastSeen() {
            return lastSeen;
        }

        /** Safe for logging and for the responder dashboard. */
        @Override
        public String toString() {
            return label + "[" + DeviceIdentity.shortDeviceId(deviceId)
                    + " trust=" + trust + "]";
        }
    }

    /**
     * Rebuilds an entry from persisted fields.
     *
     * <p>Exposed so platform adapters can reload the trust store from their own
     * storage. The device id is recomputed from the key rather than accepted from
     * the caller, so a tampered database cannot claim an identity it holds no key
     * for.
     */
    public static Entry entryOf(PublicKey publicKey, String label, Trust trust,
                                Instant firstSeen, Instant lastSeen) {
        Objects.requireNonNull(publicKey, "publicKey");
        return new Entry(DeviceIdentity.deviceId(publicKey), publicKey,
                label == null ? "unknown" : label,
                trust == null ? Trust.UNVERIFIED : trust, firstSeen, lastSeen);
    }

    /**
     * One peer's proof-bound HPKE key.
     *
     * <p>Kept beside the signing identity rather than inside it because the
     * proof is what links the two, and because a peer may legitimately have
     * announced no encryption key yet. The key id is recomputed on every
     * construction, so a corrupted row cannot smuggle a different identifier
     * past the router.
     */
    public static final class EncryptionKey {
        private final String deviceId;
        private final byte[] publicKey;
        private final byte[] keyIdBytes;
        private final String keyId;
        private final Instant firstSeen;
        private final Instant lastSeen;

        private EncryptionKey(String deviceId, byte[] publicKey, Instant firstSeen,
                              Instant lastSeen) {
            this.deviceId = Objects.requireNonNull(deviceId, "deviceId");
            if (publicKey == null
                    || publicKey.length != EncryptionIdentity.PUBLIC_KEY_BYTES) {
                throw new IllegalArgumentException("HPKE public key must be "
                        + EncryptionIdentity.PUBLIC_KEY_BYTES + " bytes");
            }
            this.publicKey = publicKey.clone();
            this.keyIdBytes = EncryptionIdentity.keyIdBytes(publicKey);
            this.keyId = DeviceIdentity.toHex(this.keyIdBytes);
            this.firstSeen = Objects.requireNonNull(firstSeen, "firstSeen");
            this.lastSeen = Objects.requireNonNull(lastSeen, "lastSeen");
        }

        public String deviceId() {
            return deviceId;
        }

        public byte[] publicKey() {
            return publicKey.clone();
        }

        public String keyId() {
            return keyId;
        }

        /** Raw SHA-256 key identifier carried inside a protected payload. */
        public byte[] keyIdBytes() {
            return keyIdBytes.clone();
        }

        public Instant firstSeen() {
            return firstSeen;
        }

        public Instant lastSeen() {
            return lastSeen;
        }

        @Override
        public String toString() {
            return "EncryptionKey{" + DeviceIdentity.shortDeviceId(deviceId)
                    + " key=" + keyId.substring(0, 12) + "}";
        }
    }

    /**
     * Rebuilds a persisted encryption key.
     *
     * <p>The key id is recomputed from the bytes; the caller does not get to
     * supply it. The trust store only loads a key when the corresponding
     * signing identity is known and not blocked.
     */
    public static EncryptionKey encryptionKeyOf(String deviceId, byte[] publicKey,
                                                Instant firstSeen, Instant lastSeen) {
        return new EncryptionKey(deviceId, publicKey, firstSeen, lastSeen);
    }

    /** Persistence hook. The Android app writes entries to SQLite. */
    public interface Persistence {
        void save(Entry entry);

        Collection<Entry> loadAll();

        void remove(String deviceId);

        /** Persists one proof-bound encryption key. */
        default void saveEncryptionKey(EncryptionKey key) {
        }

        /** Loads every previously persisted encryption key. */
        default Collection<EncryptionKey> loadEncryptionKeys() {
            return List.of();
        }

        /** Removes the encryption key associated with a peer. */
        default void removeEncryptionKey(String deviceId) {
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, EncryptionKey> encryptionKeys = new LinkedHashMap<>();
    private final Persistence persistence;
    private final int maxEntries;

    /** In-memory store bounded to 512 peers, which is far beyond any real mesh. */
    public TrustStore() {
        this(null, 512);
    }

    public TrustStore(Persistence persistence, int maxEntries) {
        this.persistence = persistence;
        this.maxEntries = maxEntries;
        if (persistence != null) {
            for (Entry e : persistence.loadAll()) {
                entries.put(e.deviceId(), e);
            }
            for (EncryptionKey key : persistence.loadEncryptionKeys()) {
                Entry peer = entries.get(key.deviceId());
                if (peer != null && peer.trust() != Trust.BLOCKED) {
                    encryptionKeys.put(key.deviceId(), key);
                }
            }
        }
    }

    /**
     * Records a key we have just learned about.
     *
     * <p>If the device id is unknown the entry starts {@link Trust#UNVERIFIED}.
     * If it is known with the <em>same</em> key the label and timestamp are
     * refreshed and the trust level is preserved. If it is known with a
     * <em>different</em> key that is a hard error: reusing a device id with a
     * new key is either a wiped device or an impersonation attempt, and the
     * caller must not silently swap the key.
     *
     * @throws GeneralSecurityException when a known device id presents a new key
     */
    public synchronized Entry rememberUnverified(String label, PublicKey publicKey, Instant now)
            throws GeneralSecurityException {
        Objects.requireNonNull(publicKey, "publicKey");
        String deviceId = DeviceIdentity.deviceId(publicKey);
        Entry existing = entries.get(deviceId);
        if (existing != null) {
            if (!existing.keyFingerprint().equals(DeviceIdentity.fingerprint(publicKey))) {
                throw new GeneralSecurityException(
                        "Device id " + DeviceIdentity.shortDeviceId(deviceId)
                                + " presented a different key; refusing to replace it");
            }
            Entry refreshed = new Entry(deviceId, publicKey,
                    label == null ? existing.label : label,
                    existing.trust, existing.firstSeen, now);
            entries.put(deviceId, refreshed);
            persist(refreshed);
            return refreshed;
        }
        evictIfFull();
        Entry entry = new Entry(deviceId, publicKey, label == null ? "unknown" : label,
                Trust.UNVERIFIED, now, now);
        entries.put(deviceId, entry);
        persist(entry);
        return entry;
    }

    /** Promotes a known entry to {@link Trust#VERIFIED}. */
    public synchronized Optional<Entry> markVerified(String deviceId, Instant now) {
        Entry existing = entries.get(deviceId);
        if (existing == null) {
            return Optional.empty();
        }
        Entry updated = new Entry(existing.deviceId(), existing.publicKey(), existing.label(),
                Trust.VERIFIED, existing.firstSeen(), now);
        entries.put(deviceId, updated);
        persist(updated);
        return Optional.of(updated);
    }

    /** Revokes a peer. Idempotent. */
    public synchronized boolean block(String deviceId) {
        Entry existing = entries.get(deviceId);
        if (existing == null) {
            return false;
        }
        Entry updated = new Entry(existing.deviceId(), existing.publicKey(), existing.label(),
                Trust.BLOCKED, existing.firstSeen(), existing.lastSeen());
        entries.put(deviceId, updated);
        persist(updated);
        return true;
    }

    /** Removes a peer entirely, e.g. after a factory reset of the contact list. */
    public synchronized boolean forget(String deviceId) {
        boolean removed = entries.remove(deviceId) != null;
        encryptionKeys.remove(deviceId);
        if (removed && persistence != null) {
            persistence.remove(deviceId);
            persistence.removeEncryptionKey(deviceId);
        }
        return removed;
    }

    /**
     * Records a proof-bound HPKE key for a known peer.
     *
     * <p>Key replacement is a hard error even when the peer is merely
     * unverified. A changed encryption key on a known signing identity is
     * indistinguishable from an attacker who captured the announcement and
     * substituted their own HPKE key; until key rotation has a user-visible
     * protocol, refusing is the only safe answer.
     *
     * @throws GeneralSecurityException when the peer is unknown/blocked or
     *     already has a different encryption key
     */
    public synchronized EncryptionKey rememberEncryptionKey(String deviceId,
                                                            byte[] publicKey,
                                                            Instant now)
            throws GeneralSecurityException {
        Entry peer = entries.get(deviceId);
        if (peer == null) {
            throw new GeneralSecurityException(
                    "Cannot record an encryption key for an unknown device");
        }
        if (peer.trust() == Trust.BLOCKED) {
            throw new GeneralSecurityException("Cannot record a key for a blocked device");
        }
        if (publicKey == null
                || publicKey.length != EncryptionIdentity.PUBLIC_KEY_BYTES) {
            throw new GeneralSecurityException("HPKE public key has the wrong length");
        }
        Objects.requireNonNull(now, "now");
        String newKeyId = EncryptionIdentity.keyId(publicKey);
        EncryptionKey existing = encryptionKeys.get(deviceId);
        if (existing != null) {
            if (!existing.keyId().equals(newKeyId)) {
                throw new GeneralSecurityException(
                        "Device " + DeviceIdentity.shortDeviceId(deviceId)
                                + " presented a different encryption key; refusing rotation");
            }
            EncryptionKey refreshed = new EncryptionKey(deviceId, publicKey,
                    existing.firstSeen(), now);
            encryptionKeys.put(deviceId, refreshed);
            if (persistence != null) {
                persistence.saveEncryptionKey(refreshed);
            }
            return refreshed;
        }
        EncryptionKey key = new EncryptionKey(deviceId, publicKey, now, now);
        encryptionKeys.put(deviceId, key);
        if (persistence != null) {
            persistence.saveEncryptionKey(key);
        }
        return key;
    }

    /**
     * Resolves a peer's encryption key.
     *
     * <p>Returns empty for unknown and blocked peers, matching
     * {@link #resolve(String)}: neither is a valid encryption target.
     */
    public synchronized Optional<EncryptionKey> encryptionKey(String deviceId) {
        Entry peer = entries.get(deviceId);
        if (peer == null || peer.trust() == Trust.BLOCKED) {
            return Optional.empty();
        }
        return Optional.ofNullable(encryptionKeys.get(deviceId));
    }

    /** True when the peer is verified and has a proof-bound encryption key. */
    public synchronized boolean canEncryptTo(String deviceId) {
        Entry peer = entries.get(deviceId);
        return peer != null && peer.trust() == Trust.VERIFIED
                && encryptionKeys.containsKey(deviceId);
    }

    /**
     * Resolves the key for an origin id.
     *
     * @return empty when unknown or blocked, so the router treats both the same
     *     way: no signature verification, no relay, no inbox entry
     */
    public synchronized Optional<PublicKeyHolder> resolve(String deviceId) {
        Entry entry = entries.get(deviceId);
        if (entry == null || entry.trust() == Trust.BLOCKED) {
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    public synchronized Optional<Entry> entry(String deviceId) {
        return Optional.ofNullable(entries.get(deviceId));
    }

    /** True when this device may relay life-safety envelopes from the origin. */
    public synchronized boolean mayRelayLifeSafety(String deviceId) {
        return entry(deviceId).map(e -> e.trust() == Trust.VERIFIED).orElse(false);
    }

    public synchronized Collection<Entry> entries() {
        return Collections.unmodifiableCollection(new LinkedHashMap<>(entries).values());
    }

    public synchronized int size() {
        return entries.size();
    }

    private void persist(Entry entry) {
        if (persistence != null) {
            persistence.save(entry);
        }
    }

    private void evictIfFull() {
        if (entries.size() < maxEntries) {
            return;
        }
        String oldest = null;
        Instant oldestSeen = null;
        for (Entry e : entries.values()) {
            if (e.trust() == Trust.VERIFIED) {
                continue;
            }
            if (oldestSeen == null || e.lastSeen().isBefore(oldestSeen)) {
                oldestSeen = e.lastSeen();
                oldest = e.deviceId();
            }
        }
        if (oldest != null) {
            forget(oldest);
        }
    }
}