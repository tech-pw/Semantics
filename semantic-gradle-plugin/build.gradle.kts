import java.net.URI

plugins {
    `java-library`
    id("com.gradle.plugin-publish") version "1.2.1"
    kotlin("kapt")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.vanniktech.publish)
}

tasks.register("sourcesJar", Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
    dependsOn(tasks.classes)
}

mavenPublishing {
    coordinates(
        groupId = "io.github.tech-pw",
        artifactId = "semantic-gradle-plugin",
        version = project.findProperty("VERSION") as String?
    )
    pom {
        name.set("Semantic Gradle Plugin")
    }
}

dependencies {
    implementation(libs.auto.service)
    kapt(libs.auto.service)
    implementation(libs.kotlin.gradle.plugin.api)
}

gradlePlugin {
    website.set("https://github.com/tech-pw/Semantics")
    vcsUrl.set("https://github.com/tech-pw/Semantics")
    plugins {
        create("semanticPlugin") {
            displayName = "Semantic Gradle Plugin"
            description = "A gradle plugin for generating compose semantics test-tags for composables."
            id = "io.github.tech-pw.auto-test-tag"
            implementationClass = "io.github.techpw.semantic.gradle.SemanticGradlePlugin"
            tags.set(listOf("compose", "test-tags", "semantics", "kotlin"))
        }
    }
}


tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

