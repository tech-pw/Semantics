package io.github.techpw.semantic.compiler.transformer

import io.github.techpw.semantic.compiler.KtxNameConventions
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.backend.js.utils.nameWithoutExtension
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irBoolean
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObjectValue
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrPropertySymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isFunction
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import java.util.LinkedList
import kotlin.math.ceil

/**
 * An [IrElementTransformerVoidWithContext] that transforms the IR of Composable functions
 * to automatically add semantics modifiers with test tags.
 *
 * This transformer aims to simplify UI testing by:
 * 1. Identifying Composable function calls.
 * 2. Checking if they should have semantics (based on a whitelist or if they are common UI components).
 * 3. Ensuring they don't already have semantics defined.
 * 4. If conditions are met, it injects a `Modifier.semantics` call, setting `testTagsAsResourceId = true`
 *    and a generated `testTag`.
 *
 * The generated `testTag` is designed to be stable and unique, incorporating:
 * - The application's package name.
 * - A configurable prefix (`testTagPrefix`).
 * - The filename where the Composable is located.
 * - A hierarchical path of parent Composable function names.
 * - A hash of the Composable's parameters to differentiate instances.
 *
 * This allows test automation frameworks to reliably locate UI elements.
 *
 * Key operations:
 * - **`visitFunctionNew`**: Filters for functions annotated with `@Composable`.
 * - **`visitCall`**: Processes calls to Composable functions.
 *   - Uses a `callQueue` to track the hierarchy of Composable calls for tag generation.
 *   - **`shouldAddSemantics`**: Determines if a Composable call needs semantics added.
 *     - Checks for `@Composable` annotation.
 *     - Checks if semantics are already present (`hasExistingSemantics`).
 *     - Checks if the Composable is in the `whiteListedUiComponents` or if the whitelist is empty (meaning all Composables are considered).
 *   - **`addSemanticsModifier`**: Constructs and injects the `Modifier.semantics` call.
 *     - Handles cases where the Composable function uses default parameter values for its modifier.
 *     - **`generateStableTag`**: Creates the unique test tag string.
 *     - **`createSemanticsLambda`**: Builds the IR for the lambda function passed to `Modifier.semantics`.
 *
 * Handling of Default Parameters:
 * The transformer correctly identifies if a Composable is using a default `Modifier` (e.g., `Modifier = Modifier`).
 *
 * @author Farhazul Mullick
 */
