pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        maven {
            name = "Central Portal Snapshots"
            url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        }
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "tldw"
include(":app")
