plugins {
  alias(libs.plugins.kotlin.multiplatform)
  // Auto-wires the leap-sdk-linuxx64:<ver>:natives@zip dependency and extracts
  // libinference_engine.so + libinference_engine_llamacpp_backend.so + libie_zip.so
  // alongside the produced executable so the cinterop $ORIGIN rpath finds them at runtime.
  alias(libs.plugins.leap.sdk.native.libs)
}

kotlin {
  linuxX64 {
    binaries {
      executable {
        entryPoint = "ai.liquid.leap.cli.main"
        baseName = "leap-chat-cli"
      }
    }
  }

  sourceSets {
    val linuxX64Main by getting {
      dependencies {
        implementation(libs.leap.sdk)
        implementation(libs.kotlinx.coroutines.core)
        // Ktor Curl engine for TLS on Kotlin/Native (the leap-sdk's bundled CIO
        // engine doesn't support TLS on Native; injecting HttpClient(Curl) into
        // LeapDownloader fixes HTTPS calls to leap.liquid.ai). The
        // ContentNegotiation + kotlinx-json plugins match what leap-sdk's own
        // default client installs — required for `body<Manifest>()` to deserialize
        // the JSON manifest response.
        implementation(libs.ktor.client.curl)
        implementation(libs.ktor.client.content.negotiation)
        implementation(libs.ktor.serialization.kotlinx.json)
      }
    }
  }
}
