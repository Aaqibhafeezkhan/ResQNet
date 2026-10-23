# Mesh Replay Lab

A public page at **`/lab`** for replaying a report written by the ResQNet Java
mesh simulator. It is a reading tool: it shows what a real deterministic run
recorded, at the resolution the run recorded it, and refuses to fill in numbers
the run did not measure.

> **SIMULATED — NOT REAL RADIO TELEMETRY.** Every topology, event, handoff and
> metric in the Lab comes from a synthetic radio driven by the ResQNet
> `MeshRouter`. Nothing on the page is a measurement of a physical radio, an
> Android handset or a field deployment. The banner is present on every
> simulation view and in the exported comparison.

The prototype rationale is in [audit.md](./audit.md). This document is the
usage guide.

---

## What it can and cannot tell you

| The Lab shows | The Lab does not claim |
| --- | --- |
| The recorded topology and every cut/restore | That a physical radio link existed |
| Every retained protocol event, in a deterministic total order | That the event order is the timestamp order inside the Java process |
| Handoffs (`forwarded`, `accepted`) for a message | One unique route, or that a handoff arrived |
| Acknowledged vs delivered vs still queued | That a broadcast was acknowledged (broadcasts never are) |
| The metrics the report measured | A value for a measurement the run did not take |
| Two reports side by side, with their full configurations | That a comparison is controlled when more than one parameter changed |

A `forwarded` event means a node handed a frame to its radio. A `delivered`
event means the destination's inbox received it. Only `acknowledged` means the
recipient confirmed it. The message inspector keeps those three distinct.

### Unavailable is not zero

The simulator writes `-1` when it could not measure a value. The parser turns
that (and a missing member) into `null`, and every view renders it as an em
dash with a reason. Unavailable values are excluded from comparison deltas; a
missing latency can never look like an improvement. A genuine `0` is still
rendered as `0`.

---

## Workflow

1. **Build a command.** The scenario builder on the left validates the
   parameters and writes the Java command. It does **not** run anything in the
   browser. Copy the command and run it from the repository root:

   ```bash
   mvn -q -pl packages/mesh-simulator exec:java \
     -Dexec.args="--topology ring --nodes 8 --degree 3 --messages 24 --seed 42 \
     --drop 0 --churn 12 --hops 8 --rounds 60 --step 250 --broadcast 0.25 \
     --out report.json"
   ```

   `--partitioned` takes the busiest relay offline before the first round.
   `--topology` accepts `line`, `ring`, `star`, `mesh`, `two-cluster` and
   `random`.

2. **Import the report.** Drop `report.json` on the loader or choose it with
   the file picker. Files are read locally with the browser's File API, parsed
   in the tab and never uploaded. The import limit is 16 MB and 200 000 events;
   a malformed, empty or oversized file is rejected with a message that says
   which limit or field failed.

   Two reports produced by the simulator are committed and load instantly:
   - **Ring** — 6 nodes, 10 messages, seed 42, two links cut and one of them
     restored, every directed message acknowledged (the cut link is down at the
     end of the run).
   - **Two clusters** — the same schedule with `--partitioned`: 5 partitions at
     the end and only 2 of 6 directed messages acknowledged.

3. **Replay.** Play, pause, step or scrub. The cursor is an index into the
   deterministic event order. Scrubbing pauses playback and updates the link
   states, the event stream and the message journey to the same moment. The
   reported totals panel always describes the whole run and says so.

4. **Inspect a message.** Pick one from the selector, or select a message id in
   the event stream. Steps are limited to events that have happened at the
   cursor, so no acknowledgement is revealed early. "Jump" moves the replay to
   the recorded event. The limitations block under every journey names what the
   view cannot say — most importantly that a flood is not a single route.

5. **Inspect a node.** Select a node chip under the map. The details panel shows
   the node's recorded counters (whole run) and its active link count at the
   cursor. The SVG itself is an image with a text node list as its accessible
   alternative; selecting a node never depends on hitting a small target.

6. **Compare two runs.** Open the **Compare** tab, put a report in each slot,
   and read the configuration first. An amber verdict means the runs differ in
   one or more dimensions, so outcome differences cannot be attributed to one
   change. "Copy summary" and "Export .md" produce the same Markdown, including
   the simulation boundary and the reconstructed commands for both runs.

---

## Built-in samples and regenerating them

The committed fixtures live in
[`apps/web/src/lab/fixtures/`](../../apps/web/src/lab/fixtures/). They are real
simulator output — not hand-written JSON. To regenerate them from the
repository root:

```bash
# Ring sample
mvn -q -pl packages/mesh-simulator exec:java \
  -Dexec.args="--topology ring --nodes 6 --degree 3 --messages 10 --seed 42 \
  --drop 0.05 --churn 8 --hops 8 --rounds 30 --step 250 --broadcast 0.4 \
  --out apps/web/src/lab/fixtures/ring-6-seed-42.json"

# Partitioned sample
mvn -q -pl packages/mesh-simulator exec:java \
  -Dexec.args="--topology two-cluster --nodes 6 --degree 3 --messages 10 --seed 42 \
  --drop 0.05 --churn 8 --hops 8 --rounds 30 --step 250 --broadcast 0.4 \
  --partitioned --out apps/web/src/lab/fixtures/two-cluster-6-seed-42.json"
```

