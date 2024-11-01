package com.bnorm.template

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid


class EvalIrInterpreter(private val context: IrPluginContext, private val sharedContext: SharedEvalContext) {
  private lateinit var constTransformer: IrElementTransformerVoid;

  fun initializeFunctionContext(mappings: Map<IrValueParameter, IrConst<*>>) {
    // Store the constant values for parameters
    sharedContext.variables.putAll(mappings.map { (param, const) ->
      param.symbol to const
    })

    val newMappings = mappings.mapKeys { (parameter, _) -> parameter.symbol as IrValueSymbol }
    constTransformer = IrConstTransformer(context, sharedContext)
  }


  private class EvalContext {
    val variables = mutableMapOf<IrValueSymbol, IrExpression>()
  }

  fun evaluateFunction(function: IrFunction): IrExpression? {
    if (!function.name.asString().startsWith("eval")) {
      return null
    }

    return try {
      function.body?.let { body ->
        when (body) {
          is IrBlockBody      -> evaluateBlockBody(body)
          is IrExpressionBody -> evaluateExpression(
            body.expression,
            context = sharedContext
          )
          else                -> null
        }
      }
    } catch (e: Exception) {
      throw e
      null
    }
  }

  private fun evaluateBlockBody(body: IrBlockBody): IrExpression {
    var result: IrExpression = createUnitValue()

    for (statement in body.statements) {
      result = when (statement) {
        is IrReturn   -> {
          return evaluateExpression(statement.value, sharedContext)
        }
        is IrVariable  -> ({
          val initializer = statement.initializer?.let {
                evaluateExpression(it, sharedContext)
          }
          if (initializer != null) {
                sharedContext.variables[statement.symbol] = initializer
          }
          initializer ?: statement
        }) as IrExpression
        is IrWhen      -> evaluateWhen(statement, sharedContext)
        is IrWhileLoop -> evaluateWhile(statement, sharedContext)
        else           -> ({
          when (val transformed = statement.transform(constTransformer, null)) {
                is IrGetValue -> {
                  // Look up variable value from context
                  sharedContext.variables[transformed.symbol] ?: transformed
                }
                else -> transformed
          }
        }) as IrExpression
      }
    }

    return result
  }



  private fun evaluateExpression(expr: IrExpression, context: SharedEvalContext): IrExpression {
    return when (expr) {
      is IrCall -> {
        // Handle arithmetic operations
        val transformedArgs = (0 until expr.valueArgumentsCount).mapNotNull { i ->
          expr.getValueArgument(i)?.let { arg ->
            evaluateExpression(arg, context)
          }
        }

        if (transformedArgs.all { it is IrConst<*> }) {
          // Create a new IrCall with the transformed arguments
          transformedArgs.forEachIndexed { index, arg ->
            expr.putValueArgument(index, arg)
          }
          expr.transform(constTransformer, null)
        } else {
          expr
        }
      }
      is IrGetValue -> {
        // Check if it's a parameter reference
        context.variables[expr.symbol] ?: expr
      }
      else -> expr.transform(constTransformer, null)
    }
  }

  private fun evaluateWhen(whenExpr: IrWhen, context: SharedEvalContext): IrExpression {
    for (branch in whenExpr.branches) {
      val condition = evaluateExpression(branch.condition, context)
      if (condition is IrConst<*> && condition.value as Boolean) {
        return evaluateExpression(branch.result, context)
      }
    }
    return whenExpr
  }

  private fun evaluateWhile(whileExpr: IrWhileLoop, context: SharedEvalContext): IrExpression {
    var result: IrExpression = createUnitValue()

    while (true) {
      val condition = evaluateExpression(whileExpr.condition, context)
      if (condition !is IrConst<*> || !(condition.value as Boolean)) {
        break
      }
      result = whileExpr.body?.let { evaluateExpression(it, context) }!!
    }

    return result
  }

  private fun createUnitValue(): IrExpression {
    return IrGetObjectValueImpl(
      startOffset = UNDEFINED_OFFSET,
      endOffset = UNDEFINED_OFFSET,
      type = context.irBuiltIns.unitType,
      symbol = context.irBuiltIns.unitClass
    )
  }
}

