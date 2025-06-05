import com.google.auto.service.AutoService
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration

@OptIn(ExperimentalCompilerApi::class)
@AutoService(CompilerPluginRegistrar::class)
class SemanticsComponentRegistrar : CompilerPluginRegistrar() {
    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val enabled = configuration.get(ComposeTaggerCommandLineProcessor.ARG_ENABLED, true)
        if (!enabled) return

        val testTagPrefix = configuration.get(ComposeTaggerCommandLineProcessor.ARG_TEST_TAG_PREFIX, "")
        val autoGenerate = configuration.get(ComposeTaggerCommandLineProcessor.ARG_AUTO_GENERATE, true)

        IrGenerationExtension.registerExtension(
            SemanticsIrGenerationExtension(testTagPrefix, autoGenerate)
        )
    }

    override val supportsK2: Boolean = true
}