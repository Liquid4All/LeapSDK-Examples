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
        // the user's PATH. The .exe still depends on libgcc_s_seh-1.dll (no
        // static counterpart ships in Konan's MinGW toolchain — `-lgcc_s`
        // resolves to libgcc_s.dll.a, the import library); for now, that DLL
        // is expected to be alongside the .exe (or on PATH) at run time. A
        // proper redistributable bundle of MinGW runtime DLLs is deferred.
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
