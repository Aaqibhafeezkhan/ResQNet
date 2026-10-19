#!/usr/bin/env bash
#
# Builds every release artifact and records what each one actually is.
#
# The point is the RECORD, not the build. A checksum for an artifact nobody
# can identify is close to worthless, so every file here is described in the
# output alongside its digest: what it is, what was verified about it, and
# what remains unverified.
#
# Run from the repository root:
#   scripts/build_release_artifacts.sh
#   scripts/build_release_artifacts.sh --exclude-android
#
# --exclude-android skips Gradle entirely and emits no APK. Used for the
# v0.1.0-alpha release, because physical-device networking has never been
# tested and no Android binary may be published until it has been.
#
# Writes to dist/. Nothing outside dist/ and the build directories is
# touched, and no signing material is read from the repository.

set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$(pwd)"
DIST="$ROOT/dist"

INCLUDE_ANDROID=1
for arg in "$@"; do
    case "$arg" in
        --exclude-android) INCLUDE_ANDROID=0 ;;
        -h|--help) sed -n '3,17p' "$0"; exit 0 ;;
        *) echo "error: unknown argument '$arg'" >&2; exit 2 ;;
    esac
done

if [ ! -d "$ROOT/.git" ]; then
    echo "error: run this from a ResQNet checkout" >&2
    exit 2
fi

# The version comes from the root pom, which every module inherits. Deriving it
# from the Android build file instead would have made the Android app the
# version authority for a release that ships no Android binary.
VERSION="$(sed -n 's|.*<version>\(0[^<]*\)</version>.*|\1|p' pom.xml | head -1)"
if [ -z "$VERSION" ]; then
    echo "error: could not read the project version from pom.xml" >&2
    exit 2
fi

GIT_SHA="$(git rev-parse --short HEAD 2>/dev/null || echo unknown)"
NOTES_SRC="$ROOT/docs/release/$VERSION"

# Flat, deliberately. GitHub release assets are always flat, so a nested
# dist/ and a flat release mean two layouts for one set of files and a
# checksum file that names paths nobody will have. One layout, one digest
# file, and `sha256sum -c` works identically from dist/ and from a
# downloaded release.
CHECKSUMS="$DIST/SHA256SUMS"
MANIFEST="$DIST/ARTIFACTS.md"

echo "==> ResQNet $VERSION (commit $GIT_SHA)"

# The whole directory goes, not just the known subdirectories. A stale file
# left by an earlier build would be picked up by the checksum sweep and
# published as if it belonged to this release, which is exactly the kind of
# mistake that is invisible until somebody downloads it.
rm -rf "$DIST"
mkdir -p "$DIST"

# ---------------------------------------------------------------- Java ----
echo "==> Building Java modules"
mvn -q clean package -DskipTests

# ----------------------------------------------------------------- Web ----
echo "==> Building the web command center"
if [ ! -d apps/web/node_modules ]; then
    (cd apps/web && npm ci --no-audit --no-fund)
fi
(cd apps/web && npm run build)

# ---------------------------------------------------------------- Docs ----
# Hand-authored release notes and installation guide, copied verbatim when the
# repository carries them for this version. The generated manifest below is
# separate: it describes the files in THIS build directory, and is therefore
# regenerated on every run rather than committed.
NOTES_BODY="No RELEASE_NOTES.md was found for $VERSION in docs/release/."
INSTALL_BODY="No INSTALLATION.md was found for $VERSION in docs/release/."
if [ -f "$NOTES_SRC/RELEASE_NOTES.md" ]; then
    cp "$NOTES_SRC/RELEASE_NOTES.md" "$DIST/RELEASE_NOTES.md"
    NOTES_BODY="$(cat "$DIST/RELEASE_NOTES.md")"
fi
if [ -f "$NOTES_SRC/INSTALLATION.md" ]; then
    cp "$NOTES_SRC/INSTALLATION.md" "$DIST/INSTALLATION.md"
    INSTALL_BODY="$(cat "$DIST/INSTALLATION.md")"
fi