Runs are deterministic: the same command produces the same routing decisions
and the same event stream. Node key pairs are regenerated per run and are
deliberately treated as volatile. See
[SimulationReport#volatileJsonFields()](../../packages/mesh-simulator/src/main/java/dev/resqnet/mesh/simulator/SimulationReport.java)
and the audit for the exact contract.

### Report fields the Lab relies on

The Lab reads the standard report plus additive fields introduced for this
feature: `simulation.edges`, `simulation.degree`,
`simulation.messageLifetimeSeconds`, `simulation.startEpochSeconds`,
`simulation.startPartitioned`, `linkChanges`, `messages`, `eventWindow`, and
populated `peers` on events. Older reports still load: missing additions
produce an import note and the affected view says what it cannot show. The
audit documents every addition and why it was needed.

Peer indices are serialized as JSON **strings** (`["1", "5"]`); the parser
accepts both strings and numbers.

---

## Metrics, exactly as defined

| Metric | Definition |
| --- | --- |
| Acknowledgement rate | `acknowledged` directed messages ÷ directed messages originated. Broadcasts cannot be acknowledged and are excluded. A run with no directed messages reports unavailable, not 0 %. |
| Median / p95 latency | Simulated round-trip latency over acknowledged directed messages, in milliseconds. The simulator advances in fixed steps, so equal step counts produce equal latencies; that is the model, not a radio measurement. |
| Amplification | Recorded forwards ÷ originated messages. |
| Frames dropped / sent | Counters from the synthetic radio (`SimulationNetwork`). |
| Partitions at end | The recorded partition count. |
| Events retained | `eventWindow.retained` / `eventWindow.total`. If the retained window is smaller, the replay says so; the counters still describe the whole run. |

`-1` and missing values are rendered as `—` with a "not measured" label. They
are never coerced to zero.

---

## Determinism and ordering

The simulator appends events node by node, so the JSON array is not a
timeline. The Lab applies its own total order: `atEpochMillis`, `round`, node
index, event kind, `messageId`, event detail, then the original array position.
The last key makes the order total for identical events. Timestamps are never
rewritten and no synthetic intermediate hops are inserted.

Consequences worth knowing:

- The same report always replays identically.
- Events at the same simulated millisecond from different nodes get a stable,
  documented order; that order is the Lab's, not a claim about real-time
  concurrency.
- Identity material and CPU wall-clock time vary between nominally identical
  runs. They are displayed as provenance but are not used as evidence.

---

## Privacy and the build boundary

- Reports are read with the File API in the browser. There is no upload route,
  no remote fetch of a report and no analytics on the contents.
- `/lab` is public and lazy-loaded. It does not import the private command
  centre, auth or backend modules. The public build
  (`npm run build:public`) contains the Lab; `/command` and `/signin` remain
  excluded in that mode.
- The page makes no network request after the bundle loads.

---

## Accessibility

- The map is an SVG image with an accessible summary; the node chip list and
  text link/state panel are the equivalent non-visual view.
- Every control has a visible focus ring, all inputs have labels, and replay
  position is a real range input with a spoken time/value.
- Pan and zoom work from the keyboard when the map has focus. Node selection
  does not require the map: the chips are ordinary buttons.
- Decorative halo animation is opacity-only and freezes under
  `prefers-reduced-motion: reduce`. No information is carried by colour alone;
  every state is also written.
- Errors use `role="alert"`; the current replay step is written as text.

---

## Local development

```bash
cd apps/web
npm install
npm run dev        # http://localhost:5173/lab
```

Checks:

```bash
cd apps/web
npm test                # Vitest, all web tests
npx tsc -b --force      # type-check
npm run lint            # ESLint, zero warnings
npm run build:public    # public-only production build

cd ../..
mvn -q -pl packages/mesh-simulator -am test   # simulator + report tests
```

The Lab tests are `src/lab/*.test.ts` (parsing, ordering, journeys, metrics,
scenario commands, comparison) and `src/pages/LabPage.test.tsx` (page, import
errors, compare, public route).

---

## Screenshots

| | |
| --- | --- |
| Empty state | [lab-empty.png](./screenshots/lab-empty.png) |
| Replay with the ring sample | [lab-replay.png](./screenshots/lab-replay.png) |
| Message journey at the end of the run | [lab-message-journey.png](./screenshots/lab-message-journey.png) |
| Node details | [lab-node-details.png](./screenshots/lab-node-details.png) |
| Two-report comparison | [lab-compare.png](./screenshots/lab-compare.png) |
| Mobile layout | [lab-mobile.png](./screenshots/lab-mobile.png) |

Captured against the public build with the committed samples. The screenshots
are simulations, not field measurements.

---

## Known limitations

- **The report's event window can be truncated.** The simulator caps retained
  events. The replay covers the retained window, not the whole run; affected
  views say so. Counters still describe the whole run.
- **Rejected events are noisy.** Many `rejected` events in a report are ACKs a
  relay could not correlate, not messages that failed. The Lab shows the
  recorded reason and does not turn those events into a per-message failure.
- **No radio physics.** There is no RSSI, SNR, bandwidth or physical range in
  the simulator. The Lab does not draw coverage or signal strength.
- **The unique path is unknown.** The view lists recorded handoffs and says
  explicitly that a flood is not one reconstructed route.
- **No wall-clock provenance.** The report has simulated time, not the moment
  the run happened on the host. The Lab does not invent one. `timing.simulatedMillis`
  is the configured round time (`rounds × step`); the simulator runs eight
  settling passes afterwards, so recorded events can extend past it. The Lab
  labels that figure "Round time" and follows event timestamps for the replay.
- **Latency is step-quantised.** It is a model property, not a measurement.
- **Peer indices are strings in the JSON.** The Lab accepts strings and
  numbers; third-party consumers should do the same.
- **No in-browser simulation.** The scenario builder only generates a command.
  Running it requires JDK 21 and Maven.
