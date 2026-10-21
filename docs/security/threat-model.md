# Threat Model

ResQNet is safety-adjacent software. A design flaw here does not merely leak
data; it can misdirect a rescue. This document states what the system defends
against, what it deliberately does not, and where the residual risk sits.

Scope: the mesh protocol and routing engine (`packages/mesh-core`), the Android
transport adapter, and the coordination gateway.

---

## 1. Assets

Ordered by how much damage their compromise would cause.

| # | Asset | Why it matters |
| --- | --- | --- |
| A1 | Integrity of an SOS | A forged or altered rescue request sends scarce responders to the wrong place |
| A2 | Authenticity of a device identity | Without it, any phone can claim to be a verified responder |
| A3 | Availability of the mesh | A dead battery during a flood is a death |
| A4 | Integrity of responder actions | An audit trail that can be rewritten is worthless |
| A5 | Confidentiality of directed messages and location | Being located, or having a private update read, can endanger people, especially survivors |
| A6 | Availability of the coordination backend | Affects situational awareness, not the emergency network |

Directed SOS and text messages are end-to-end encrypted between the sender and
the verified recipient (measured overhead:
[docs/benchmarks/e2ee.md](../benchmarks/e2ee.md)). Public SOS broadcasts remain deliberately unencrypted:
they are addressed to everyone in range and must stay usable by people who have
not exchanged keys. The two paths are distinct message types, and the client
never silently falls back from an encrypted send to a public one.

## 2. Adversaries

### D1 — Opportunistic eavesdropper within radio range
Has a phone, no special access, can see every frame the mesh carries. Can read
public broadcasts and all envelope metadata (origin, destination, timestamps,
type, size), but not the plaintext of a correctly encrypted directed message. May
capture and replay bytes.

### D2 — Malicious participant
Has joined the mesh legitimately, has been given a key, and then abuses the
protocol: floods, replays, or forwards selectively to make a victim believe a
message was delivered when it was not.

### D3 — Impersonator
Wants to send messages as a verified responder or as a specific pers# Threat Model

ResQNet is safety-adjacent software. A design flaw here does not merely leak
data; it can misdirect a rescue. This document states what the system defends
against, what it deliberately does not, and where the residual risk sits.

Scope: the mesh protocol and routing engine (`packages/mesh-core`), the Android
transport adapter, and the coordination gateway.

---

## 1. Assets

Ordered by how much damage their compromise would cause.

| # | Asset | Why it matters |
| --- | --- | --- |
| A1 | Integrity of an SOS | A forged or altered rescue request sends scarce responders to the wrong place |
| A2 | Authenticity of a device identity | Without it, any phone can claim to be a verified responder |
| A3 | Availability of the mesh | A dead battery during a flood is a death |
| A4 | Integrity of responder actions | An audit trail that can be rewritten is worthless |
| A5 | Confidentiality of directed messages and location | Being located, or having a private update read, can endanger people, especially survivors |
| A6 | Availability of the coordination backend | Affects situational awareness, not the emergency network |

Directed SOS and text messages are end-to-end encrypted between the sender and
the verified recipient (measured overhead:
[docs/benchmarks/e2ee.md](../benchmarks/e2ee.md)). Public SOS broadcasts remain deliberately unencrypted:
they are addressed to everyone in range and must stay usable by people who have
not exchanged keys. The two paths are distinct message types, and the client
never silently falls back from an encrypted send to a public one.

## 2. Adversaries

### D1 — Opportunistic eavesdropper within radio range
Has a phone, no special access, can see every frame the mesh carries. Can read
public broadcasts and all envelope metadata (origin, destination, timestamps,
type, size), but not the plaintext of a correctly encrypted directed message. May
capture and replay bytes.

### D2 — Malicious participant
Has joined the mesh legitimately, has been given a key, and then abuses the
protocol: floods, replays, or forwards selectively to make a victim believe a
message was delivered when it was not.

### D3 — Impersonator
Wants to send messages as a verified responder or as a specific person, without
that person's key.

### D4 — Compromised device
Has extracted a private key from a lost or seized handset.

### D5 — Network attacker
Operates the Wi-Fi access point, or the cell tower, in the affected area.

### D6 — Insiders at the coordination backend
Has database access or backend credentials.

## 3. Trust boundaries

