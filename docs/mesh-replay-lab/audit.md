# Mesh Replay Lab — simulator report audit

Status: audited against `packages/mesh-simulator` on `feat/mesh-replay-lab`.

This document records what the ResQNet simulator actually records, what a
browser can honestly reconstruct from a report, and what it cannot. The Replay
Lab is built against this document: every limitation listed here is surfaced in
the UI rather than papered over.

---

## 1. Where the data comes from

```
MeshSimulation
  ├── SimulatedNode (one per handset)
  │     └── MeshRouter  ← the real routing engine, unmodified
  │           └── MeshEvent  ← the real event stream
  ├── SimulationNetwork  ← synthetic radio (frames, loss, link churn)
  └── SimulationReport   ← JSON projection of everything above
```

The simulator contains **no routing logic of its own**. Every accept, forward,
duplicate suppression, rejection and acknowledgement in a report was produced by
the same `MeshRouter` class that runs on a handset. The radio is synthetic; the
protocol is real.

A run is deterministic: manual clock, seeded `Random`, FIFO frame delivery, and
a message schedule derived only from the seed. The same
`SimulationConfig` produces the same event stream on any machine.

## 2. The report schema as it stood before this feature

`SimulationReport#toJson()` emits:

| Member | Type | Notes |
| --- | --- | --- |
| `simulation` | object | `topology`, `nodeCount`, `linkCount`, `seed`, `messageCount`, `rounds`, `stepMillis`, `hopLimit`, `linkDropProbability`, `broadcastRatio`, `churnEveryRounds` |
| `outcome` | object | `originated`, `directedOriginated`, `broadcastOriginated`, `acknowledged`, `deliveredRecords`, `expired`, `failed`, `stillQueued`, `duplicatesSuppressed`, `rejected`, `forwards` |
| `deliveryRate` | number | fraction of **directed** messages confirmed |
| `amplificationFactor` | number | forwarded copies per originated message |
| `latencyMillis` | object | `median`, `p95`, `p99`, `max` — `-1` means *not measured* |
| `hopDistribution` | object | `{ "1": 12, "2": 30 }` |
| `radio` | object | `framesSent`, `framesDelivered`, `framesDropped`, `linkChanges` (count), `partitions` |
| `timing` | object | `simulatedMillis`, `wallClockMillis`, `throughputPerSecond` |
| `nodes` | array | per node: `index`, `label`, `deviceId`, `keyFingerprint`, `originated`, `accepted`, `forwarded`, `delivered`, `duplicatesSuppressed`, `rejected`, `untrustedDrops`, `rateLimitDrops`, `hopLimitDrops`, `stillQueued`, `medianLatencyMillis`, `p95LatencyMillis`, `degreeAtEnd`, `stateCounts` |
| `events` | array | see below |

Each event is:

```json
{
  "atEpochMillis": 1760000000250,
  "round": 3,
  "node": "n4",
  "kind": "forwarded",
  "messageId": "9834712",
  "peers": [],
  "hopCount": 2,
  "reason": null,
  "detail": "relayed to 3 peer(s)",
  "simulated": true
}
```

Event `kind` values, and what the router actually recorded for each:

| `kind` | Source | What is factually known |
| --- | --- | --- |
| `accepted` | `MeshEvent.Accepted` | this relay accepted the envelope; `detail` names the **peer it arrived from**; `hopCount` is the hop budget used |
| `forwarded` | `MeshEvent.Forwarded` | this relay handed the message to N neighbours; N is in `detail`, the identities were not |
| `delivered` | `MeshEvent.Delivered` | the message reached its destination inbox |
| `acknowledged` | `MeshEvent.AcknowledgementReceived` | an acknowledgement for this message arrived; `detail` names who confirmed |
| `acknowledgement-sent` | `MeshEvent.AcknowledgementSent` | this node produced an acknowledgement; `detail` names who it went to |
| `peer-connected` / `peer-disconnected` | `MeshEvent.PeerConnectionChanged` | a neighbour appeared or disappeared |
| `rejected` | `MeshEvent.Rejected` | refused; `reason` is the protocol reason; `detail` names the delivering peer and the claimed origin |
| `state` | `MeshEvent.DeliveryStateChanged` | a stored message moved between delivery states, e.g. `QUEUED -> RELAYED` |
| `triage` | `MeshEvent.TriageSuggestion` | the offline classifier suggested a category |
| `backpressure` | `MeshEvent.BackpressureApplied` | a relay had no queue space |
| `other` | anything else | preserved verbatim by class name, not interpreted |

## 3. What could genuinely be reconstructed — and what could not

### 3.1 Could be reconstructed from the original report

* **The event stream itself.** Every accept, forward, reject, delivery and
  acknowledgement is present with its simulated timestamp, round and node.
* **Which node did what.** `event.node` is a stable `n<index>` label.
* **Per-node counters and delivery-state counts**, for the node table.
* **The aggregate performance numbers** in `outcome`, `latencyMillis`, `radio`.
* **The configured scenario** in `simulation`, sufficient to reproduce the run.

