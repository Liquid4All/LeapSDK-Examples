pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
    google()
  }
}

dependencyResolutionManagement {
  // PREFER_SETTINGS (not FAIL_ON_PROJECT_REPOS) so the Kotlin/Wasm plugin can declare
  // its own toolchain ivy repos at the project level without erroring; ours below
  // win for org.nodejs / com.yarnpkg / com.github.webassembly via exclusiveContent.
  repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
  repositories {
    mavenCentral()
    google()
    maven {
      name = "Central Portal Snapshots"
      url = uri("https://central.sonatype.com/repository/maven-snapshots/")
    }

    // Required for Kotlin/Wasm + Kotlin/JS Node.js / Yarn / Binaryen toolchain
    // downloads. FAIL_ON_PROJECT_REPOS blocks the Kotlin plugin from adding its
    // own ivy repos for these, so we declare them here explicitly.
    exclusiveContent {
      forRepository {
        ivy {
          name = "Node Distributions"
          url = uri("https://nodejs.org/dist/")
          patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
          metadataSources { artifact() }
          content { includeModule("org.nodejs", "node") }
        }
      }
      filter { includeGroup("org.nodejs") }
    }
    exclusiveContent {
      forRepository {
        ivy {
          name = "Yarn Distributions"
          url = uri("https://github.com/yarnpkg/yarn/releases/download/")
          patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
          metadataSources { artifact() }
          content { includeModule("com.yarnpkg", "yarn") }
        }
      }
      filter { includeGroup("com.yarnpkg") }
    }
    exclusiveContent {
      forRepository {
        ivy {
          name = "Binaryen Distributions"
          url = uri("https://github.com/WebAssembly/binaryen/releases/download/")
          patternLayout {
            artifact("version_[revision]/[artifact]-version_[revision](-[classifier]).[ext]")
          }
          metadataSources { artifact() }
          content { includeModule("com.github.webassembly", "binaryen") }
        }
      }
      filter { includeGroup("com.github.webassembly") }
    }
  }
}

rootProject.name = "LeapVoiceAssistantDemoWeb"
