package io.github.techpw.semantic.compiler

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

@OptIn(ExperimentalCompilerApi::class)
@AutoService(CommandLineProcessor::class)
class SemanticCommandLineProcessor : CommandLineProcessor {
    companion object {
        private const val PLUGIN_ID = "io.github.tech-pw.auto-test-tag"

        val ARG_ENABLED = CompilerConfigurationKey<Boolean>("semantics.enabled")
        val ARG_TEST_TAG_PREFIX = CompilerConfigurationKey<String>("semantics.testTagPrefix")
        val ARG_PACKAGE_NAME = CompilerConfigurationKey<String>("semantics.packageName")
    }

    override val pluginId: String = PLUGIN_ID

    override val pluginOptions: Collection<CliOption> = listOf(
        CliOption(
            optionName = "enabled",
            valueDescription = "true|false",
            description = "Enable semantics plugin"
        ),
        CliOption(
            optionName = "testTagPrefix",
            valueDescription = "string",
            description = "Prefix for generated test tags"
        ),
        CliOption(
            optionName = "packageName",
            valueDescription = "string",
            description = "Package name for generated test tags"
        )
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration
    ) {
        when (option.optionName) {
            "enabled" -> configuration.put(ARG_ENABLED, value.toBoolean())
            "testTagPrefix" -> configuration.put(ARG_TEST_TAG_PREFIX, value)
            "packageName" -> configuration.put(ARG_PACKAGE_NAME, value)
            else -> throw IllegalArgumentException("Unknown option: ${option.optionName}")
        }
    }
}