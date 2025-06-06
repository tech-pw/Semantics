package io.github.farhazulmullick.compiler.transformer

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.FirIncompatiblePluginAPI
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrPropertySymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class SemanticsIrTransformer(
    private val pluginContext: IrPluginContext,
    private val testTagPrefix: String,
    private val autoGenerate: Boolean
) : IrElementTransformerVoidWithContext() {

    private val composableAnnotation = FqName("androidx.compose.runtime.Composable")
    private val modifierClass = FqName("androidx.compose.ui.Modifier")
    private val semanticsFunction = FqName("androidx.compose.ui.semantics.semantics")

    override fun visitFunctionNew(declaration: IrFunction): IrStatement {
        // Only process Composable functions
        if (!declaration.hasAnnotation(composableAnnotation)) {
            return super.visitFunctionNew(declaration)
        }

        // Transform the function body
        declaration.transformChildrenVoid()

        return declaration
    }

    override fun visitCall(expression: IrCall): IrExpression {
        val transformedCall = super.visitCall(expression) as IrCall

        // Check if this is a Composable function call that might need semantics
        if (shouldAddSemantics(transformedCall)) {
            return addSemanticsModifier(transformedCall)
        }

        return transformedCall
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun shouldAddSemantics(call: IrCall): Boolean {
        // Check if it's a Composable function
        val function = call.symbol.owner
        if (!function.hasAnnotation(composableAnnotation)) return false

        // Check if it already has a Modifier parameter with semantics
        if (hasExistingSemantics(call)) return false

        // Check if it's a UI component that should have semantics
        return isUiComponent(function)
    }

    private fun hasExistingSemantics(call: IrCall): Boolean {
        // Look for existing modifier parameter with semantics
        for (i in 0 until call.valueArgumentsCount) {
            val arg = call.getValueArgument(i)
            if (arg != null && hasSemantics(arg)) {
                return true
            }
        }
        return false
    }

    private fun hasSemantics(expression: IrExpression): Boolean {
        // Recursively check if expression contains semantics call
        when (expression) {
            is IrCall -> {
                if (expression.symbol.owner.kotlinFqName == semanticsFunction) {
                    return true
                }
                // Check arguments recursively
                for (i in 0 until expression.valueArgumentsCount) {
                    val arg = expression.getValueArgument(i)
                    if (arg != null && hasSemantics(arg)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun isUiComponent(function: IrFunction): Boolean {
        val functionName = function.name.asString()
        val commonUiComponents = setOf(
            "Button", "Text", "TextField", "Image", "Icon", "Card",
            "Surface", "Box", "Row", "Column", "LazyColumn", "LazyRow"
        )
        return commonUiComponents.any { functionName.contains(it, ignoreCase = true) }
    }

    private fun addSemanticsModifier(call: IrCall): IrExpression {
        // Find the modifier parameter
        val modifierParamIndex = findModifierParameter(call)
        if (modifierParamIndex == -1) return call

        val functionName = call.symbol.owner.name.asString()
        val testTag = generateTestTag(functionName)

        // Create the semantics modifier
        val semanticsModifier = createSemanticsModifier(call, testTag)

        // Update the call with the new modifier
        return updateCallWithModifier(call, modifierParamIndex, semanticsModifier)
    }

    private fun findModifierParameter(call: IrCall): Int {
        val function = call.symbol.owner
        for (i in 0 until function.valueParameters.size) {
            val param = function.valueParameters[i]
            if (param.type.classFqName == modifierClass) {
                return i
            }
        }
        return -1
    }

    private fun generateTestTag(functionName: String): String {
        val baseTag = if (testTagPrefix.isNotEmpty()) {
            "${testTagPrefix}_${functionName.lowercase()}_test_tag"
        } else {
            "${functionName.lowercase()}_test_tag"
        }
        return baseTag
    }

    private fun createSemanticsModifier(call: IrCall, testTag: String): IrExpression {
        return pluginContext.irBuiltIns.createIrBuilder(call.symbol).run {
            // Create: Modifier.semantics { testTagsAsResourceId = true; testTag = "..." }
            irCall(getSemanticsFunction()).apply {
                // Receiver (Modifier)
                extensionReceiver = irGetObjectValue(
                    type = getModifierCompanion().defaultType,
                    classSymbol = getModifierCompanion()
                )

                // Lambda parameter
                putValueArgument(0, createSemanticsLambda(testTag))
            }
        }
    }

    @OptIn(FirIncompatiblePluginAPI::class)
    private fun createSemanticsLambda(testTag: String): IrExpression {
        val unitType = pluginContext.irBuiltIns.unitType
        val semanticsPropertyReceiverClass = getSemanticsPropertyReceiverClass()
        val functionType: IrSimpleType = pluginContext.irBuiltIns.functionN(1).typeWith(
            semanticsPropertyReceiverClass.defaultType,
            unitType
        )

        val irFactory = pluginContext.irFactory
        val lambdaFun = irFactory.buildFun {
            name = Name.special("<anonymous>")
            returnType = unitType
            visibility = DescriptorVisibilities.LOCAL
            origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
        }.apply {
            // Add receiver parameter
            val receiverParam = addValueParameter {
                name = Name.identifier("receiver")
                type = semanticsPropertyReceiverClass.defaultType
                origin = IrDeclarationOrigin.DEFINED
            }

            // Lambda body
            body = pluginContext.irBuiltIns.createIrBuilder(symbol).irBlockBody {
                // Set testTagsAsResourceId = true
                +irCall(getTestTagsAsResourceIdField().owner.setter!!).apply {
                    dispatchReceiver = irGet(receiverParam)
                    putValueArgument(0, irBoolean(true))
                }
                // Set testTag = testTag
                +irCall(getTestTagField().owner.setter!!).apply {
                    dispatchReceiver = irGet(receiverParam)
                    putValueArgument(0, irString(testTag))
                }
            }
        }

        return IrFunctionExpressionImpl(
            UNDEFINED_OFFSET, UNDEFINED_OFFSET,
            functionType,
            lambdaFun,
            IrStatementOrigin.LAMBDA
        )
    }

    private fun updateCallWithModifier(
        call: IrCall,
        modifierParamIndex: Int,
        newModifier: IrExpression
    ): IrExpression {
        val existingModifier = call.getValueArgument(modifierParamIndex)

        val combinedModifier = if (existingModifier != null) {
            // Chain with existing modifier: existing Modifier.then(newModifier)
            pluginContext.irBuiltIns.createIrBuilder(call.symbol).run {
                irCall(getModifierThenFunction()).apply {
                    extensionReceiver = existingModifier
                    putValueArgument(0, newModifier)
                }
            }
        } else {
            newModifier
        }

        // Create new call with updated modifier
        return call.copyWithNewModifier(modifierParamIndex, combinedModifier)
    }

    private fun IrCall.copyWithNewModifier(
        modifierParamIndex: Int,
        newModifier: IrExpression
    ): IrCall {
        return pluginContext.irBuiltIns.createIrBuilder(this.symbol).run {
            irCall(this@copyWithNewModifier.symbol).apply {
                // Copy all arguments
                for (i in 0 until this@copyWithNewModifier.valueArgumentsCount) {
                    if (i == modifierParamIndex) {
                        putValueArgument(i, newModifier)
                    } else {
                        putValueArgument(i, this@copyWithNewModifier.getValueArgument(i))
                    }
                }

                // Copy receivers
                dispatchReceiver = this@copyWithNewModifier.dispatchReceiver
                extensionReceiver = this@copyWithNewModifier.extensionReceiver
            }
        }
    }

    // Helper functions to get symbols (these would need to be implemented based on your specific setup)

    private fun getSemanticsPropertyReceiverClass(): IrClassSymbol {
        val packageName = FqName("androidx.compose.ui.semantics")
        val className = Name.identifier("SemanticsPropertyReceiver")
        val classId = ClassId(packageName, className)

        return pluginContext.referenceClass(classId)
            ?: error("Could not find SemanticsPropertyReceiver class")
    }

    private fun getSemanticsFunction(): IrSimpleFunctionSymbol {
        val packageName = FqName("androidx.compose.ui.semantics")
        val callableName = Name.identifier("semantics")
        val callableId = CallableId(packageName, callableName)

        return pluginContext.referenceFunctions(callableId).firstOrNull()
            ?: error("Could not find semantics function: $semanticsFunction")
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun getModifierCompanion(): IrClassSymbol {
        val modifierClassId = ClassId(
            packageFqName = FqName("androidx.compose.ui"),
            relativeClassName = FqName("Modifier"),
            isLocal = false
        )

        val modifierClassSymbol = pluginContext.referenceClass(modifierClassId)
            ?: error("Modifier class not found")

        return modifierClassSymbol
    }

    private fun getModifierThenFunction(): IrSimpleFunctionSymbol {
        // Modifier.then()
        val modifierClassId = ClassId(
            packageFqName = FqName("androidx.compose.ui"),
            relativeClassName = FqName("Modifier"),
            isLocal = false
        )

        val modifierClassSymbol = pluginContext.referenceClass(modifierClassId)
            ?: error("Modifier class not found")

        val modifierCompanion: IrClass = modifierClassSymbol.owner.declarations
            .filterIsInstance<IrClass>()
            .firstOrNull { it.isCompanion }
            ?: error("Modifier.Companion not found")

        // Step 3: Find the `then` function inside the companion
        val thenFunction = modifierCompanion.declarations
            .filterIsInstance<IrSimpleFunction>()
            .firstOrNull { it.name.asString() == "then" }
            ?: error("Modifier.Companion.then() function not found")

        return thenFunction.symbol
    }

    private fun getTestTagsAsResourceIdField(): IrPropertySymbol {
        val packageName = FqName("androidx.compose.ui.semantics")
        val callableName = Name.identifier("testTagsAsResourceId")
        val callableId = CallableId(packageName, callableName)

        return pluginContext.referenceProperties(callableId).firstOrNull()
            ?: error("Could not find testTagsAsResourceId property")
    }

    private fun getTestTagField(): IrPropertySymbol {
        val packageName = FqName("androidx.compose.ui.semantics")
        val callableName = Name.identifier("testTag")
        val callableId = CallableId(packageName, callableName)

        return pluginContext.referenceProperties(callableId).firstOrNull()
            ?: error("Could not find testTag property")
    }
}