```mermaid
graph TB
    subgraph untrusted["Untrusted"]
        R["Any radio frame<br/>from any peer"]
    end
    subgraph device["This device"]
        V["Validation pipeline<br/>decryptless verification"]
        T["TrustStore<br/>user-confirmed keys"]
        E["HPKE<br/>directed content"]
        S["MessageStore<br/>ciphertext queue"]
        K["Keystore<br/>signing key + sealed HPKE keyset"]
    end
    subgraph peer["Semi-trusted peers"]
        P1["Unverified key holder"]
        P2["Verified key holder"]
    end
    subgraph server["Server trust boundary"]
        GW["Gateway client"]
        ADM["Human responder admin"]
    end

    R -->|"every frame crosses here"| V
    V --> T
    V --> S
    K -->|"signing key never leaves"| V
    E -->|"decrypts only for this device"| V
    V -->|"local plaintext cache"| E
    P1 -.->|"cannot sign as anyone"| V
    P2 -.->|"authenticated, not authorised"| V
    GW -->|"role decided server-side"| ADM
```

The boundary between **authenticated** and **authorised** is load-bearing. A
message with a valid signature proves *who sent it*. It never proves that they
should have been permitted to send it, and no client-supplied field is ever
treated as an authorisation claim.

## 4. Threats and mitigations

### T1 — Forged SOS (A1, A2) · D3
*An attacker crafts a message that appears to come from a verified responder.*

**Mitigation.** Every envelope carries a signature over a canonical byte string
that includes the origin device id, the origin key fingerprint, the destination,
the timestamps, and the payload. Verification binds the signature to the pinned
fingerprint and to the claimed device id. Rewriting the origin field invalidates
the signature.

**Tests.** `SecurityTest.dropsOriginSubstitution`,
`SecurityTest.dropsTamperedPayload`.

### T2 — Replay of a captured frame (A1) · D1
*A captured genuine SOS is re-injected later, generating a duplicate alert.*

**Mitigation.** A bounded duplicate-suppression window keyed by message id, run
*after* authentication so it cannot be poisoned by unauthenticated input. The
window expires with the message lifetime, so a replay of an expired message is
refused on the expiry check first.

**Tests.** `SecurityTest.dropsReplay`, `DuplicateCacheTest`.

### T3 — Amplification / battery exhaustion (A3) · D2
*A single node floods every device in range.*

**Mitigation, four layers.** Hop limits cap total distance. Deduplication caps
per-node forwarding to once per message. A fan-out cap limits each node's radio
transmissions per message. A per-origin token bucket limits sustained inbound
forwarding work.

Life-safety traffic is exempt from the token bucket by deliberate choice;
suppressing a real SOS is worse than extra work, and the flood is still bounded
by the other three layers.

**Tests.** `SecurityTest.throttlesFlooding`, `SecurityTest.doesNotThrottleLifeSafety`.

### T4 — Queue exhaustion (A3) · D2
*The queue fills and legitimate SOS traffic is evicted.*

**Mitigation.** Hard capacity bound with priority-ordered eviction. SOS records
are never evicted; the oldest low-priority record goes first.

**Tests.** `StoreAndForwardTest.enforcesCapacityProtectingSos`,
`InMemoryMessageStoreTest.neverEvictsSos`.

### T5 — Oversized or malformed frames (A3, A6) · D1, D2
*A frame declares an enormous length to exhaust memory.*

**Mitigation.** Declared lengths are checked against the remaining buffer before
any allocation. Payload and envelope ceilings are enforced in both directions.
The decoder is fuzzed in tests by corrupting every four-byte window.

**Tests.** `EnvelopeCodecTest.rejectsHostileLengthPrefix`,
`EnvelopeCodecTest.rejectsTruncation`.

### T6 — Spoofed responder role (A2, A4) · D2, D6
*A gateway client claims responder privileges for itself.*

**Mitigation.** No client can assign itself a role. The backend issues
credentials to authenticated human accounts; a gateway is authenticated by its
key and is scoped to *synchronise*, never to *authorise*. Role checks happen
server-side against the account record, never against request payloads.

### T7 — False delivery confirmation (A1) · D2
*An attacker relays only acknowledgements so the sender believes a message
arrived when it did not.*

**Mitigation.** Acknowledgements are themselves signed envelopes addressed to
the specific origin. A relay cannot forge one: the fingerprint is pinned to the
destination's key and the signature must verify. A message is only ever shown as
`ACKNOWLEDGED` when a signature from the intended recipient verifies.