# ------------------------------------------------------------ Packages ----
echo "==> Collecting artifacts"
cp packages/mesh-simulator/target/*-runnable.jar \
   "$DIST/resqnet-simulator-v$VERSION.jar" 2>/dev/null \
   || { echo "error: runnable simulator jar was not produced" >&2; exit 1; }
cp services/backend/target/backend-*.jar \
   "$DIST/resqnet-backend-v$VERSION.jar" 2>/dev/null \
   || { echo "error: backend jar was not produced" >&2; exit 1; }

# A flat archive of the built assets plus the instructions needed to serve
# them, because someone who unzips this is holding the only copy. Staged in a
# temp directory so the document is inside the archive rather than beside it.
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
cp -R apps/web/dist/. "$STAGE/"
cp "$NOTES_SRC/INSTALLATION.md" "$STAGE/INSTALLATION.md"

# Compression is stored rather than deflated: the JavaScript inside is already
# gzip-servable and a command centre is often deployed to a small device over a
# slow link, where inflating an already-compressed bundle is wasted work.
(cd "$STAGE" && zip -q -0 -r "$DIST/resqnet-web-v$VERSION.zip" . -x '.*')

# ------------------------------------------------------------- Android ----
APK_NOTE="**No APK in this release.** Physical-device networking has never
been tested, so no Android binary is published. Source is in the repository."

if [ "$INCLUDE_ANDROID" -eq 1 ]; then
    echo "==> Building the Android app"
    # Signing properties, if the developer has them, come from the user's
    # Gradle home and are never read from the repository.
    (cd apps/android && ./gradlew --quiet assembleDebug assembleRelease)
    APK_SRC_RELEASE="apps/android/app/build/outputs/apk/release/app-release.apk"
    APK_SRC_UNSIGNED="apps/android/app/build/outputs/apk/release/app-release-unsigned.apk"
    if [ -f "$APK_SRC_RELEASE" ]; then
        cp "$APK_SRC_RELEASE" "$DIST/resqnet-$VERSION-release.apk"
    elif [ -f "$APK_SRC_UNSIGNED" ]; then
        cp "$APK_SRC_UNSIGNED" "$DIST/resqnet-$VERSION-release-UNSIGNED.apk"
    fi
    cp apps/android/app/build/outputs/apk/debug/app-debug.apk \
       "$DIST/resqnet-$VERSION-debug.apk"
else
    echo "==> Skipping Android (--exclude-android)"
fi

# ----------------------------------------------------------- Manifest -----
JAVA_MIN=21
cat > "$MANIFEST" <<EOF
# Artifact manifest — ResQNet $VERSION

Generated by \`scripts/build_release_artifacts.sh\` at commit $GIT_SHA.
This file is **generated** and describes the contents of this build
directory. The human-written documents are \`RELEASE_NOTES.md\` and
\`INSTALLATION.md\`.

## Files

| File | What it is | Verified |
|---|---|---|
| \`resqnet-simulator-v$VERSION.jar\` | Self-contained simulator and the demonstration. Java $JAVA_MIN+. | Deterministic; demonstration exits 0 |
| \`resqnet-backend-v$VERSION.jar\` | Spring Boot coordination gateway. Needs PostgreSQL $JAVA_MIN+. No bundled H2 driver. | Unit + integration + real PostgreSQL |
| \`resqnet-web-v$VERSION.zip\` | Production static assets for the command centre, plus serving instructions. | lint, typecheck, unit tests, production build |
$(if [ "$INCLUDE_ANDROID" -eq 1 ]; then echo "| \`apk/*\` | Android app. | Unit tests only — **never run on hardware** |"; else echo "| _(none)_ | $APK_NOTE | — |"; fi)

## Integrity

\`\`\`sh
sha256sum -c SHA256SUMS     # Linux
shasum -a 256 -c SHA256SUMS # macOS
\`\`\`

The jars are byte-reproducible: two builds of the same commit produce the
same digest. \`resqnet-web-v$VERSION.zip\` is not, because zip records its
own modification time. APK digests are not reproducible either, because the
Android toolchain embeds build timestamps.

## Not verified

- **Two physical Android phones have never exchanged a message.** The
  Android app has never been run on hardware at all. Defects that would
  each have prevented it were found by reading the code and fixed; none of
  that is evidence that the radio path works. The procedure is
  \`apps/android/DEVICE_TESTING.md\` and the result does not exist.
- Every benchmark figure in \`docs/benchmarks/results.md\` describes the
  routing engine against a **modelled radio**. Nothing here measures real
  range, real latency through rubble, or real battery life.
- No load, soak, or battery testing.
- PostgreSQL under concurrent writes and replication is untested;
  migrations and the incident lifecycle are tested.

## Security posture

Read \`docs/security/threat-model.md\`. The shared gateway token is a
bearer secret: revocable by rotation, but not attributable, and every
device holding it shares one identity.
EOF

# ----------------------------------------------------------- Checksums ----
echo "==> Checksums"
# -b keeps the digest on one line with a two-space separator, which is what
# both `sha256sum --check` and `shasum --check` expect. SHA256SUMS cannot
# contain its own digest, so it is written last and excluded.
( cd "$DIST" && find . -maxdepth 1 -type f ! -name SHA256SUMS | sed 's|^\./||' | sort | while read -r f; do
    printf '%s  %s\n' "$(shasum -a 256 "$f" | cut -d' ' -f1)" "$f"
done ) > "$CHECKSUMS"
cat "$CHECKSUMS"

echo
echo "==> Manifest written to $MANIFEST"
echo "==> Release notes: $( [ -f "$DIST/RELEASE_NOTES.md" ] && echo "$DIST/RELEASE_NOTES.md" || echo 'none for this version' )"
echo "==> Artifacts in $DIST"