package io.github.farhazul.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import kotlin.jvm.java

class SemanticsGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {
        target.extensions.create("semanticsConfig", SemanticsExtension::class.java)
    }
    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun getCompilerPluginId(): String = "io.github.farhazulmullick.sementics-gradle-plugin"

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = "io.github.farhazulmullick",
        artifactId = "sementics-gradle-plugin",
        version = "1.0.0"
    )

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val extension = project.extensions.findByType(SemanticsExtension::class.java) ?: SemanticsExtension()

        return project.provider {
            listOf(
                SubpluginOption(key = "enabled", value = extension.enabled.toString()),
                SubpluginOption(key = "testTagPrefix", value = extension.testTagPrefix),
                SubpluginOption(key = "autoGenerate", value = extension.autoGenerate.toString())
            )
        }
    }
}

open class SemanticsExtension {
    var enabled: Boolean = true
    var testTagPrefix: String = ""
    var autoGenerate: Boolean = true
}