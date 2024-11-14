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
        google()
        mavenCentral()
    }
}

rootProject.name = "ARPA-E"
include(":app")
//project(":opencv").projectDir = File(rootDir, "packages/OpenCV-android-sdk/sdk/")
//include(":opencv")
//project(":opencv").projectDir = file("${rootDir}/packages/OpenCV-android-sdk/sdk")
//project(":opencv3412").projectDir = File(rootDir, "packages/OpenCV-android-sdk_3412/sdk/")
//include(":opencv3412")
