package io.github.techpw.semantic.compiler

import io.github.techpw.semantic.compiler.transformer.SemanticsIrTransformer
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

class SemanticIrGenerationExtension(
    private val testTagPrefix: String,
    private val packageName: String,
    private val whiteListedUiComponents: Set<String>
) : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.transformChildrenVoid(
            SemanticsIrTransformer(pluginContext, testTagPrefix, packageName, whiteListedUiComponents)
        )
    }
}