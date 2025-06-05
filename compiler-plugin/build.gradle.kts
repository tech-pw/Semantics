plugins {
    `maven-publish`
    kotlin("kapt")
    alias(libs.plugins.jetbrains.kotlin.jvm)
}

group = "org.example"
version = "unspecified"

dependencies {
    implementation(libs.kotlin.compiler.embeddable)
    implementation(libs.kotlin.gradle.plugin.api)
    implementation("com.google.auto.service:auto-service:1.1.1")
    kapt("com.google.auto.service:auto-service:1.1.1")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}