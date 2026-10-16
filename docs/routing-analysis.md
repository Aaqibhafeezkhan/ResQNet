# Why reported delivery falls as the mesh grows

A short investigation, because the answer turned out to be "the headline number
was wrong, then the graph is the limit" rather than "the router loses messages",
and both of those are worth writing down before someone else re-derives them.

All figures are simulation results from a modelled radio with a manual clock.
Nothing here is evidence about real radios.

---

## The question

Reported delivery by mesh size, mesh topology, directed and broadcast traffic
mixed:

| Nodes | 4 hops | 8 hops | 16 hops | 32 hops |
|---:|---:|---:|---:|---:|
| 10 | 90.0% | 90.0% | 90.0% | 90.0% |
| 100 | 24.0% | 44.0% | 76.0% | 88.0% |
| 500 | 2.4% | 4.0% | 10.4% | 21.6% |

The obvious reading is that the router degrades under load. It does not.

## Step 1: the metric was wrong before the router was even involved

A broadcast has no destination. `MeshRouter.buildAcknowledgement` returns
empty for one, because there is nobody to acknowledge it to:

```java
if (received == null || received.type() == MessageType.ACK
        || received.destinationDeviceId().isEmpty()) {
    return Optional.empty();
}
```

The reported rate divided acknowledged messages by **all** originated
messages, so with the default 25% broadcast ratio it was mathematically capped
below 100% however well the mesh performed. Measured directly: a run with
`--broadcast 1.0` reports 0.0% delivery, always.

Fixed in `cb56ede`. The rate is now acknowledged over *directed* messages, and
broadcasts are reported separately rather than divided away.

**This alone accounted for a large part of the apparent decline.** The table
above predates the fix.

## Step 2: the router is close to what the graph permits

With the metric fixed, and directed-only traffic, five seeds at 100 nodes:

| Hop budget | Delivered | Pairs the graph puts within reach | Share of ceiling |
|---:|---:|---:|---:|
| 4 | 16.0% | 16.2% | 99% |
| 8 | 32.0% | 32.3% | 99% |
| 16 | 63.0% | 64.6% | 98% |
| 32 | 100.0% | 100.0% | 100% |

The router is not losing messages that the graph says are reachable. This is
asserted in `RoutingEfficiencyTest`, so a future regression fails the build
rather than quietly reappearing as a benchmark number.

### One subtlety worth knowing

The ceiling is computed against `hopLimit - 1`, not `hopLimit`. A message that
arrives at a node with its budget already exhausted is rejected there, even
when that node is the destination:

```java
if (forSelf && envelope.hopExhausted() && envelope.destinationDeviceId().isPresent()) {
    reject(now, envelope, RejectReason.HOP_LIMIT_EXCEEDED, "arrived with no hops left", ...);
}
```

So **a hop budget of H reaches at most H-1 hops**. Computing the ceiling against
H makes the router look about 15% worse than it is, and it made an earlier
version of this test fail for entirely the wrong reason.

## Step 3: the graph is the limit, and `MESH` is not a mesh

`TopologyDiagnostics` measures the graphs directly:

| Topology | Nodes | Min deg | Max deg | Diameter | Within 4 | Within 8 | Within 32 |
|---|---:|---:|---:|---:|---:|---:|---:|
| mesh | 10 | 4 | 4 | 3 | 100.0% | 100.0% | 100.0% |
| mesh | 100 | 4 | 4 | 25 | 16.2% | 32.3% | 100.0% |
| mesh | 500 | 4 | 4 | **125** | 3.2% | 6.4% | **25.7%** |
| random | 100 | 3 | 15 | 5 | 99.8% | 100.0% | 100.0% |
| random | 500 | 3 | 21 | **6** | 88.6% | 100.0% | **100.0%** |

The `MESH` shape joins every node to its ±1 and ±2 neighbours on the node
index. That is a circulant band with diameter `n/4`. Its Javadoc claimed
"dense, many redundant paths", which is false at scale, and the name compounds
it.

The 21.6% measured at 500 nodes and 32 hops sits directly under the 25.7%
structural ceiling. The router was near the limit the graph imposes.

## Step 4: the control experiment

Same node count, same seeds, same hop budget, same router, different graph:

| Topology | 100 nodes, 8 hops, 3 seeds |
|---|---|
| mesh | 35.0%, 27.5%, 27.5% |
| random | 100.0%, 100.0%, 100.0% |

Nothing about the routing code changed. If the router were the constraint, the
two rows would be similar.

## Ruled out along the way

Each of these was tested rather than assumed:

- **Too little time.** 500 nodes at 32 hops, same seed: 60 rounds gives 21.6%,
  150 rounds 23.2%, 300 rounds 23.2%. Time buys 1.6 points and then stops.
- **Message expiry.** `expired` is 0 at every size tested. Lifetime is 900 s
  against a 15 s run.
- **Flood control.** `FloodControlLimiter.tryAcquire` returns true immediately
  for SOS traffic, and every simulator message is SOS. Not a factor.
- **Queue capacity.** 14,734 queued records across 500 nodes against a
  per-node cap of 256; average 29 per node.
- **Link loss.** `framesDropped` is 0 in every run. There is no drop model
  active by default.
- **Partitioning.** `partitions` is 1, i.e. connected. The decline is present
  in a fully connected graph.
- **Duplicate-cache eviction.** Only 125 distinct message ids against a 4,096
  entry cache.

## What is actually true

For a flooding router on this graph family:

1. **Hop budget dominates mesh size.** A flood spends one hop per relay. At 10
   nodes the budget is irrelevant because everything is one or two hops away.
   At 500 nodes in a band of diameter 125, even a 32-hop budget covers a
   quarter of the mesh.
2. **Connectivity matters more than size.** Diameter is the quantity that
   predicts delivery, and degree is what controls it. Two networks of 500 nodes
   can differ by 4x in achievable delivery.
3. **A partition costs more than ten times the node count.** Two-cluster at 10
   nodes acknowledges 42% where mesh at 10 acknowledges 79%.

## Why no routing change was made

The evidence says the router is at 98-100% of its achievable ceiling at sizes up
to 100 nodes and close to it at 500. Changing a working router to chase a
number that is capped by the graph would make real delivery worse to improve a
benchmark. The two changes that were made are both about reporting the truth:
the delivery-rate denominator, and the topology's own documentation.

## Reproducing

```bash
mvn -q -pl packages/mesh-core install -DskipTests
mvn -q -pl packages/mesh-simulator -am package -DskipTests
CP=packages/mesh-simulator/target/classes:packages/mesh-core/target/classes

# Graph properties: diameter and the share of pairs within each budget.
java -cp $CP dev.resqnet.mesh.simulator.diagnostics.TopologyDiagnostics

# Directed only, so broadcasts that cannot be acknowledged do not distort it.
java -cp $CP dev.resqnet.mesh.simulator.SimulatorCli \
  --topology mesh --nodes 100 --messages 400 --broadcast 0 \
  --churn 0 --seed 11 --rounds 60 --hops 8

# The router-against-ceiling assertion.
mvn -pl packages/mesh-simulator -am test -DexcludedTestGroups= \
  -Dtest=RoutingEfficiencyTest
```

Note that changing `--broadcast` or `--churn` changes how the seeded random
stream is consumed, so two runs differing in those flags are **not** comparable
even at the same seed. That is a trap worth naming: an early version of this
investigation compared a 12% directed-only run against a 50% mixed run as if
they meant something.