**Tests.** `AcknowledgementTest.reachesAcknowledged`,
`AcknowledgementTest.relayDoesNotClaimDelivery`.

### T8 — Stolen device keys (A2) · D4
*A lost handset yields a usable identity or decryption capability.*

**Mitigation.** The signing key lives in the Android Keystore where the hardware
permits, bound to the device and non-exportable on many devices. The X25519 HPKE
private keyset cannot be held in the platform Keystore, so it is sealed with an
AES-256-GCM Keystore wrapping key before it is written to app-private
preferences; an unseal failure is fatal and never regenerates silently. While
the app is running the unwrapped HPKE keys exist in process memory. Users can
revoke a peer, and a revoked key is refused everywhere the trust store is
loaded. Key theft from a physical device is **not** fully mitigated — see
Residual risks.

### T9 — Location and directed-content exposure (A5) · D1, D5
*Precise location of a survivor leaks to the network or an unknown peer.*

**Mitigation, directed messages.** Location is never transmitted implicitly. It
is user-controlled, per message, coarse by default, and omitted entirely unless
the sender attaches it. For a directed message the payload — body and optional
location — is encrypted with HPKE to the verified recipient's X25519 key and
authenticated with AES-256-GCM. Relays carry an opaque `SOS_PROTECTED` or
`TEXT_PROTECTED` payload and cannot decrypt it. The envelope header remains
visible, so relays learn who is talking to whom, when, and roughly how much.

**Risk accepted, public broadcasts.** `SOS` and `TEXT` without a destination are
the public path. They are not encrypted, and no UI string claims otherwise. A
user who needs privacy must select a verified device; if no verified peer with
an encryption key exists, the composer refuses rather than falling back.

### T10 — Backend compromise (A6, A4) · D6
*An insider rewrites responder audit records.*

**Mitigation.** Audit events are append-only, written with the acting account,
and exported to a separate sink. A responder cannot edit or delete an audit
record; only an administrative export is possible.

### T11 — Untrusted identity announcement (A2) · D3
*An attacker announces a key for a known peer's identity.*

**Mitigation.** Announcements carry no authority. They produce a prompt in the
owner's UI, showing an eight-character code. Only a human comparison populates
the trust store. Because the device id is a hash of the signing key, an
announcement for a known id necessarily carries that exact signing key, so
signing-key substitution is structurally impossible.

**Tests.** `SecurityTest.unsignedIdentityClaimGrantsNothing`,
`SecurityTest.deviceIdIsBoundToItsKey`.

### T12 — Nearby peer identity rebinding (A2, A3) · D1, D3
*An attacker replays a known device's public identity announcement on a new
Nearby endpoint to capture messages addressed to that device.*

**Mitigation.** An announcement only records an unverified key. Each connected
endpoint stays provisional until it answers a fresh 256-bit random challenge
with a signature from the claimed identity's private key. The signed bytes
include a protocol domain, challenger and claimant identities, and the nonce.
The verifier accepts the answer only on the endpoint where it issued that
challenge, within 15 seconds, once, while that connection remains active.
Disconnect, stop, failed verification, and timeout discard pending proof.
While the same provisional connection remains active, a failed or unanswered
challenge is retried after 15 seconds with a new nonce; a failed connection
request is retried while discovery still reports that endpoint.
A blocked identity cannot be promoted. A successful proof identifies a key
holder; it does not change the user's UNVERIFIED or VERIFIED trust decision.
Blocking an already connected identity immediately removes its stable directed
route and leaves the radio handle provisional.
Identity announcements and mesh envelopes still route while a link is
provisional.

**Tests.** `LinkIdentityProofTest` covers possession, replay, wrong signatures,
endpoint swaps, timeout and interruption. `IdentityRebindingTest` checks that
an announcement alone cannot bind a known identity. `PeerBindingsTest` checks
replacement and removal of stale endpoint mappings. `NearbyProofFlowTest`
exercises announcement, signed proof, directed delivery, reconnect, retry,
blocking and concurrent disconnect through the engine and transport on an
emulator with an in-process byte pipe. These tests do not exercise physical
radio delivery.

### T13 — HPKE key substitution (A5) · D1, D3
*An attacker captures an identity announcement and appends their own encryption
key, hoping the sender will encrypt to the attacker.*

