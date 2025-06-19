package live.pw.compose.semantic.gradle

import com.google.auto.service.AutoService
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

@AutoService(KotlinCompilerPluginSupportPlugin::class)
class SemanticGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {
        target.extensions.create("semanticsConfig", SemanticsExtension::class.java)
    }
    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun getCompilerPluginId(): String = "io.github.tech-pw.compose-test-tag"

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = "com.github.tech-pw",
        artifactId = "semantic-compiler-plugin",  // Changed to compiler plugin
        version = "1.0.0-alpha01"
    )

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val extension = project.extensions.findByType(SemanticsExtension::class.java) ?:
        throw IllegalStateException("SemanticsExtension not found in project ${project.name}")

        return project.provider {
            listOf(
                SubpluginOption(key = "enabled", value = extension.enabled.toString()),
                SubpluginOption(key = "testTagPrefix", value = extension.testTagPrefix),
                SubpluginOption(key = "autoGenerate", value = extension.autoGenerate.toString()),
                SubpluginOption(key = "packageName", value = extension.packageName.toString())
            )
        }
    }
}

open class SemanticsExtension {
    var enabled: Boolean = true
    var testTagPrefix: String = ""
    var autoGenerate: Boolean = true
    var packageName: String = ""
}