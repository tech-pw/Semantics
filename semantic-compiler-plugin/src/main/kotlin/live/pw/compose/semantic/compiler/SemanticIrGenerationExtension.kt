package live.pw.compose.semantic.compiler

import live.pw.compose.semantic.compiler.transformer.SemanticsIrTransformer
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

class SemanticIrGenerationExtension(
    private val testTagPrefix: String,
    private val autoGenerate: Boolean,
    private val packageName: String
) : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.transformChildrenVoid(
            SemanticsIrTransformer(pluginContext, testTagPrefix, autoGenerate, packageName)
        )
    }
}