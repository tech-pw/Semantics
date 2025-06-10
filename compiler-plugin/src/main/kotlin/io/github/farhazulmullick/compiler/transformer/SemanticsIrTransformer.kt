package io.github.farhazulmullick.compiler.transformer

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.backend.js.utils.nameWithoutExtension
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
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import java.util.LinkedList

class SemanticsIrTransformer(
    private val pluginContext: IrPluginContext,
    private val testTagPrefix: String,
    private val autoGenerate: Boolean
) : IrElementTransformerVoidWithContext() {

    companion object {
        val TAG= "SemanticsIrTransformer"
        private const val COMPOSABLE_ANNOTATION = "androidx.compose.runtime.Composable"
        private const val MODIFIER_CLASS = "androidx.compose.ui.Modifier"
        private const val SEMANTICS_FUNC = "androidx.compose.ui.semantics.semantics"
    }

    private val composableAnnotation = FqName(COMPOSABLE_ANNOTATION)
    private val modifierClass = FqName(MODIFIER_CLASS)
    private val semanticsFunction = FqName(SEMANTICS_FUNC)

    // Map to store instance counts for fully qualified hierarchical tags
    private val instanceCounts = mutableMapOf<String, Int>()
    private val callQueue: LinkedList<IrCall> = LinkedList()

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
        callQueue.addLast(expression) // Push the current call onto the stack
        val transformedCall = super.visitCall(expression) as IrCall

        // Check if this is a Composable function call that might need semantics
        if (shouldAddSemantics(transformedCall)) {
            val expression = addSemanticsModifier(transformedCall)
            println("$TAG After adding Semantics:: ${expression.dump()}")
            callQueue.removeLast() // Pop the call after processing
            return expression
        }

        callQueue.removeLast() // Pop the call if no transformation was applied
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

        // val functionName = call.symbol.owner.name.asString()
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

    private fun IrCall.generateStableTag(): String {
        val calledComposableName = symbol.owner.name.asString()
        val fileName = currentFile?.nameWithoutExtension ?: "UnknownFile"

        val pathComponents = mutableListOf<String>()
        println("$TAG --- Starting generateStableTag for '$calledComposableName' in file '$fileName' ---")
        println("$TAG   Inspecting callStack (from top/deepest to bottom/outermost):")

        // Iterate through the custom callStack (excluding the current call itself)
        // This gives us the hierarchical chain of COMPOSABLE CALLS
        val currentCallIndex = callQueue.indexOf(this) // Find current call in stack
        val relevantCalls = if (currentCallIndex >= 0) {
            // Get parents in order from outermost to innermost
            // The subList range is (fromIndex, toIndex), so to get elements *before* currentCallIndex
            // (i.e., parents), and then reverse them for outermost to innermost order.
            callQueue.toList().subList(0, currentCallIndex)
        } else {
            emptyList()
        }

        for (parentCall in relevantCalls) {
            val parentFunctionName = parentCall.symbol.owner.name.asString()
            val parentIsComposable = parentCall.symbol.owner.hasAnnotation(composableAnnotation)
            val parentOrigin = parentCall.origin
            val parentType = parentCall.type.classFqName

            println("$TAG     Parent Call details: Name='$parentFunctionName' | Type=$parentType | IsComposable=$parentIsComposable | Origin=$parentOrigin")

            // Filter out compiler-generated anonymous call names or internal functions.
            // These are often internal Compose lambdas and should not be part of the stable tag path.
            val isCompilerGeneratedAnonymousCall = parentFunctionName.contains("<anonymous>") ||
                                                   parentFunctionName.contains("<no name provided>") ||
                                                   parentFunctionName.startsWith("invoke") ||
                                                   parentOrigin == IrStatementOrigin.LAMBDA ||
                                                   parentOrigin == IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA

            println("$TAG       Filtering decision: isCompilerGeneratedAnonymousCall=$isCompilerGeneratedAnonymousCall, parentIsComposable=$parentIsComposable")

            if (!isCompilerGeneratedAnonymousCall && parentIsComposable) {
                pathComponents.add(parentFunctionName)
                println("$TAG       *** ADDED to pathComponents: $parentFunctionName ***")
            } else {
                println("$TAG       SKIPPING this parent call. Reason: isCompilerGeneratedAnonymousCall=$isCompilerGeneratedAnonymousCall, parentIsComposable=$parentIsComposable")
            }
        }
        println("$TAG generateStableTag :: pathComponents (before final tag build): $pathComponents")

        val tagBuilder = StringBuilder()
        tagBuilder.append("auto")

        if (testTagPrefix.isNotEmpty()) {
            tagBuilder.append("_").append(testTagPrefix)
        }

        tagBuilder.append("_").append(fileName)

        for (component in pathComponents) {
            tagBuilder.append("_").append(component)
        }

        tagBuilder.append("_").append(calledComposableName)

        val baseTag = tagBuilder.toString()

        val currentCount = instanceCounts.getOrDefault(baseTag, 0) + 1
        instanceCounts[baseTag] = currentCount

        println("$TAG Final generated tag for '$calledComposableName': '$baseTag' count: $currentCount -> '$baseTag'_'$currentCount'")
        println("$TAG --- End generateStableTag for '$calledComposableName' ---")
        return "${baseTag}_${currentCount}"
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
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = functionType,
            function = lambdaFun,
            origin = IrStatementOrigin.LAMBDA
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