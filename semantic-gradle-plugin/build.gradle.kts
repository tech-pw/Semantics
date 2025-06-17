plugins {
    `java-library`
    `java-gradle-plugin`
    `maven-publish`
    kotlin("kapt")
    alias(libs.plugins.jetbrains.kotlin.jvm)
}

group = "io.github.farhazulmullick"
version = "1.0.0"
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

            artifact(tasks["sourcesJar"])
            artifactId = mArtifactId

            pom {
                name.set("Semantic Gradle Plugin")
                description.set("A Gradle plugin for semantic")
                url.set("https://github.com/farhazulmullick-pw/semantic-plugin")

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

        // Optionally add a custom local directory repository
        maven {
            name = "projectLocalRepo"
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}

dependencies {
    implementation("com.google.auto.service:auto-service:1.1.1")
    kapt("com.google.auto.service:auto-service:1.1.1")
    implementation(libs.kotlin.gradle.plugin.api)
}

gradlePlugin {
    plugins {
        create("semanticPlugin") {
            id = "live.pw.compose.semantic.auto-test-tag"
            implementationClass = "live.pw.compose.semantic.gradle.SemanticGradlePlugin"
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

