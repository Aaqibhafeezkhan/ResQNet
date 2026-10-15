package dev.resqnet.mesh.crypto;

import java.security.PublicKey;

/**
 * A device whose public key is known but whose private key this process does
 * not (and should not) hold.
 *
 * <p>Splitting this out of {@link SigningIdentity} keeps the router honest: it
 * can only ever <em>verify</em> with a peer key, never sign on a peer's behalf.
 */
public interface PublicKeyHolder {

    /** Stable, key-derived device identifier. */
    String deviceId();

    /** SHA-256 fingerprint of {@link #publicKey()}, lowercase hex. */
    String keyFingerprint();

    PublicKey publicKey();
}