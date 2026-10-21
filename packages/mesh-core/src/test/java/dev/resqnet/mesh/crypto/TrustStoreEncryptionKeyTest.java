package dev.resqnet.mesh.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TrustStore encryption keys")
class TrustStoreEncryptionKeyTest {

    @Test
    @DisplayName("records a peer's HPKE key only after the signing identity is known")
    void requiresKnownPeer() throws Exception {
        InMemorySigningIdentity alice = InMemorySigningIdentity.generate("Alice");
        EncryptionIdentity encryption = EncryptionIdentity.generate();
        TrustStore store = new TrustStore();

        assertThatThrownBy(() -> store.rememberEncryptionKey(
                alice.deviceId(), encryption.publicKeyBytes(), Instant.EPOCH))
                .isInstanceOf(GeneralSecurityException.class);

        store.rememberUnverified("Alice", alice.publicKey(), Instant.EPOCH);
        TrustStore.EncryptionKey key = store.rememberEncryptionKey(
                alice.deviceId(), encryption.publicKeyBytes(), Instant.EPOCH);

        assertThat(key.keyId()).isEqualTo(encryption.keyId());
        assertThat(key.publicKey()).isEqualTo(encryption.publicKeyBytes());
        assertThat(store.encryptionKey(alice.deviceId())).contains(key);
        assertThat(store.canEncryptTo(alice.deviceId()))
                .as("unverified peers are not encryption targets")
                .isFalse();
    }

    @Test
    @DisplayName("allows encryption once the peer is verified")
    void verificationUnlocksEncryption() throws Exception {
        InMemorySigningIdentity alice = InMemorySigningIdentity.generate("Alice");
        EncryptionIdentity encryption = EncryptionIdentity.generate();
        TrustStore store = new TrustStore();
        store.rememberUnverified("Alice", alice.publicKey(), Instant.EPOCH);
        store.rememberEncryptionKey(alice.deviceId(), encryption.publicKeyBytes(),
                Instant.EPOCH);

        store.markVerified(alice.deviceId(), Instant.EPOCH);

        assertThat(store.canEncryptTo(alice.deviceId())).isTrue();
    }

    @Test
    @DisplayName("refuses a changed HPKE key for an identity already on file")
    void refusesKeyRotation() throws Exception {
        InMemorySigningIdentity alice = InMemorySigningIdentity.generate("Alice");
        EncryptionIdentity first = EncryptionIdentity.generate();
        EncryptionIdentity second = EncryptionIdentity.generate();
        TrustStore store = new TrustStore();
        store.rememberUnverified("Alice", alice.publicKey(), Instant.EPOCH);
        store.rememberEncryptionKey(alice.deviceId(), first.publicKeyBytes(), Instant.EPOCH);

        assertThatThrownBy(() -> store.rememberEncryptionKey(
                alice.deviceId(), second.publicKeyBytes(), Instant.EPOCH.plusSeconds(1)))
                .isInstanceOf(GeneralSecurityException.class)
                .hasMessageContaining("different encryption key");
        assertThat(store.encryptionKey(alice.deviceId()).orElseThrow().keyId())
                .isEqualTo(first.keyId());
    }

    @Test
    @DisplayName("a refreshed identical key keeps its original first-seen time")
    void refreshesIdenticalKey() throws Exception {
        InMemorySigningIdentity alice = InMemorySigningIdentity.generate("Alice");
        EncryptionIdentity encryption = EncryptionIdentity.generate();
        TrustStore store = new TrustStore();
        store.rememberUnverified("Alice", alice.publicKey(), Instant.EPOCH);
        store.rememberEncryptionKey(alice.deviceId(), encryption.publicKeyBytes(),
                Instant.EPOCH);

        TrustStore.EncryptionKey refreshed = store.rememberEncryptionKey(
                alice.deviceId(), encryption.publicKeyBytes(), Instant.EPOCH.plusSeconds(90));

        assertThat(refreshed.firstSeen()).isEqualTo(Instant.EPOCH);
        assertThat(refreshed.lastSeen()).isEqualTo(Instant.EPOCH.plusSeconds(90));
    }