class SemanticsIrTransformer(
    private val pluginContext: IrPluginContext,
    private val testTagPrefix: String,
    private val packageName: String,
    private val whiteListedUiComponents: Set<String>
) : IrElementTransformerVoidWithContext() {

    companion object {
        val TAG= "SemanticsIrTransformer"
        private const val COMPOSABLE_ANNOTATION = "androidx.compose.runtime.Composable"
        private const val MODIFIER_CLASS = "androidx.compose.ui.Modifier"
        private const val SEMANTICS_FUNC = "androidx.compose.ui.semantics.semantics"
        const val BITS_PER_INT = 31
        const val SLOTS_PER_INT = 10
    }

    private val composableAnnotation = FqName(COMPOSABLE_ANNOTATION)
    private val modifierClass = FqName(MODIFIER_CLASS)
    private val semanticsFunction = FqName(SEMANTICS_FUNC)

    // Map to store instance counts for fully qualified hierarchical tags
    private val instanceCounts = mutableMapOf<String, Int>()
    private val callQueue: LinkedList<IrCall> = LinkedList()

    /**
     * Visits a new function declaration.
     *
     * This method is overridden to process only Composable functions. If the function
     * is not annotated with `@Composable`, it delegates to the superclass implementation.
     * Otherwise, it transforms the children of the function declaration.
     *
     * @param declaration The [IrFunction] declaration to visit.
     * @return The transformed [IrStatement], which is the original declaration after
     *         its children have been transformed, or the result of the superclass
     *         call if the function is not Composable.
     */
    override fun visitFunctionNew(declaration: IrFunction): IrStatement {
        // Only process Composable functions
        if (!declaration.hasAnnotation(composableAnnotation)) {
            return super.visitFunctionNew(declaration)
        }
        // Transform the function body
        declaration.transformChildrenVoid()

        return declaration
    }

    /**
     * Visits an [IrCall] expression.
     *
     * This method is the core of the transformation. It performs the following steps:
     * 1. Pushes the current call onto a `callQueue`. This queue is used to reconstruct
     *    the call hierarchy for generating unique test tags.
     * 2. Calls `super.visitCall(expression)` to allow other transformations to proceed.
     * 3. Checks if the `transformedCall` (the result of `super.visitCall`) is a Composable
     *    function call that `shouldAddSemantics`.
     * 4. If semantics should be added:
     *    a. Calls `addSemanticsModifier` to create and inject a new Modifier that includes
     *       the semantics information (specifically, the `testTag`).
     *    b. Pops the current call from the `callQueue` after processing.
     *    c. Returns the new expression with the added semantics.
     * 5. If no transformation was applied (either not a Composable or already has semantics):
     *    a. Pops the current call from the `callQueue`.
     *    b. Returns the `transformedCall` as is.
     *
     * The `callQueue` is crucial for understanding the nesting of Composable calls, which
     * allows for the generation of hierarchical and stable test tags.
     *
     * @param expression The [IrCall] expression to visit.
     * @return The transformed [IrExpression], which might be the original expression or a new
     *         one with an added semantics modifier.
     */
    override fun visitCall(expression: IrCall): IrExpression {
        callQueue.addLast(expression) // Push the current call onto the stack
        val transformedCall = super.visitCall(expression) as IrCall

        // Check if this is a Composable function call that might need semantics
        if (shouldAddSemantics(transformedCall)) {
            //println("$TAG Before adding Semantics:: ${expression.dump()}")
            val expression = addSemanticsModifier(transformedCall)
            //println("$TAG After adding Semantics:: ${expression.dump()}")
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
        return isWhiteListedUiComponents(function, whiteListedUiComponents)
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

    /**
     * Checks if a given Composable function is white-listed for adding semantics.
     * The function is considered white-listed if its name is present in the `components` set.
     * If the `components` set is empty, all Composable functions are considered white-listed.
     *
     * @param function The [IrFunction] to check. This function is expected to be a Composable.
     * @param components A set of strings representing the names of white-listed UI components.
     *                   If this set is empty, the function will always return `true`.
     * @return `true` if the function is white-listed or if the `components` set is empty,
     *         `false` otherwise.
     *
     * @author Farhazul Mullick
     */
    private fun isWhiteListedUiComponents(function: IrFunction, components: Set<String>): Boolean {
        val functionName = function.name.asString()
        return components.isEmpty() || components.contains(functionName)
    }

    private fun addSemanticsModifier(call: IrCall): IrExpression {
        val modifierParamIndex = findModifierParameter(call)
        if (modifierParamIndex == -1) return call

        val ownerFn = call.symbol.owner
        val defaultInfo = getDefaultParameterInfo(call, ownerFn)
        val isUsingDefault = isParameterUsingDefault(modifierParamIndex, defaultInfo)

        println("$TAG function :: ${ownerFn.name.asString()} has default $isUsingDefault")

        val parentModifier = call.getValueArgument(modifierParamIndex)
        val testTag = call.generateStableTag()
        val semanticsModifier = createSemanticsModifier(call, testTag, parentModifier, isUsingDefault)

        val newCall = call.copyWithNewModifier(modifierParamIndex, semanticsModifier)

        if (isUsingDefault) {
            updateDefaultParameterMask(newCall, modifierParamIndex, defaultInfo)
        }

        return newCall
    }

    private data class DefaultParameterInfo(
        val hasDefaults: Boolean,
        val numContextParams: Int,
        val numRealValueParams: Int,
        val defaultMasks: List<Int>,
        val defaultArgIndex: Int
    )

    val IrFunction.thisParamCount
        get() = contextReceiverParametersCount +
                (if (dispatchReceiverParameter != null) 1 else 0) +
                (if (extensionReceiverParameter != null) 1 else 0)

    /**
     * Retrieves information about default parameters for a given function call.
     *
     * This function analyzes the `ownerFn` (the function being called) to determine
     * if it utilizes default parameter values. If so, it extracts information such as:
     * - The number of context parameters.
     * - The number of "real" value parameters (excluding compiler-generated ones).
     * - The default parameter masks (bitmasks indicating which parameters are using defaults).
     * - The index where default argument information (masks and handler) starts in the function's value parameters.
     *
     * The default parameter mechanism in Kotlin IR involves:
     * 1. **`$default` parameter:** A synthetic parameter indicating the presence of default values.
     * 2. **Default masks:** Integer parameters (often named `$mask0`, `$mask1`, etc.) where each bit
     *    corresponds to a parameter. A '1' indicates the parameter uses its default value.
     * 3. **Default handler:** A synthetic parameter (often named `$handler`) which is a function
     *    responsible for providing the default values.
     *
     * This function parses these elements from the `call` and `ownerFn` to construct
     * a [DefaultParameterInfo] object.
     *
     * @param call The [IrCall] expression representing the function invocation.
     * @param ownerFn The [IrFunction] declaration of the function being called.
     * @return A [DefaultParameterInfo] object containing details about the default parameters.
     *         If the function has no default parameters, a [DefaultParameterInfo] with `hasDefaults = false`
     *         and other fields set to zero/empty is returned.
     */
    private fun getDefaultParameterInfo(call: IrCall, ownerFn: IrFunction): DefaultParameterInfo {
        val hasDefaults = ownerFn.valueParameters.any { it.name == KtxNameConventions.DEFAULT_PARAMETER }
        println("$TAG Checking defaults for function '${ownerFn.name.asString()}': hasDefaults=$hasDefaults")
        if (!hasDefaults) {
            return DefaultParameterInfo(false, 0, 0, emptyList(), 0)
        }

        val numContextParams = ownerFn.contextReceiverParametersCount
        val numRealValueParams = ownerFn.valueParameters.indexOfLast { !it.name.asString().startsWith('$') } + 1 - numContextParams
        val numDefaults = defaultParamCount(numContextParams + numRealValueParams)
        val defaultArgIndex = numContextParams + numRealValueParams + 1 + changedParamCount(numRealValueParams, ownerFn.thisParamCount)
        val defaultArgs = (defaultArgIndex until ownerFn.valueParameters.size).map { call.getValueArgument(it) }
        val defaultMasks = defaultArgs.mapNotNull { arg ->
            when (arg) {
                is IrConst -> {
                    when (val value = arg.value) {
                        is Int -> value
                        is Long -> value.toInt()
                        else -> {
                            println("$TAG Unexpected default mask value type: ${value?.javaClass?.name}")
                            null
                        }
                    }
                }
                else -> {
                    println("$TAG Unexpected default mask type: ${arg?.javaClass?.name}")
                    null
                }
            }
        }

        if (defaultMasks.size != numDefaults) {
            println("$TAG Warning: Expected $numDefaults default masks but got ${defaultMasks.size}")
        }

        return DefaultParameterInfo(
            hasDefaults = true,
            numContextParams = numContextParams,
            numRealValueParams = numRealValueParams,
            defaultMasks = defaultMasks,
            defaultArgIndex = defaultArgIndex
        )
    }

    /**
     * Checks if a parameter at a given index is using its default value.
     * This is determined by looking at the default parameter masks provided by the Kotlin compiler.
     *
     * Default parameters in Kotlin are handled using an integer mask. Each bit in the mask
     * corresponds to a parameter. If the bit is set, it means the default value for that
     * parameter is used.
     *
     * For example, if a function has parameters (a, b, c, d, e) and parameters `b` and `d`
     * are using their default values, the mask might look like `0...01010` (binary).
     *
     * This function:
     * 1. Calculates which mask integer in the `defaultMasks` list contains the bit for `paramIndex`.
     *    This is `maskIndex`.
     * 2. Calculates the specific bit position within that mask integer for `paramIndex`.
     *    This is `bitIndex`.
     * 3. Retrieves the mask value from `defaultInfo.defaultMasks` at `maskIndex`.
     * 4. Checks if the bit at `bitIndex` in `maskValue` is set.
     *
     * @param paramIndex The 0-based index of the parameter to check. This is the index
     *                   within the function's `valueParameters` list.
     * @param defaultInfo Information about the default parameters of the function, including the masks.
     * @return `true` if the parameter at `paramIndex` is using its default value, `false` otherwise.
     *         Returns `false` if the function doesn't have default parameters, if default masks are empty,
     *         or if the calculated `maskIndex` is out of bounds.
     */
    private fun isParameterUsingDefault(paramIndex: Int, defaultInfo: DefaultParameterInfo): Boolean {
        if (!defaultInfo.hasDefaults || defaultInfo.defaultMasks.isEmpty()) return false

        val bitIndex: Int = defaultsBitIndex(paramIndex)
        val maskIndex: Int = defaultsParamIndex(paramIndex)

        if (maskIndex >= defaultInfo.defaultMasks.size) {
            println("$TAG Warning: Mask index $maskIndex out of bounds for masks size ${defaultInfo.defaultMasks.size}")
            return false
        }

        val maskValue = defaultInfo.defaultMasks[maskIndex]
        println("$TAG maskValue for paramIndex $paramIndex (maskIndex $maskIndex, bitIndex $bitIndex): $maskValue")
        return maskValue and (0b1 shl bitIndex) != 0
    }

    /**
     * Updates the default parameter mask for a function call when a default parameter is overridden.
     *
     * In Kotlin, when a function has default parameters, the compiler generates additional mask parameters
     * (integers) to indicate which parameters are using their default values. Each bit in these masks
     * corresponds to a parameter. If a bit is set, the corresponding parameter uses its default value.
     *
     * This function is called when we programmatically provide a value for a parameter that was
     * originally using its default (e.g., adding a Modifier). We need to update the corresponding
     * mask to reflect that this parameter is no longer using its default value.
     *
     * @param call The [IrCall] expression representing the function call.
     * @param paramIndex The 0-based index of the parameter whose default status is being changed.
     *                   This index is relative to the original value parameters of the function.
     * @param defaultInfo Information about the default parameters of the called function,
     *                    including the original mask values.
     */
    private fun updateDefaultParameterMask(call: IrCall, paramIndex: Int, defaultInfo: DefaultParameterInfo) {
        if (!defaultInfo.hasDefaults || defaultInfo.defaultMasks.isEmpty()) return

        val bitIndex = defaultsBitIndex(paramIndex)
        val maskIndex = defaultsParamIndex(paramIndex)

        if (maskIndex >= defaultInfo.defaultMasks.size) {
            println("$TAG Warning: Cannot update mask at index $maskIndex (out of bounds)")
            return
        }

        val oldMask = defaultInfo.defaultMasks[maskIndex]
        val newMask = oldMask and (0b1 shl bitIndex).inv() // Clear the bit

        val maskArgIndex = defaultInfo.defaultArgIndex + maskIndex
        call.putValueArgument(
            maskArgIndex,
            pluginContext.irBuiltIns.createIrBuilder(call.symbol).irInt(newMask)
        )
    }

    /**
     * Creates a new semantics modifier expression.
     *
     * This function constructs an IR expression that represents a call to the `semantics`
     * modifier function. It takes the original call site, a generated test tag, an optional
     * parent modifier, and a flag indicating if the original modifier was using its default value.
     *
     * If `isUsingDefault` is true or `parentModifier` is null, a new empty `Modifier` instance
     * is created as the base. Otherwise, the provided `parentModifier` is used.
     *
     * The `semantics` function is then called on this base modifier, with `mergeDescendants`
     * set to `false` and a lambda that sets the `testTag` and `testTagsAsResourceId` properties.
     *
     * @param call The original IrCall that is being modified.
     * @param testTag The unique test tag string to be applied.
     * @param parentModifier The existing modifier expression, if any, to chain with.
     *                       If null or `isUsingDefault` is true, an empty Modifier is used.
     * @param isUsingDefault True if the original modifier parameter was using its default value,
     *                       false otherwise. This influences whether a new empty Modifier is created.
     * @return An IrExpression representing the new modifier with semantics applied.
     *
     * @author Farhazul Mullick
     */
    private fun createSemanticsModifier(
        call: IrCall,
        testTag: String,
        parentModifier: IrExpression?,
        isUsingDefault: Boolean
    ): IrExpression {
        val baseModifier = if (isUsingDefault || parentModifier == null) {
            createEmptyModifier(call)
        } else {
            parentModifier
        }

        return pluginContext.irBuiltIns.createIrBuilder(call.symbol).run {
            irCall(getSemanticsFunction()).apply {
                extensionReceiver = baseModifier
                putValueArgument(0, irBoolean(false)) // mergeDescendants = false
                putValueArgument(1, createSemanticsLambda(testTag))
            }
        }
    }

    private fun createEmptyModifier(call: IrCall): IrExpression {
        return pluginContext.irBuiltIns.createIrBuilder(call.symbol).run {
            irGetObjectValue(
                type = getModifierCompanionObj().defaultType,
                classSymbol = getModifierCompanionObj()
            )
        }
    }

    /**
     * Generates a merged string representation (dump) of all parameters of an [IrCall].
     *
     * This function iterates through the value parameters of the called function.
     * For each parameter, it retrieves the corresponding argument from the [IrCall].
     * If an argument exists, its IR dump is appended to a [StringBuilder].
     *
     * Special handling is in place to skip parameters that are composable lambdas
     * (identified by having a FunctionN type and a @Composable annotation),
     * unless they are related to resources (e.g., `androidx.compose.ui.res` or
     * `org.jetbrains.compose.resources`). This is to avoid including large,
     * irrelevant lambda dumps in the generated string, which is typically used
     * for creating stable identifiers or hashes.
     *
     * @return A [StringBuilder] containing the concatenated IR dumps of the relevant parameters.
     */
    private fun IrCall.getMergedIrDumpOfParams(): StringBuilder {
        val builder = StringBuilder()
        val function: IrSimpleFunction = this.symbol.owner
        function.valueParameters.forEachIndexed { index, param: IrValueParameter ->
            // Skip if parameter is a composable lambda (has FunctionN type with @Composable annotation)

            val arg = getValueArgument(index)
            if (arg != null) {
                val dump = arg.dump()
                val isComposer = dump.contains("androidx.compose.runtime.Composer")
                val isResource = dump.contains("androidx.compose.ui.res") ||
                        dump.contains("org.jetbrains.compose.resources")
                if (isComposer && !isResource) {
                    println("$TAG --- Found composable param, skipping param at index $index")
                    // Skip composable lambda parameters that are not resource-related
                } else {
                    println("$TAG --- Parameters at index $index is $dump")
                    builder.append(dump)
                }
            }
        }
        println("$TAG --- Dump for '${function.name} is $builder' ---")
        println("$TAG findTextParameter, Not found")
        return builder
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

    /**
     * Retrieves the call hierarchy for the current Composable function call.
     *
     * This function inspects the `callQueue` (which tracks the nesting of Composable calls)
     * to determine the parent Composable functions leading to the current call.
     * It filters out compiler-generated or internal Compose functions (e.g., lambdas)
     * to build a meaningful hierarchical path of user-defined Composables.
     *
     * @return A list of strings representing the names of the Composable functions
     *         in the call hierarchy, from the outermost parent to the current function.
     *         The current function's name is added as the last element.
     *         Returns an empty list if the current call is not found in the queue (should not happen in normal operation)
     *         or if there are no relevant parent composables.
     *
     * @author Farhazul Mullick
     */
    fun IrCall.getCallHierarchy(): List<String>  {
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
        pathComponents.add(calledComposableName)
        return pathComponents
    }

    /**
     * Generates a stable tag for a Composable function call.
     * The tag is constructed using the package name, "auto" prefix, optional test tag prefix,
     * file name, call hierarchy, and a hash of the merged IR dump of parameters.
     *
     * Example: `com.example:id/auto_MyPrefix_MyScreen_MyComponent_ChildComponent_-123456789`
     *
     * @return A string representing the stable tag.
     *
     * @author Farhazul Mullick
     */
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun IrCall.generateStableTag(): String {
        val calledComposableName = symbol.owner.name.asString()
        val fileName = currentFile.nameWithoutExtension

        val tagBuilder = StringBuilder()
        if (packageName.isNotEmpty()) {
            tagBuilder
                .append(packageName)
                .append(":id/")
        }
        tagBuilder.append("auto")
        if (testTagPrefix.isNotEmpty()) {
            tagBuilder
                .append("_")
                .append(testTagPrefix)
        }
        tagBuilder
            .append("_")
            .append(fileName)

        for (component in getCallHierarchy()) {
            tagBuilder
                .append("_")
                .append(component)
        }

        val methodHashId: Int = getMergedIrDumpOfParams().toString().hashCode()
        tagBuilder.append("_").append(methodHashId)

        println("$TAG --- Tag for '$calledComposableName is $tagBuilder' ---")
        println("$TAG --- End generateStableTag for '$calledComposableName' ---")

        return "$tagBuilder"
    }

    /**
     * Creates an IR expression for a lambda function that sets semantics properties.
     *
     * This function generates the following lambda:
     * ```
     * { // SemanticsPropertyReceiver
     *   testTagsAsResourceId = true
     *   testTag = testTag // provided testTag string
     * }
     * ```
     *
     * @param testTag The string value to be set for the `testTag` semantics property.
     * @return An [IrExpression] representing the created lambda.
     *
     * @author Farhazul Mullick
     */
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
            name = SpecialNames.ANONYMOUS
            returnType = unitType
            visibility = DescriptorVisibilities.LOCAL
            origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
        }.apply {
            // Add receiver parameter with correct name and origin
            val receiverParam: IrValueParameter = addValueParameter {
                name = SpecialNames.RECEIVER
                type = semanticsPropertyReceiverClass.defaultType
                origin = IrDeclarationOrigin.DEFINED
            }

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
        super.currentDeclarationParent?.let { lambdaFun.parent = it }
        return functionExpression
    }

    /**
     * Creates a copy of an [IrCall] with a new modifier expression.
     *
     * This function is used to replace the existing modifier argument of a composable function call
     * with a new modifier that includes semantics information.
     *
     * @param modifierParamIndex The index of the modifier parameter in the function call.
     * @param newModifier The new modifier expression to be used.
     * @return A new [IrCall] with the updated modifier.
     */
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

    // Helper functions for bit manipulation
    private fun defaultsBitIndex(paramIndex: Int): Int = paramIndex.rem(BITS_PER_INT)
    private fun defaultsParamIndex(paramIndex: Int): Int = paramIndex.div(BITS_PER_INT)
    private fun defaultParamCount(numParams: Int): Int = (numParams + 31) / 32
    /**
     * Calculates the number of "changed" parameter slots.
     *
     * In Compose, when a function with default parameters is called, an additional `$changed`
     * parameter is generated. This parameter is an integer (or a series of integers) that
     * acts as a bitmask. Each bit in this mask corresponds to a group of parameters
     * (value parameters, `this` receiver, extension receiver, context receivers).
     * If a bit is set, it means at least one parameter in that group has changed
     * since the last recomposition, or that it's the initial composition.
     *
     * This function determines how many such integer slots are needed to represent
     * the "changed" status for all relevant parameters.
     *
     * The `SLOTS_PER_INT` constant defines how many parameter groups can be represented
     * by a single `$changed` integer.
     *
     * @param realValueParams The number of actual value parameters (excluding compiler-generated ones like `$composer` or `$changed`).
     * @param thisParams The number of "this" parameters, which includes:
     *   - Dispatch receiver (if the function is a member of a class).
     *   - Extension receiver (if the function is an extension function).
     *   - Context receivers.
     * @return The number of integer slots required for the `$changed` parameter(s).
     *         Returns 1 if there are no parameters, as there's always at least one `$changed` slot.
     */
    private fun changedParamCount(realValueParams: Int, thisParams: Int): Int {
        val totalParams = realValueParams + thisParams
        if (totalParams == 0) return 1 // There is always at least 1 changed param
        return ceil(
            totalParams.toDouble() / SLOTS_PER_INT.toDouble()
        ).toInt()
    }
}