plugins {
    `maven-publish`
    kotlin("kapt")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.vanniktech.publish)
}

dependencies {
    implementation(libs.kotlin.compiler.embeddable)
    implementation(libs.kotlin.gradle.plugin.api)
    implementation(libs.auto.service)
    kapt(libs.auto.service)
    testImplementation(kotlin("test"))
}

mavenPublishing {
    coordinates(
        groupId = "io.github.tech-pw",
        artifactId = "semantic-compiler-plugin",
        version = project.findProperty("VERSION") as String?
    )
    pom { name.set("Semantic Compiler Plugin") }
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}