    @Test
    @DisplayName("a blocked or forgotten peer can no longer be encrypted to")
    void blockAndForgetRemoveAccess() throws Exception {
        InMemorySigningIdentity alice = InMemorySigningIdentity.generate("Alice");
        EncryptionIdentity encryption = EncryptionIdentity.generate();
        TrustStore store = new TrustStore();
        store.rememberUnverified("Alice", alice.publicKey(), Instant.EPOCH);
        store.rememberEncryptionKey(alice.deviceId(), encryption.publicKeyBytes(),
                Instant.EPOCH);
        store.markVerified(alice.deviceId(), Instant.EPOCH);

        store.block(alice.deviceId());
        assertThat(store.encryptionKey(alice.deviceId())).isEmpty();
        assertThat(store.canEncryptTo(alice.deviceId())).isFalse();
        assertThatThrownBy(() -> store.rememberEncryptionKey(
                alice.deviceId(), encryption.publicKeyBytes(), Instant.EPOCH))
                .isInstanceOf(GeneralSecurityException.class);

        store.forget(alice.deviceId());
        assertThat(store.encryptionKey(alice.deviceId())).isEmpty();
    }

    @Test
    @DisplayName("reloads proof-bound keys from persistence without trusting orphan rows")
    void persistsAcrossRestart() throws Exception {
        InMemorySigningIdentity alice = InMemorySigningIdentity.generate("Alice");
        InMemorySigningIdentity bob = InMemorySigningIdentity.generate("Bob");
        EncryptionIdentity aliceKey = EncryptionIdentity.generate();
        EncryptionIdentity bobKey = EncryptionIdentity.generate();
        RecordingPersistence persistence = new RecordingPersistence();

        TrustStore first = new TrustStore(persistence, 32);
        first.rememberUnverified("Alice", alice.publicKey(), Instant.EPOCH);
        first.rememberUnverified("Bob", bob.publicKey(), Instant.EPOCH);
        first.rememberEncryptionKey(alice.deviceId(), aliceKey.publicKeyBytes(),
                Instant.EPOCH);
        first.rememberEncryptionKey(bob.deviceId(), bobKey.publicKeyBytes(), Instant.EPOCH);
        first.forget(bob.deviceId());

        TrustStore reloaded = new TrustStore(persistence, 32);

        assertThat(reloaded.encryptionKey(alice.deviceId()).orElseThrow().keyId())
                .isEqualTo(aliceKey.keyId());
        assertThat(reloaded.encryptionKey(bob.deviceId())).isEmpty();
    }

    private static final class RecordingPersistence implements TrustStore.Persistence {
        private final List<TrustStore.Entry> peers = new ArrayList<>();
        private final List<TrustStore.EncryptionKey> keys = new ArrayList<>();

        @Override
        public void save(TrustStore.Entry entry) {
            peers.removeIf(e -> e.deviceId().equals(entry.deviceId()));
            peers.add(entry);
        }

        @Override
        public Collection<TrustStore.Entry> loadAll() {
            return List.copyOf(peers);
        }

        @Override
        public void remove(String deviceId) {
            peers.removeIf(e -> e.deviceId().equals(deviceId));
        }

        @Override
        public void saveEncryptionKey(TrustStore.EncryptionKey key) {
            keys.removeIf(k -> k.deviceId().equals(key.deviceId()));
            keys.add(key);
        }

        @Override
        public Collection<TrustStore.EncryptionKey> loadEncryptionKeys() {
            return List.copyOf(keys);
        }

        @Override
        public void removeEncryptionKey(String deviceId) {
            keys.removeIf(k -> k.deviceId().equals(deviceId));
        }
    }
}
