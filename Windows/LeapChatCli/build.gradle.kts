import java.io.File

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  // Auto-wires leap-sdk-mingwx64:<ver>:natives@zip and extracts inference_engine.dll +
  // libinference_engine_llamacpp_backend.dll + ie_zip.dll alongside the produced .exe so
  // standard Windows DLL co-location loads them at runtime.
  alias(libs.plugins.leap.sdk.native.libs)
}

kotlin {
  mingwX64 {
    binaries {
      executable {
        entryPoint = "ai.liquid.leap.cli.main"
        baseName = "leap-chat-cli"
        // Both ktor-client-curl-mingwx64 (statically links pthread-win32) and
        // libinference_engine.dll (also statically links pthread-win32) pull in
        // duplicate definitions of pthread_*, sched_yield, etc. lld errors out
        // with `duplicate symbol` by default. -Wl,--allow-multiple-definition
        // tells lld (via the clang driver Kotlin/Native invokes on mingwX64) to
        // take the first definition and ignore subsequent — safe because both
        // sides are using the same pthread-win32 ABI.
        //
        // Note: must be the gcc-driver passthrough form (`-Wl,...`) — unlike
        // linuxX64 (which invokes ld.lld directly and wants bare `--xxx`),
        // mingwX64 invokes clang++ which only accepts driver-level flags.
        //
        // -Wl,-Bstatic,-lstdc++,-lwinpthread,-Bdynamic:
        // statically link the MinGW C++ + winpthread runtimes into the .exe so
        // it does NOT depend on libstdc++-6.dll / libwinpthread-1.dll being on
        // the user's PATH. (libgcc_s_seh-1.dll is handled separately, by
        // copying it next to the .exe — see copyMingwRuntimeDlls below — since
        // libgcc_s on MinGW-w64 x86_64 has no usable static counterpart in
        // Konan's toolchain: -lgcc_s resolves to libgcc_s.dll.a, the import
        // library, and there's no libgcc_s.a to fall back to.)
        //
        // The explicit `-Bstatic -l<name>` form is required (as opposed to
        // the clang-driver `-static-libstdc++` flag): Kotlin/Native's
        // mingwX64 link injects `-lstdc++` etc. into the link line directly
        // rather than letting the clang++ driver pick the C++ runtime, so the
        // driver-level flags have nothing to substitute. The explicit form
        // forces those specific `-l` lookups to resolve to the static `.a`
        // archive instead of the import library for the DLL. -Bdynamic at
        // the end switches back so the rest of the .exe (kernel32, user32,
        // ws2_32, the inference_engine DLLs) still imports dynamically.
        linkerOpts(
          "-Wl,--allow-multiple-definition",
          "-Wl,-Bstatic,-lstdc++,-lwinpthread,-Bdynamic",
        )
      }
    }
  }

  // Copy libgcc_s_seh-1.dll from Konan's bundled MinGW toolchain alongside
  // the produced .exe so the binary is fully self-contained. (-lgcc_s can't be
  // statically linked on MinGW-w64 x86_64 — Konan ships only the import lib
  // libgcc_s.dll.a, no libgcc_s.a — so we ship the DLL next to the .exe.)
  // Wired as a finalizer of linkReleaseExecutableMingwX64 so the file lands in
  // releaseExecutable/ alongside the .exe and the inference_engine DLLs.
  val copyMingwRuntimeDlls by tasks.registering(Copy::class) {
    from(providers.provider {
      val home = File(System.getProperty("user.home"))
      val konanRoot = File(home, ".konan/dependencies")
      val candidate = konanRoot.walkTopDown()
        .firstOrNull { it.isFile && it.name == "libgcc_s_seh-1.dll" }
      checkNotNull(candidate) {
        "libgcc_s_seh-1.dll not found under $konanRoot — Konan toolchain layout may have changed"
      }
    })
    into(layout.buildDirectory.dir("bin/mingwX64/releaseExecutable"))
  }
  tasks.named("linkReleaseExecutableMingwX64") { finalizedBy(copyMingwRuntimeDlls) }

  sourceSets {
    val mingwX64Main by getting {
      dependencies {
        implementation(libs.leap.sdk)
        implementation(libs.kotlinx.coroutines.core)
        // Ktor Curl engine for TLS on Kotlin/Native (the leap-sdk's bundled CIO
        // engine doesn't support TLS on Native; injecting HttpClient(Curl) into
        // LeapDownloader fixes HTTPS calls to leap.liquid.ai). Windows variant
        // statically links libcurl + openssl, so no system-side install needed.
        // ContentNegotiation + kotlinx-json plugins match what leap-sdk's own
        // default client installs — required for body<Manifest>() deserialization.
        implementation(libs.ktor.client.curl)
        implementation(libs.ktor.client.content.negotiation)
        implementation(libs.ktor.serialization.kotlinx.json)
      }
    }
  }
}
