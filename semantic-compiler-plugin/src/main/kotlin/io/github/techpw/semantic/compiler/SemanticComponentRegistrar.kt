package io.github.techpw.semantic.compiler

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration

@OptIn(ExperimentalCompilerApi::class)
@AutoService(CompilerPluginRegistrar::class)
class SemanticComponentRegistrar : CompilerPluginRegistrar() {
    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val enabled = configuration.get(SemanticCommandLineProcessor.ARG_ENABLED, true)
        if (!enabled) return

        val testTagPrefix = configuration.get(SemanticCommandLineProcessor.ARG_TEST_TAG_PREFIX, "")
        val packageName = configuration.get(SemanticCommandLineProcessor.ARG_PACKAGE_NAME, "")

        IrGenerationExtension.Companion.registerExtension(
            SemanticIrGenerationExtension(testTagPrefix, packageName)
        )
    }

    override val supportsK2: Boolean = true
}