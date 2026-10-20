# Security Policy

ResQNet is safety-adjacent software. A security defect can misdirect a rescue,
not merely leak data. Please report vulnerabilities privately and give the
maintainer a chance to fix them before public disclosure.

## Supported versions

| Version | Supported |
|---|---|
| `0.2.x` (Android beta, current) | Yes |
| `0.1.0-alpha` | No; superseded experimental preview |
| `main` branch | Yes, for fixes and review |

Only the signed release artifacts published on the
[Releases page](https://github.com/Gautam-aman/ResQNet/releases) are supported
builds. APKs built from a fork or a local working tree are not.

## Reporting a vulnerability

**Do not open a public issue, pull request, or discussion containing exploit
details.**

GitHub private vulnerability reporting is not currently enabled on this
repository. Report privately to the maintainer instead:

- Contact [@Gautam-aman](https://github.com/Gautam-aman) through GitHub and ask
  for a private channel if one is not already available.

Include:

1. The affected component and version or commit.
2. What an attacker gains, and under which adversary from the
   [threat model](docs/security/threat-model.md).
3. Reproduction steps or a minimal proof of concept.
4. Whether you reproduced it, read it from source, or inferred it.
5. Any suggested fix or mitigation.

Do not include real location data, personal data, or credentials belonging to
other people. Use test devices and synthetic data.

## What to expect

- Acknowledgement within 7 days, best effort.
- An initial assessment and a decision on severity and scope.
- A fix on a private branch where practical, with credit in the release notes
  unless you ask to remain anonymous.
- Coordinated disclosure: please wait until a fixed release is published before
  writing publicly about the issue.

This is a small volunteer project with no bug bounty. There is no payment, but
serious reports are credited.

## Scope

In scope:

- `packages/mesh-core`: envelope validation, signatures, routing, replay and
  flood limits, durable queue semantics.
- `apps/android`: Nearby Connections transport, identity binding, permission
  handling, local storage.
- `services/backend`: authentication, authorization, gateway ingestion, audit
  trail, database migrations.
- `apps/web`: command-centre authentication and authorization handling.
- Release and CI configuration that could produce or distribute a compromised
  artifact.

Out of scope:

- Denial of service through physical radio jamming or battery exhaustion.
- Attacks that require a rooted device, a compromised OS, or extraction of the
  user's own keystore.
- Vulnerabilities in third-party dependencies that are already publicly known
  and have no ResQNet-specific impact. Report those upstream.
- Social engineering of responders, and physical attacks on devices.
- The documented absence of payload confidentiality. ResQNet does not claim to
  encrypt message contents; see the
  [threat model](docs/security/threat-model.md).

## Known limitations

These are documented design limitations, not secrets. Do not report them as new
vulnerabilities, but do report a concrete way to make one worse than described:

- **No payload encryption.** Anyone in radio range can read message contents.
- **Identity binding depends on the user.** A device identity becomes trusted
  only after a human compares safety numbers. The trust model and its residual
  risks are described in [docs/security/threat-model.md](docs/security/threat-model.md).
- **The gateway uses a shared device token.** It is a single bearer secret for
  all devices, so a leaked token affects the whole installation.
- **Multi-hop hardware behaviour is unverified.** Simulation results are not
  evidence about real radios; see
  [apps/android/DEVICE_TESTING.md](apps/android/DEVICE_TESTING.md).

## Verifying release artifacts

Each Android release ships with `SHA256SUMS`, `ARTIFACTS.md`, and a certificate
fingerprint. Before installing an APK outside a trusted channel, verify it:

```bash
sha256sum -c SHA256SUMS
apksigner verify --print-certs resqnet-v0.2.0-beta-release.apk
```

The `v0.2.0-beta` APK is signed with certificate SHA-256:

```text
ccba33fd25d4acaedcf130f1fdd46e22497d501a244cd459fdebf616fba1237a
```

A signature that does not match this fingerprint means the APK was not built by
this project's release workflow. Do not install it.

## Safe harbor

Security research conducted in good faith under this policy will not be
reported to GitHub or pursued legally by the maintainer. Good faith means:
testing only against your own devices and deployments, not accessing other
people's data, not degrading service for others, and giving the maintainer a
reasonable opportunity to fix the issue before disclosure.
