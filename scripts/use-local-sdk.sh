#!/usr/bin/env bash
# Switches each iOS demo's SPM ref from the remote `https://github.com/Liquid4All/leap-sdk`
# package to a local Swift package on disk — typically a leap-android-sdk worktree where
# you're iterating on unpublished SDK changes. Idempotent.
#
# Run AFTER `xcodegen generate` in each demo, since xcodegen reads `project.yml` (which holds
# the remote URL) and rewrites the pbxproj each time, undoing any local edits to the SPM ref.
#
# Usage:
#   WORKTREE=/Users/.../leap-android-sdk/worktrees/<branch> \
#     scripts/use-local-sdk.sh
#
# The local package must contain a Package.swift with the same library product names the demos
# depend on (`LeapModelDownloader`, `LeapUI`, `LeapSDKMacros`, etc.). The leap-android-sdk
# worktree's Package.swift uses `binaryTarget(path: "XCFrameworks/<Name>.xcframework")` for
# this purpose — run gradle's `:<module>:assembleLeap*ReleaseXCFramework` +
# `embedDylibsInXCFramework` + `appendDualImportGuardHeader` (LMD only) first so the symlinked
# XCFrameworks exist.
#
# When the SDK release is published to GitHub, drop this script — `xcodegen generate` alone
# produces a working remote-URL pbxproj.

set -euo pipefail

: "${WORKTREE:?Set WORKTREE to the leap-android-sdk worktree absolute path}"

if [ ! -f "$WORKTREE/Package.swift" ]; then
  echo "❌ WORKTREE=$WORKTREE has no Package.swift — wrong path?" >&2
  exit 1
fi

DEMOS=(
  iOS/LeapChatExample
  iOS/LeapAudioDemo
  iOS/LeapSloganExample
  iOS/LeapVoiceAssistantDemo
  iOS/RecipeGenerator
  iOS/LeapVLMExample
)

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

updated=0
for demo in "${DEMOS[@]}"; do
  pbxproj="${demo}/$(basename "$demo").xcodeproj/project.pbxproj"
  if [ ! -f "$pbxproj" ]; then
    echo "⏭  skip $demo (no pbxproj — run 'xcodegen generate' in this demo first)"
    continue
  fi

  # Replace any XCRemoteSwiftPackageReference block with an XCLocalSwiftPackageReference
  # pointing at $WORKTREE, preserving UUIDs. The `packageReferences` array entries' comments
  # also get updated so Xcode shows the right kind in the GUI.
  WORKTREE_PATH="$WORKTREE" PBXPROJ="$pbxproj" python3 - <<'PY'
import os
import re
import sys

pbxproj = os.environ["PBXPROJ"]
worktree = os.environ["WORKTREE_PATH"]
src = open(pbxproj).read()

# Match every XCRemoteSwiftPackageReference block.
remote_pattern = re.compile(
    r'(\t\t([0-9A-F]{24}) /\* XCRemoteSwiftPackageReference "([^"]+)" \*/ = \{\n'
    r'\t\t\tisa = XCRemoteSwiftPackageReference;\n'
    r'.*?\n'
    r'\t\t\};\n)',
    re.DOTALL,
)

matches = list(remote_pattern.finditer(src))
if not matches:
    # Already local — nothing to do.
    sys.exit(0)

for m in matches:
    uuid = m.group(2)
    pkg_name = m.group(3)
    local_block = (
        f'\t\t{uuid} /* XCLocalSwiftPackageReference "{pkg_name}" */ = {{\n'
        f'\t\t\tisa = XCLocalSwiftPackageReference;\n'
        f'\t\t\trelativePath = "{worktree}";\n'
        f'\t\t}};\n'
    )
    src = src.replace(m.group(1), local_block, 1)

# Swap the section header to match.
src = src.replace(
    "/* Begin XCRemoteSwiftPackageReference section */",
    "/* Begin XCLocalSwiftPackageReference section */",
)
src = src.replace(
    "/* End XCRemoteSwiftPackageReference section */",
    "/* End XCLocalSwiftPackageReference section */",
)

# Fix up the packageReferences array entries' comments so Xcode shows the right kind.
src = re.sub(
    r'XCRemoteSwiftPackageReference "([^"]+)"',
    r'XCLocalSwiftPackageReference "\1"',
    src,
)

open(pbxproj, "w").write(src)
PY
  echo "✅ $demo → $WORKTREE"
  updated=$((updated + 1))
done

echo
echo "Switched $updated demo(s) to local SDK at $WORKTREE"
echo "Re-run 'xcodegen generate' followed by this script any time you regenerate a pbxproj."
