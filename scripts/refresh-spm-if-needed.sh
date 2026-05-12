#!/usr/bin/env bash
# Detect and recover from a force-pushed SPM tag for the LeapSDK pin.
#
# When upstream re-publishes a SNAPSHOT tag (e.g. v0.10.4-SNAPSHOT) under a
# new commit, SPM still has the OLD commit SHA recorded in Package.resolved
# and refuses to resolve with "does not match previously recorded value".
# The fix per ~/.claude/CLAUDE.md is a full SPM cache clear; this script
# automates the detection + clear so you can `xcodegen generate` and resolve
# without thinking about it.
#
# Compares: upstream tag SHA (`git ls-remote`) vs each Package.resolved's
# `pins[].state.revision` for `leap-sdk`. If any local revision differs from
# the current upstream, purge SPM caches + DerivedData + all Package.resolved
# files under iOS/ + macOS/, so the next xcodebuild resolve fetches fresh.
#
# Idempotent / safe to run repeatedly; exits 0 when there's nothing to do.
#
# Usage: ./scripts/refresh-spm-if-needed.sh

set -euo pipefail

cd "$(dirname "$0")/.."

# All Apple demos pin the same exactVersion in their project.yml — pick the
# first one we find as the source of truth. (Avoids hardcoding the version
# in this script: bumping any demo's project.yml is enough to update what
# this script checks.)
PROJECT_YML=$(ls iOS/*/project.yml macOS/*/project.yml 2>/dev/null | head -1 || true)
if [ -z "$PROJECT_YML" ]; then
  echo "no Apple demos with project.yml found — nothing to check"
  exit 0
fi

SDK_VERSION=$(grep -E '^[[:space:]]+exactVersion:' "$PROJECT_YML" | head -1 \
  | sed -E 's/.*exactVersion:[[:space:]]*([^[:space:]]+).*/\1/')
if [ -z "$SDK_VERSION" ]; then
  echo "could not parse exactVersion from $PROJECT_YML — skipping"
  exit 0
fi
TAG="v$SDK_VERSION"

# Upstream tag SHA right now
REMOTE_SHA=$(git ls-remote https://github.com/Liquid4All/leap-sdk.git "refs/tags/$TAG" 2>/dev/null \
  | awk '{print $1}')
if [ -z "$REMOTE_SHA" ]; then
  echo "tag $TAG not found at github.com/Liquid4All/leap-sdk — skipping"
  exit 0
fi

# All Package.resolved files in the worktree
RESOLVED_FILES=$(find iOS macOS -name 'Package.resolved' 2>/dev/null || true)
if [ -z "$RESOLVED_FILES" ]; then
  echo "no Package.resolved files found locally — nothing cached, nothing to do"
  echo "(run xcodegen generate + open the .xcodeproj to populate, or xcodebuild -resolvePackageDependencies)"
  exit 0
fi

# Walk every Package.resolved; collect any with a stale leap-sdk revision.
# Package.resolved v3 stores per-pin state at .pins[].state.revision; v2 used
# .object.pins[].state.revision. Try both shapes via simple grep so we don't
# need jq.
STALE=()
for f in $RESOLVED_FILES; do
  LOCAL_SHA=$(awk '
    /"identity"[[:space:]]*:[[:space:]]*"leap-sdk"/ { in_block=1; next }
    in_block && /"revision"[[:space:]]*:/ {
      gsub(/[",]/, ""); print $NF; exit
    }
  ' "$f")
  if [ -n "$LOCAL_SHA" ] && [ "$LOCAL_SHA" != "$REMOTE_SHA" ]; then
    STALE+=("$f $LOCAL_SHA")
  fi
done

if [ ${#STALE[@]} -eq 0 ]; then
  echo "SPM cache is fresh — $TAG resolves to ${REMOTE_SHA:0:12} on both upstream and every local Package.resolved"
  exit 0
fi

echo "$TAG was force-pushed (upstream is now at ${REMOTE_SHA:0:12}); the following Package.resolved files reference a stale revision:"
for entry in "${STALE[@]}"; do
  set -- $entry
  echo "  $1 -> ${2:0:12}"
done
echo
echo "Clearing SPM caches and stale Package.resolved files…"
rm -rf "$HOME/Library/Caches/org.swift.swiftpm"
rm -rf "$HOME/Library/org.swift.swiftpm"
rm -rf "$HOME/Library/Developer/Xcode/DerivedData"
find iOS macOS -name 'Package.resolved' -delete 2>/dev/null || true

echo "Done. Re-run \`xcodegen generate\` in each demo dir and open the .xcodeproj (or run \`xcodebuild -resolvePackageDependencies -project <name>.xcodeproj\`) to fetch the new tag."
