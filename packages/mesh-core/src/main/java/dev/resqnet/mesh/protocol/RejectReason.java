package dev.resqnet.mesh.protocol;

/** Why a message was refused. These values are part of the public wire API. */
public enum RejectReason {
    /** Envelope exceeded the hard size ceiling. */
    PAYLOAD_TOO_LARGE,
    /** Structural problem: missing identifiers, impossible timestamps. */
    MALFORMED,
    /** Protocol major version is not implemented here. */
    UNSUPPORTED_VERSION,
    /** Wire type id does not map to a known message type. */
    UNKNOWN_MESSAGE_TYPE,
    /** Expiry already in the past when received. */
    EXPIRED,
    /** Hop budget exhausted before reaching the destination. */
    HOP_LIMIT_EXCEEDED,
    /** Signature missing or cryptographically invalid. */
    INVALID_SIGNATURE,
    /** Signature key fingerprint does not match the claimed origin. */
    UNTRUSTED_ORIGIN,
    /** Already processed: duplicate suppression. */
    DUPLICATE,
    /** Rate limiting kicked in to bound flooding amplification. */
    RATE_LIMITED,
    /** Local resource bound reached (queue, cache, store). */
    RESOURCE_EXHAUSTED,
    /** Origin identity is on the local block list. */
    ORIGIN_BLOCKED,
    /** A protected send has no verified recipient key to encrypt to. */
    ENCRYPTION_UNAVAILABLE,
    /** Authenticated decryption failed; content must not be displayed. */
    DECRYPTION_FAILED
}