### 3.2 Could **not** be reconstructed — and why

| Missing | Consequence for a viewer |
| --- | --- |
| **No node-to-node links.** The report carried only `linkCount` and a per-node `degreeAtEnd`. Any picture of who is connected to whom would have to be invented. | A topology view would be a drawing, not evidence. |
| **No link change records.** `SimulationNetwork` recorded every cut and restore internally and the report kept only the *count*. | "Link failure and recovery" could not be shown, only counted. |
| **`peers` was always empty.** `SimulationEvent.peerIndexes` was never populated, so a `forwarded` event said "relayed to 3 peer(s)" without saying to whom. | The actual hop sequence could not be followed; the path would have to be guessed. |
| **No per-message origin or destination.** Nothing recorded which node composed a message or which node it was addressed to. | A message journey could not be shown at all, and directed messages could not be separated from broadcasts per message. |
| **Events are grouped per node, not ordered by time.** `MeshSimulation#buildReport` concatenates `node.events()` node by node. The array is *not* a chronological timeline. | Replaying it as-is would show a scrambled history. A deterministic total order is required. |
| **The retained event window is silent.** Events are truncated to the last 4 000 without saying so. | A viewer could not tell that the beginning of a run is missing. |
| **No wall-clock provenance.** `simulatedMillis` is scenario time; there is no timestamp for when the run happened. | Reports cannot be ordered by date. |

### 3.3 The deliberate non-goals

* The report does **not** contain radio-level truth: no RSSI, no airtime, no
  spectrum use. A `dropped` frame is a frame the synthetic radio threw away.
* The report does **not** contain a guarantee of a unique path. A mesh floods;
  several copies can travel in parallel and one usually wins. The recorded
  stream shows what happened, not the only path that could have happened.
* Nothing in a report is evidence about physical Android handsets.

## 4. Additive report changes made for the Replay Lab

All changes are **additive**: no existing member changed name, type or
meaning, so every current consumer — the backend ingestion service, the command
centre, the gateway publisher — keeps working unchanged.

| Addition | Where | Why it was needed |
| --- | --- | --- |
| `simulation.edges` | `SimulationReport` | The initial topology as `[[from, to], ...]`. Makes the graph *recorded* rather than drawn. |
| `simulation.degree` | `SimulationReport` | The target degree the run was configured with, so a mesh/random report can be reproduced exactly. |
| `simulation.messageLifetimeSeconds` | `SimulationReport` | The queue lifetime, otherwise absent from the report and therefore unreproducible. |
| `simulation.startEpochSeconds` | `SimulationReport` | The manual clock's start, which fixes every `atEpochMillis` in the run. |
| `simulation.startPartitioned` | `SimulationReport` | Whether the run started with the busiest relay offline. Without it a partitioned report could not be reproduced or compared honestly. |
| `linkChanges` | `SimulationReport` | Every recorded cut and restore: `{ atEpochMillis, round, from, to, up }`. `radio.linkChanges` remains the count. |
| `messages` | `SimulationReport` | Per originated message: `{ id, origin, destination, broadcast, originatedAt }`. Node indices, not identities. |
| `eventWindow` | `SimulationReport` | `{ retained, total, truncated }` so a truncated history can say so. |
| `peers` populated | `SimulationEvent` | Node indices of the peers an event actually involved. The field already existed and was always `[]`. |

The node indices are used rather than device identities because the simulator
generates a fresh key pair per node per run: identities differ between runs,
indices do not. `SimulationReport#volatileJsonFields()` still lists the identity
fields as volatile, and nothing new was added to it — every added field is
reproducible.

One serialization detail matters to any other consumer: `SimulationEvent`
writes peer indices as JSON **strings** (`"peers": ["1", "5"]`), while every
other index in the report is a number. The Lab's parser accepts both. A future
change to numeric peers would be additive for the Lab but not for consumers that
already parse the strings, so it has not been made.

### 4.1 What is still not reconstructable after the change

* **The unique path of a message.** The stream records that node A forwarded to
  peers B, C and D, and that B later accepted a frame from A. That is a
  recorded relationship, and the Lab draws it as such. It is *not* a claim
  that B was the only route.
* **Which physical radio carried a frame.** There is none in the simulation.
* **Anything about the radio environment.** Loss is a configured probability.
* **Wall-clock provenance.** Still absent; the Lab says so rather than
  inventing a timestamp.

## 5. The chronological-order problem, and how the Lab solves it

`collectedEvents` is concatenated node by node, so two events recorded in the
same millisecond on different nodes appear in node order, not time order.

The Lab therefore applies its own **deterministic total order** to every
report before replaying it:

1. `atEpochMillis` ascending,
2. then `round` ascending,
3. then node index ascending,
4. then event kind (alphabetical),
5. then `messageId`,
6. then the event detail,
7. then the original array position as a final tie-break.

The last rule makes the order total even for two identical events, so the same
report always replays identically. Timestamps are never altered and no
intermediate hops are invented — the ordering only decides *when* a recorded
event is shown.
