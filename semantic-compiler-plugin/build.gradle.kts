plugins {
    `maven-publish`
    kotlin("kapt")
    alias(libs.plugins.jetbrains.kotlin.jvm)
}

group = "live.pw.compose.semantic"
version = "1.0.0"
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
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}