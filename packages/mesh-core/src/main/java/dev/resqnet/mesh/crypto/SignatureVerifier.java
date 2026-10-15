package dev.resqnet.mesh.crypto;

import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;

import dev.resqnet.mesh.protocol.MessageEnvelope;

/**
 * Signature verification for mesh envelopes.
 *
 * <p>ResQNet does not accept envelopes from origins whose public key it has
 * never seen. There is no "trust on first use" shortcut here on purpose: an
 * emergency mesh is exactly the environment where a single spoofed SOS could
 * divert scarce responders. Keys are exchanged explicitly, out of band, via
 * {@code IDENTITY} envelopes whose short fingerprints the two humans compare
 * in person, and the exchange is recorded in the trust store.
 */
public final class SignatureVerifier {

    /** Algorithm this engine produces and verifies. */
    public static final String ALGORITHM = DeviceIdentity.SIGNATURE_ALGORITHM;

    private SignatureVerifier() {
    }

    /** Verifies a raw signature over raw bytes. */
    public static boolean verify(PublicKey publicKey, byte[] data, byte[] signature)
            throws GeneralSecurityException {
        if (publicKey == null || data == null || signature == null || signature.length == 0) {
            return false;
        }
        Signature verifier = Signature.getInstance(ALGORITHM);
        verifier.initVerify(publicKey);
        verifier.update(data);
        return verifier.verify(signature);
    }

    /**
     * Verifies that an envelope was produced by the private key behind
     * {@code expectedFingerprint} and that its signed content is unaltered.
     *
     * <p>The fingerprint comparison is what binds a signature to a claimed
     * origin. Without it any observer could replay a captured envelope after
     * rewriting {@code MessageEnvelope#originDeviceId()}.
     *
     * @param trustedKeyHolder the known key holder, or {@code null} when this
     *     device has never met the origin
     * @return true only when the signature is valid for the pinned fingerprint
     */
    public static boolean verifyEnvelope(PublicKeyHolder trustedKeyHolder,
                                         MessageEnvelope envelope)
            throws GeneralSecurityException {
        if (envelope == null || !envelope.signed()) {
            return false;
        }
        if (!ALGORITHM.equals(envelope.signatureAlgorithm())) {
            return false;
        }
        if (trustedKeyHolder == null) {
            return false;
        }
        if (!trustedKeyHolder.keyFingerprint().equals(envelope.originKeyFingerprint())) {
            return false;
        }
        if (!trustedKeyHolder.deviceId().equals(envelope.originDeviceId())) {
            return false;
        }
        return verify(trustedKeyHolder.publicKey(), envelope.signaturePayload(), envelope.signature());
    }
}