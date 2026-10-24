## Problem, benefit and current behavior

Help users discover shapes without starting a simulation. SimulatorCli USAGE lists six canonical names and Options.parseShape maps aliases; Topology.Shape defines supported shapes. There is no list-only flag.

**Evidence:** source-confirmed on published main at [4986e28](https://github.com/Gautam-aman/ResQNet/commit/4986e284f8c4f1add60cb1e2568d0cdeadc29831); proposed enhancement, not a reproduced runtime defect. Start from current GitHub main; no unpublished local commits are required.

## Scope and verified starting points

- [packages/mesh-simulator/src/main/java/dev/resqnet/mesh/simulator/SimulatorCli.java](https://github.com/Gautam-aman/ResQNet/blob/main/packages/mesh-simulator/src/main/java/dev/resqnet/mesh/simulator/SimulatorCli.java)
- [packages/mesh-simulator/src/main/java/dev/resqnet/mesh/simulator/Topology.java](https://github.com/Gautam-aman/ResQNet/blob/main/packages/mesh-simulator/src/main/java/dev/resqnet/mesh/simulator/Topology.java)
- [packages/mesh-simulator/src/test/java/dev/resqnet/mesh/simulator/TopologyTest.java](https://github.com/Gautam-aman/ResQNet/blob/main/packages/mesh-simulator/src/test/java/dev/resqnet/mesh/simulator/TopologyTest.java)
- [docs/demo.md](https://github.com/Gautam-aman/ResQNet/blob/main/docs/demo.md)

## Acceptance criteria

- [ ] --list-topologies lists line, ring, star, mesh, two-cluster, random in documented stable order and returns success.
- [ ] Reuse existing topology definitions where practical.
- [ ] Do not construct MeshSimulation, generate keys, write reports or publish to a gateway.
- [ ] Retain current --help precedence; reject list mode combined with simulation/output/gateway flags or unknown options with a clear diagnostic and nonzero exit.
- [ ] Update CLI help and docs/demo.md.
- [ ] Add Java tests for output, exit status, incompatible arguments and absence of file/network side effects.

## Tests and commands

Add SimulatorCliTest using public run(String[]); restore captured stdout/stderr. Coordinate parser edits with #72 and PR #67 (fixing #49).

```bash
mvn -pl packages/mesh-simulator -am test
```

## Non-goals

No topology generation changes, stdout JSON (#73), simulation runs or --help precedence changes (#72).

## Difficulty and contributor guidance

**Beginner** — Java, CLI parsing, JUnit. Check assignees, comment `/assign` and wait for confirmation before starting. Ask here for setup or test guidance; use `/unassign` to release the issue. Report exact commands, results and anything untested.

Read the [contributor guide](https://github.com/Gautam-aman/ResQNet/blob/main/CONTRIBUTING.md), [PR evidence template](https://github.com/Gautam-aman/ResQNet/blob/main/.github/PULL_REQUEST_TEMPLATE.md) and [security policy](https://github.com/Gautam-aman/ResQNet/blob/main/SECURITY.md). Guardian readiness is advisory; maintainers review and decide.
