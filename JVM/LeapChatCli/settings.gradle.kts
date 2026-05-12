pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
    mavenLocal()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    mavenLocal()
    mavenCentral()
  }
}

rootProject.name = "LeapChatCli"
