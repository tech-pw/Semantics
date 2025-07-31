package io.github.techpw.semantic.gradle

import com.google.auto.service.AutoService
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

@AutoService(KotlinCompilerPluginSupportPlugin::class)
class SemanticGradlePlugin : KotlinCompilerPluginSupportPlugin {

    companion object {
        const val OPTION_ENABLED = "enabled"
        const val OPTION_TEST_TAG_PREFIX = "testTagPrefix"
        const val OPTION_PACKAGE_NAME = "packageName"
        const val OPTION_WHITELISTED_UI_COMPONENTS = "whiteListedUiComponents"
    }
    override fun apply(target: Project) {
        target.extensions.create("semanticsConfig", SemanticsExtension::class.java)
    }
    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun getCompilerPluginId(): String = "io.github.tech-pw.auto-test-tag"

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = "io.github.tech-pw",
        artifactId = "semantic-compiler-plugin",  // Changed to compiler plugin
        version = "1.1.1"
    )

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val extension = project.extensions.findByType(SemanticsExtension::class.java) ?:
        throw IllegalStateException("SemanticsExtension not found in project ${project.name}")
        val componentsSubplugin = extension.whiteListedUiComponents.map {components ->
            SubpluginOption(
                key = OPTION_WHITELISTED_UI_COMPONENTS,
                value = components
            )
        }
        return project.provider {
            listOf(
                SubpluginOption(key = OPTION_ENABLED, value = extension.enabled.toString()),
                SubpluginOption(key = OPTION_TEST_TAG_PREFIX, value = extension.testTagPrefix),
                SubpluginOption(key = OPTION_PACKAGE_NAME, value = extension.packageName.toString()),
            ) + componentsSubplugin
        }
    }
}

open class SemanticsExtension {
    /**
     *  @property enabled Enables or disables the semantics plugin. Default is true.
     */
    var enabled: Boolean = true

    /**
     *  @property testTagPrefix Prefix for generated test tags. Default is an empty string.
     */
    var testTagPrefix: String = ""

    /**
     * @property packageName Package name for generated test tags. Default is an empty string.
     */
    var packageName: String = ""

    /**
     * @property whiteListedUiComponents List of UI component to generate Ids.
     * By default it is empty, meaning all components are whitelisted. Ids for all components will be generated.
     * Example: listOf("Button", "Text", "Image")
     */
    var whiteListedUiComponents: List<String> = emptyList()
}