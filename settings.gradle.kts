pluginManagement {
    repositories {
        mavenLocal()
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
        maven { url = uri("https://jitpack.io") }
        maven {
                   url = uri("https://nexus3.penpencil.co/repository/maven-hosted-snapshots/")
                   content {
                       // Only checks for following groups in this repo
                       includeGroup("live.pw")
                       includeGroup("live.pw.kmp")
                       includeGroup("live.pw.kmm.common")
                       includeGroup("live.pw.kmp.common")
                       includeGroup("live.pw.sd-ui-client")
                       includeGroup("live.pw.vendors")
                       includeGroup("com.amazonaws.waf")
                       includeGroup("androidx.tonyodev.fetch2")
                       includeGroup("androidx.tonyodev.fetch2core")
                       includeGroup("androidx.tonyodev.fetch2okhttp")
                       includeGroup("com.osbcp")
                   }
               }
    }
}

rootProject.name = "Sementics"
include(":app")
include(":semantic-compiler-plugin")
include(":semantic-gradle-plugin")