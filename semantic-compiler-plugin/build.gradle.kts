import java.net.URI
plugins {
    `maven-publish`
    kotlin("kapt")
    alias(libs.plugins.jetbrains.kotlin.jvm)
}

group = "io.github.tech-pw"
version = "1.1.1"
val mArtifactId = "semantic-compiler-plugin"

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
        create<MavenPublication>("semanticCompilerPlugin") {
            from(components["java"])

            artifactId = mArtifactId

            pom {
                name.set("Semantic Compiler Plugin")
                description.set("A Kotlin compiler plugin for adding compose semantics test-tags to composables.")
            }
        }
    }
    repositories {
        // local
        mavenLocal()
        // pw nexus
        maven {
            url = URI.create("https://nexus3.penpencil.co/repository/maven-hosted-snapshots/")
            credentials {
                username = "nx-publish"
                password = "Nexus@12345"
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}