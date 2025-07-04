plugins {
    `java-library`
    id("com.gradle.plugin-publish") version "1.2.1"
    kotlin("kapt")
    alias(libs.plugins.jetbrains.kotlin.jvm)
}

group = "io.github.tech-pw"
version = "1.0.0-alpha03"
val mArtifactId = "semantic-gradle-plugin"

tasks.register("sourcesJar", Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
    dependsOn(tasks.classes)
}

publishing {
    publications {
        create<MavenPublication>("semanticPlugin") {
            from(components["java"])

            artifactId = mArtifactId
            pom {
                name.set("Semantic Gradle Plugin")
                description.set("A Gradle plugin for semantic")
                url.set("https://github.com/tech-pw/Semantics")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("farhazulmullick")
                        name.set("Farhazul Mullick")
                    }
                }
            }
        }
    }

    repositories {
        mavenLocal() // Publishes to the local Maven repository (~/.m2/repository)

        // remote
        maven("https://jitpack.io")
    }
}

dependencies {
    implementation("com.google.auto.service:auto-service:1.1.1")
    kapt("com.google.auto.service:auto-service:1.1.1")
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
            implementationClass = "live.pw.compose.semantic.gradle.SemanticGradlePlugin"
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

