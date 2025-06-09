package io.github.farhazulmullick.compiler.transformer

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
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
import org.jetbrains.kotlin.ir.declarations.name
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
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

object MetaDataManager {
    val fileComposableCounters = HashMap<String, HashMap<String, Int>>()
}

class SemanticsIrTransformer(
    private val pluginContext: IrPluginContext,
    private val testTagPrefix: String,
    private val autoGenerate: Boolean
) : IrElementTransformerVoidWithContext() {

    companion object {
        val TAG= "SemanticsIrTransformer"
    }

    private val composableAnnotation = FqName("androidx.compose.runtime.Composable")
    private val modifierClass = FqName("androidx.compose.ui.Modifier")
    private val modifierCompanionClass = FqName("androidx.compose.ui.Modifier")
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
        //println("$TAG -> ${transformedCall.dump()}")
        if (shouldAddSemantics(transformedCall)) {
            val expression: IrExpression = addSemanticsModifier(transformedCall)
            println("$TAG After adding Semantics:: ${expression.dump()}")
            return expression
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
        val parentModifier: IrExpression? = call.getValueArgument(modifierParamIndex)

        val testTag: String = call.generateStableTag()

        // Create the semantics modifier
        val semanticsModifier = createSemanticsModifier(call, testTag, parentModifier)

        // Update the call with the new modifier
        return call.copyWithNewModifier(modifierParamIndex, semanticsModifier)
    }

    private fun findModifierParameter(call: IrCall): Int {
        val function: IrSimpleFunction = call.symbol.owner
        for (i in 0 until function.valueParameters.size) {
            val param = function.valueParameters[i]
            if (param.type.classFqName == modifierClass) {
                println("$TAG findModifierParameter, found at index ${i}")
                return i
            }
        }
        println("$TAG findModifierParameter, Not found")
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
    private fun IrCall.generateStableTag(): String {
        println("$TAG generateStableTag :: fileComposableCounters = ${MetaDataManager.fileComposableCounters.hashCode()}")
        val fileName = currentFile?.name ?: "UnknownFile"
        val functionName = symbol.owner.name.asString()
        val fileMap = MetaDataManager.fileComposableCounters.getOrPut(fileName) { HashMap() }
        val count = (fileMap[functionName] ?: 0) + 1
        fileMap[functionName] = count
        return "auto_${testTagPrefix}_${fileName}_${functionName}_$count"
    }

    private fun createSemanticsModifier(
        call: IrCall,
        testTag: String,
        parentModifier: IrExpression? // parent modifier for chaining.
    ): IrExpression {
        val parentModifierDump: String? = parentModifier?.dump()
        println("$TAG createSemanticsModifier :: parentModifierDump $parentModifierDump")
        val baseModifier: IrExpression = if (
            parentModifier == null ||
            parentModifierDump?.contains("DEFAULT_VALUE") == true ||
            parentModifierDump?.contains("value=null") == true
        ) {
            pluginContext.irBuiltIns.createIrBuilder(call.symbol).run {
                irGetObjectValue(
                    type = getModifierCompanionObj().defaultType,
                    classSymbol = getModifierCompanionObj()
                )
            }
        } else parentModifier

        println("$TAG createSemanticsModifier :: baseModifierDump ${baseModifier.dump()}")

        return pluginContext.irBuiltIns.createIrBuilder(call.symbol).run {
            // Create: Modifier.semantics { testTagsAsResourceId = true; testTag = "..." }
            irCall(getSemanticsFunction()).apply {
                // Use Modifier.Companion as receiver
                extensionReceiver = baseModifier
                // Lambda parameter
                putValueArgument(0, irBoolean(false)) // mergeDescendants = false
                putValueArgument(1, createSemanticsLambda(testTag))
            }
        }
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun createSemanticsLambda(testTag: String): IrExpression {
        val unitType = pluginContext.irBuiltIns.unitType
        val semanticsPropertyReceiverClass = getSemanticsPropertyReceiverClass()
        val functionType: IrSimpleType = pluginContext.irBuiltIns.functionN(1).typeWith(
            semanticsPropertyReceiverClass.defaultType,
            unitType
        )

        val irFactory = pluginContext.irFactory

        // Create lambda function with proper parent setup
        val lambdaFun = irFactory.buildFun {
            name = Name.special("<anonymous>")
            returnType = unitType
            visibility = DescriptorVisibilities.LOCAL
            origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
        }.apply {
            // Set parent to current context with proper origin
            parent = currentClass?.irElement as? IrClass ?: currentFunction?.irElement as IrFunction

            // Add receiver parameter with correct name and origin
            val receiverParam = addValueParameter {
                name = Name.identifier("receiver")
                type = semanticsPropertyReceiverClass.defaultType
                origin = IrDeclarationOrigin.DEFINED
            }
            receiverParam.parent = this

            // Lambda body with proper origin
            body = pluginContext.irBuiltIns.createIrBuilder(symbol).irBlockBody {
                // Set testTagsAsResourceId = true
                +irCall(getTestTagsAsResourceIdField().owner.setter!!).apply {
                    extensionReceiver = irGet(receiverParam)
                    putValueArgument(0, irBoolean(true))
                }
                // Set testTag = testTag
                +irCall(getTestTagField().owner.setter!!).apply {
                    extensionReceiver = irGet(receiverParam)
                    putValueArgument(0, irString(testTag))
                }
            }

            patchDeclarationParents()
        }

        // Create the function expression with proper origin
        val functionExpression = IrFunctionExpressionImpl(
            UNDEFINED_OFFSET, UNDEFINED_OFFSET,
            functionType,
            lambdaFun,
            IrStatementOrigin.LAMBDA
        )

        // Set proper parent for function expression
        val parentDeclaration = when {
            currentClass?.irElement is IrClass -> currentClass!!.irElement as IrClass
            currentFunction?.irElement is IrFunction -> currentFunction!!.irElement as IrFunction
            else -> null // Do not assign parent if not a class or function
        }
        if (parentDeclaration != null) {
            lambdaFun.parent = parentDeclaration
        }
        return functionExpression
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
    private fun getModifierClass(): IrClassSymbol {
        val modifierClassId = ClassId(
            packageFqName = FqName("androidx.compose.ui"),
            relativeClassName = FqName("Modifier"),
            isLocal = false
        )

        val modifierClassSymbol = pluginContext.referenceClass(modifierClassId)
            ?: error("Modifier class not found")

        return modifierClassSymbol
    }

    private fun getModifierCompanionObj(): IrClassSymbol {
        val modifierClassSymbol: IrClassSymbol = getModifierClass()
        val modifierCompanion: IrClass = modifierClassSymbol.owner.declarations
            .filterIsInstance<IrClass>()
            .firstOrNull { it.isCompanion }
            ?: error("Modifier.Companion not found")

        return modifierCompanion.symbol
    }

    private fun getModifierThenFunction(): IrSimpleFunctionSymbol {
        // Modifier.then()
        // Step 3: Find the `then` function inside the companion
        val thenFunction = getModifierCompanionObj().owner.declarations
            .filterIsInstance<IrSimpleFunction>()
            .firstOrNull { it.name.asString() == "then" }
            ?: error("Modifier.Companion.then() function not found")

        println("$TAG :: foundThenFunction ${thenFunction.dump()}")

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