package io.github.farhazulmullick.compiler

import io.github.farhazulmullick.compiler.transformer.SemanticsIrTransformer
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

class SemanticsIrGenerationExtension(
    private val testTagPrefix: String,
    private val autoGenerate: Boolean
) : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.transformChildrenVoid(
            SemanticsIrTransformer(pluginContext, testTagPrefix, autoGenerate)
        )
    }
}