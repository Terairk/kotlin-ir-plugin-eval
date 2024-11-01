package com.bnorm.template

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.impl.IrValueParameterImpl
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrValueParameterSymbolImpl
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.Name


class IrEvalFunctionTransformer(
  private val pluginContext: IrPluginContext,
  private val interpreter: EvalIrInterpreter,
  private val sharedContext: SharedEvalContext
) : IrElementTransformerVoid() {
  override fun visitCall(expression: IrCall): IrExpression {
    if (!expression.symbol.owner.name.asString().startsWith("eval")) {
      return super.visitCall(expression)
    }

    println("got past eval.")
    // 1. First transform all arguments recursively
    val transformedArgs = expression.valueArgumentsCount.let { count ->
      (0 until count).map { i ->
        expression.getValueArgument(i)?.transform(this, null)?.also { transformed ->
          if (transformed is IrConst<*>) {
            val param = expression.symbol.owner.valueParameters[i]
            sharedContext.variables[param.symbol] = transformed
          }
        }
      }
    }

    println("past 1")

    // 2. Check if all arguments are constants
    if (transformedArgs.all { it is IrConst<*> }) {
      // 3. Set up parameter mappings for the interpreter
      val function = expression.symbol.owner
      val parameterMappings = function.valueParameters.zip(transformedArgs)
        .associate { (param, arg) ->
          param to (arg as IrConst<*>)
        }

      println("past 2/3")

      // 4. Initialize interpreter with the constant parameters
      interpreter.initializeFunctionContext(parameterMappings)

      // 5. Evaluate the function
      val result = interpreter.evaluateFunction(function)

      // 6. Return the result if evaluation was successful
      if (result is IrConst<*>) {
        // Create new constant based on the result type

        // create a temporary value to store the result
        val tempValueParameter = IrValueParameterImpl(
          startOffset = expression.startOffset,
          endOffset = expression.endOffset,
          origin = IrDeclarationOrigin.DEFINED,
          symbol = IrValueParameterSymbolImpl(),
          name = Name.identifier("temp_${expression.symbol.owner.name}"),
          index = -1,
          type = result.type,
          varargElementType = null,
          isCrossinline = false,
          isNoinline = false,
          isHidden = false,
          isAssignable = false
        )


        sharedContext.variables[tempValueParameter.symbol] = result
        println("got here const")
        return when (result.kind) {
          IrConstKind.Int -> IrConstImpl(
            startOffset = expression.startOffset,
            endOffset = expression.endOffset,
            type = result.type,
            kind = IrConstKind.Int,
            value = result.value as Int
          )
          IrConstKind.String -> IrConstImpl(
            startOffset = expression.startOffset,
            endOffset = expression.endOffset,
            type = result.type,
            kind = IrConstKind.String,
            value = result.value as String
          )
          IrConstKind.Boolean -> IrConstImpl(
            startOffset = expression.startOffset,
            endOffset = expression.endOffset,
            type = result.type,
            kind = IrConstKind.Boolean,
            value = result.value as Boolean
          )
          else -> expression // Fall back to original expression for unsupported types
        }
      }
    }

    println("result is not const")
    return super.visitCall(expression)
  }

  override fun visitGetValue(expression: IrGetValue): IrExpression {
    // Check shared context for any stored values
    return sharedContext.variables[expression.symbol] ?: super.visitGetValue(expression)
  }
}
