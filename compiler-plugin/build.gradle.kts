plugins {
    `maven-publish`
    kotlin("kapt")
    alias(libs.plugins.jetbrains.kotlin.jvm)
}

group = "io.github.farhazulmullick"
version = "1.0.0"
val mArtifactId = "semantics-compiler-plugin"

dependencies {
    implementation(libs.kotlin.compiler.embeddable)
    implementation(libs.kotlin.gradle.plugin.api)
    implementation("com.google.auto.service:auto-service:1.1.1")
    kapt("com.google.auto.service:auto-service:1.1.1")
    testImplementation(kotlin("test"))
}

// Add publishing configuration
publishing {
    publications {
        create<MavenPublication>("semanticsCompilerPlugin") {
            from(components["java"])

            artifactId = mArtifactId

            pom {
                name.set("Semantics Compiler Plugin")
                description.set("A Kotlin compiler plugin for semantics")
            }
        }
    }
    repositories {
        mavenLocal()
    }
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}