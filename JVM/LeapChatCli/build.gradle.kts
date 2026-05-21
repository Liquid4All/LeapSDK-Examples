plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.application)
  alias(libs.plugins.com.ncorti.ktfmt.gradle)
}

kotlin { jvmToolchain(21) }

dependencies {
  implementation(libs.leap.sdk)
  implementation(libs.kotlinx.coroutines.core)
}

application {
  mainClass.set("ai.liquid.leap.cli.MainKt")
  applicationName = "leap-chat-cli"
}

ktfmt { googleStyle() }
