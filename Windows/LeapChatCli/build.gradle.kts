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
        linkerOpts("-Wl,--allow-multiple-definition")
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
