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


class EvalIrInterpreter(private val context: IrPluginContext) {
  private lateinit var constTransformer: IrElementTransformerVoid;
  private val localContext = mutableMapOf<IrValueSymbol, IrExpression>()

  fun initializeFunctionContext(mappings: Map<IrValueParameter, IrConst<*>>) {
    // Store the constant values for parameters
    localContext.clear()
    localContext.putAll(mappings.map { (param, const) ->
      param.symbol to const
    })

    constTransformer = IrConstTransformer(context, localContext)
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
            body.expression
          )
          else                -> null
        }
      }
    } catch (e: Exception) {
      null
    }
  }

  private fun evaluateBlockBody(body: IrBlockBody): IrExpression {
    var result: IrExpression = createUnitValue()

    for (statement in body.statements) {
      result = when (statement) {
        is IrReturn   -> {
          return evaluateExpression(statement.value)
        }
        is IrVariable  -> ({
          val initializer = statement.initializer?.let {
                evaluateExpression(it)
          }
          if (initializer != null) {
                localContext[statement.symbol] = initializer
          }
          initializer ?: statement
        }) as IrExpression
        is IrWhen      -> evaluateWhen(statement)
        is IrWhileLoop -> evaluateWhile(statement)
        is IrGetValue -> evaluateExpression(statement)
        else           -> ({
          when (val transformed = statement.transform(constTransformer, null)) {
                is IrGetValue -> {
                  // Look up variable value from context
                  localContext[transformed.symbol] ?: transformed
                }
                else -> transformed
          }
        }) as IrExpression
      }
    }

    return result
  }



 private fun evaluateExpression(expr: IrExpression): IrExpression {
    return when (expr) {
      is IrCall -> {
        // Handle arithmetic operation
        val transformedArgs = expr.symbol.owner.valueParameters.mapNotNull { param ->
          localContext[param.symbol]?.let { evaluateExpression(it) }
        }

        if (transformedArgs.all { it is IrConst<*> }) {
          expr.transform(constTransformer, null)
        } else {
          expr
        }
      }
      is IrGetValue -> {
        // Check if it's a parameter reference
//        println("got here for evalExpr")
        localContext[expr.symbol] ?: expr
      }
      is IrWhen -> {
        evaluateWhen(expr)
      }
      else -> {
//        println("got const for evalExpr")
        expr.transform(constTransformer, null)
      }
    }
  }

  private fun evaluateWhen(whenExpr: IrWhen): IrExpression {
    for (branch in whenExpr.branches) {
      val condition = evaluateExpression(branch.condition)
      if (condition is IrConst<*> && condition.value as Boolean) {
        return evaluateExpression(branch.result)
      }
    }
    return whenExpr
  }

  private fun evaluateWhile(whileExpr: IrWhileLoop): IrExpression {
    var result: IrExpression = createUnitValue()
    val maxIterations = 10000
    var iterations = 0

    try {
      while (iterations < maxIterations) {
        iterations++

        // Evaluate the condition first
        val evaluatedCondition = evaluateExpression(whileExpr.condition)
        if (evaluatedCondition !is IrConst<*>) {
          return whileExpr
        }

        if (!(evaluatedCondition.value as Boolean)) {
          break
        }

        // Evaluate the body to make progress
        result = whileExpr.body?.let { body ->
          when (body) {
            is IrBlockBody -> evaluateBlockBody(body)
            is IrExpressionBody -> evaluateExpression(body.expression)
            else -> return whileExpr
          }
        } ?: return whileExpr
      }

      // If we hit iteration limit, return original expression
      if (iterations >= maxIterations) {
        return whileExpr
      }

      return result

    } catch (e: Exception) {
      return whileExpr
    }
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

