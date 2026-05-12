# Local SDK testing for the iOS demos

The `project.yml` files in this directory pin their `LeapSDK` Swift package to the published
URL `https://github.com/Liquid4All/leap-sdk`. That's what most contributors want — open a
demo's `*.xcodeproj`, build, run. Maven Central / GitHub release publishes ship as the
canonical artifact set.

If you're iterating on **unpublished** SDK changes in a `leap-android-sdk` worktree and want
the demos to consume them locally, follow the steps below.

## One-time setup

In your `leap-android-sdk` worktree, build the XCFrameworks and run the embed + dual-import
post-process tasks:

```sh
JAVA_HOME=$ZULU_21 ./gradlew \
  :leap-sdk:assembleLeapSDKReleaseXCFramework \
  :leap-sdk-model-downloader:assembleLeapModelDownloaderReleaseXCFramework \
  :leap-sdk-openai-client:assembleLeapOpenAIClientReleaseXCFramework \
  :leap-ui:assembleLeapUiReleaseXCFramework \
  :leap-sdk:embedDylibsInXCFramework \
  :leap-sdk-model-downloader:embedDylibsInXCFramework \
  :leap-sdk-model-downloader:appendDualImportGuardHeader \
  -PliquidInferenceEngineGithubRunId=<run-id>
```

The worktree's `Package.swift` has `binaryTarget(path: "XCFrameworks/<Name>.xcframework")`
entries pointing at gitignored symlinks under `XCFrameworks/` that resolve to the build
outputs above. `swift package resolve` from inside the worktree should report
"resolved source packages" cleanly.

## Pointing a demo at the local SDK

For each demo you want to test:

```sh
# 1. (Re)generate the demo's Xcode project from its project.yml.
cd iOS/<DemoName>
xcodegen generate
cd ../..

# 2. Swap that demo's SPM reference from the remote URL to the worktree's Package.swift.
WORKTREE=/absolute/path/to/leap-android-sdk/worktrees/<branch> \
  scripts/use-local-sdk.sh
```

`scripts/use-local-sdk.sh` rewrites each demo's `*.xcodeproj/project.pbxproj` (gitignored —
edits never commit) so its `XCRemoteSwiftPackageReference` becomes an
`XCLocalSwiftPackageReference` pointing at `$WORKTREE`. The original UUIDs are preserved so
existing `XCSwiftPackageProductDependency` entries continue to resolve.

Re-run both steps any time `xcodegen generate` regenerates the pbxproj (it always falls back
to the remote URL from `project.yml`).

## Reverting

When the SDK release publishes and you no longer need the local pointer, just
`xcodegen generate` once and the demo goes back to the remote URL. Or delete
`<DemoName>.xcodeproj/` entirely and regenerate.

## Why this is local-only

`project.yml`'s `packages:` block holds the remote URL, not a local path, because:

- Most contributors don't have a `leap-android-sdk` worktree.
- The local path is developer-machine-specific (`$WORKTREE` differs per machine).
- The remote URL is what shipping versions of the demos should resolve.

The `XCLocalSwiftPackageReference` lives only in the gitignored pbxproj, never in the tracked
`project.yml` — so committing demo changes doesn't accidentally hard-code an absolute path
into the public examples repo.