**Mitigation.** The HPKE key travels with an ECDSA proof made by the announced
signing key over a domain-separated, length-prefixed encoding of both public
keys (`ResQNet-EncryptionKeyBinding-v1`). The client verifies that proof before
storing the key. A changed HPKE key for an identity that already has one is a
hard refusal, not a silent rotation. Protected sends require the recipient to be
`VERIFIED`, so an unverified announcement cannot be an encryption target.

**Tests.** `EncryptionKeyBindingTest`, `TrustStoreEncryptionKeyTest`,
`MeshE2eeTest.refusesUnverifiedRecipient`,
`TrustStoreEncryptionKeyTest.refusesKeyRotation`.

### T14 — Ciphertext tampering or envelope rebinding (A1, A5) · D2
*A relay rewrites the destination or type, or flips ciphertext bytes, to change
where or how a message is interpreted.*

**Mitigation.** The HPKE associated data covers the canonical signed header with
an empty payload, the protected-payload version and suite, the recipient key id,
and explicit lengths. The envelope signature still covers the final header and
ciphertext. A relay can decrement the hop counters — as it must — but any
content-affecting edit fails either the signature or the AEAD tag. The client
never displays unauthenticated ciphertext.

**Tests.** `ProtectedPayloadTest.associatedDataBindsHeader`,
`MeshE2eeTest.editedHeaderFailsAuthentication`.

### T15 — Local plaintext at rest (A5) · D4
*Someone with the database file reads a message the sender wrote.*

**Mitigation and explicit boundary.** Incoming directed messages are stored as
ciphertext and decrypted in memory for display. The HPKE private keyset is
sealed with a Keystore AES-GCM key. However, the sender's own outgoing directed
plaintext is retained in an app-private SQLite `local_body` column, unencrypted,
so the sender can read their sent history. This is not full local-storage
encryption. An attacker with app-private storage access, a rooted device, or an
unlocked handset can read those sent bodies. The boundary is documented rather
than hidden.

## 5. Residual risks

Stated plainly, because a threat model that claims completeness is not a threat
model.

| Risk | Why it remains | Mitigation in practice |
| --- | --- | --- |
| R1 — Physical key extraction | A rooted device or an unlocked bootloader can defeat the Keystore | Revocation; short message lifetimes limit the window |
| R2 — Public traffic is readable | Broadcast SOS/text is intentionally unencrypted and can be read by anyone in range | The UI labels it public and keeps encrypted directed sends separate; there is no silent downgrade |
| R2a — Metadata is visible | Relays must route on origin, destination, type, timestamps and size | Accepted; the mesh does not claim traffic-analysis resistance |
| R2b — Sent plaintext is stored locally | The sender's own directed bodies are kept in app-private SQLite so sent history remains readable | Documented explicitly; not full local-storage encryption. Incoming ciphertext is decrypted in memory only |
| R2c — No HPKE key rotation | A changed HPKE key is refused, so a reinstalled peer cannot be encrypted to until its identity is re-established | Deliberate v1 trade-off; rotation needs a user-visible protocol |
| R2d — HPKE key in process memory | Android Keystore cannot perform X25519 HPKE | The keyset is sealed at rest; runtime extraction requires code execution inside the app |
| R3 — Global eclipse attack | A radio-level attacker could jam discovery | Requires spectrum access beyond the threat scope of a single app |
| R4 — First-contact trust is unavailable | Devices that have never met cannot authenticate each other, so traffic between them is refused | Deliberate. Identification is an in-person act; this is the honest trade |
| R5 — Classification errors | The offline classifier can be wrong | Confidence is reported, results are advisory, manual override is always available |
| R6 — Background execution limits | Android can suspend or kill the app | Documented; store-and-forward recovers on relaunch |
| R7 — Live relay of a proof exchange | An attacker with simultaneous access to the real key holder can forward a live challenge and answer; the protocol does not establish physical proximity or prevent radio relay attacks | Treat a proof as key possession at the end of the byte pipe, not as distance evidence; validate the link on real devices and retain human fingerprint verification |

## 6. Reporting

See `SECURITY.md` at the repository root.

## 7. Non-goals

- **Not a replacement for official emergency services.** Call your local
  emergency number. ResQNet is a prototype communications aid.
- **Not certified for any safety standard.** No IEC 61508, ISO 26262 or medical
  certification is claimed or implied.
- **Not an anonymity system.** Traffic analysis defeats any claim in that
  direction, and none is made.
- **Not full local-storage encryption.** Outgoing directed bodies are retained
  in plaintext inside app-private SQLite for the sender's own history.
