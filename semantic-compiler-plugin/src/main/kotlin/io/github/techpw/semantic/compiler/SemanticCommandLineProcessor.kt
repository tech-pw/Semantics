package io.github.techpw.semantic.compiler

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

/**
 * Processes command-line options for the semantic plugin.
 *
 * This class is responsible for defining and handling the command-line arguments
 * that can be passed to the Kotlin compiler when this plugin is active.
 * It uses the `AutoService` annotation to be discoverable by the Kotlin compiler.
 *
 * The plugin supports the following options:
 * - `enabled`: (true|false) Enables or disables the semantic plugin.
 * - `testTagPrefix`: (string) Sets a prefix for automatically generated test tags.
 * - `packageName`: (string) Specifies the package name to be used for generated test tags.
 * - `whiteListedUiComponents`: (string) A comma-separated list of UI components that should be considered
 *   for automatic test tag generation. This option can be specified multiple times.
 *
 *   @author Farhazul Mullick
 */
@OptIn(ExperimentalCompilerApi::class)
@AutoService(CommandLineProcessor::class)
class SemanticCommandLineProcessor : CommandLineProcessor {
    companion object {
        private const val PLUGIN_ID = "io.github.tech-pw.auto-test-tag"

        val ARG_ENABLED = CompilerConfigurationKey<Boolean>("enabled")
        val ARG_TEST_TAG_PREFIX = CompilerConfigurationKey<String>("testTagPrefix")
        val ARG_PACKAGE_NAME = CompilerConfigurationKey<String>("packageName")
        val ARG_WHITELISTED_UI_COMPONENTS = CompilerConfigurationKey<Set<String>>("whiteListedUiComponents")


        const val OPTION_ENABLED = "enabled"
        const val OPTION_TEST_TAG_PREFIX = "testTagPrefix"
        const val OPTION_PACKAGE_NAME = "packageName"
        const val OPTION_WHITELISTED_UI_COMPONENTS = "whiteListedUiComponents"

    }

    override val pluginId: String = PLUGIN_ID

    override val pluginOptions: Collection<CliOption> = listOf(
        CliOption(
            optionName = OPTION_ENABLED,
            valueDescription = "true|false",
            description = "Enable semantics plugin"
        ),
        CliOption(
            optionName = OPTION_TEST_TAG_PREFIX,
            valueDescription = "string",
            description = "Prefix for generated test tags"
        ),
        CliOption(
            optionName = OPTION_PACKAGE_NAME,
            valueDescription = "string",
            description = "Package name for generated test tags"
        ),
        CliOption(
            optionName = OPTION_WHITELISTED_UI_COMPONENTS,
            valueDescription = "string",
            description = "Comma-separated list of UI components",
            allowMultipleOccurrences = true,
            required = false
        )
    )

    private val whiteListedComponents = mutableSetOf<String>()
    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration
    ) {
        when (option.optionName) {
            OPTION_ENABLED -> configuration.put(ARG_ENABLED, value.toBoolean())
            OPTION_TEST_TAG_PREFIX -> configuration.put(ARG_TEST_TAG_PREFIX, value)
            OPTION_PACKAGE_NAME -> configuration.put(ARG_PACKAGE_NAME, value)
            OPTION_WHITELISTED_UI_COMPONENTS -> {
                whiteListedComponents.add(value)
                configuration.put(ARG_WHITELISTED_UI_COMPONENTS, whiteListedComponents)
            }
            else -> throw IllegalArgumentException("Unknown option: ${option.optionName}")
        }
    }
}