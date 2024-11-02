package com.bnorm.template

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.Name

class IrConstTransformer(
  private val context: IrPluginContext,
  private val localContext: Map<IrValueSymbol, IrExpression>
) : IrElementTransformerVoid() {

  override fun visitConst(expression: IrConst<*>): IrExpression {
//    println("got to visit const")
    return expression
  }

  override fun visitGetValue(expression: IrGetValue): IrExpression {
//    println("Visiting getValue: ${expression.symbol}")
    return localContext[expression.symbol] ?: expression
  }

  override fun visitCall(expression: IrCall): IrExpression {
    val functionName = expression.symbol.owner.name.asString()

    // Handle built-in comparison operators
    if (functionName in setOf("greater", "less", "greaterOrEqual", "lessOrEqual", "equals", "notEquals")) {
      // Get both arguments for comparison
      val arg0 = expression.getValueArgument(0)?.transform(this, null)
      val arg1 = expression.getValueArgument(1)?.transform(this, null)

      // If both arguments are constants, evaluate
      if (arg0 is IrConst<*> && arg1 is IrConst<*>) {
        return createConstFromOperation(
          expression.symbol.owner.name,
          listOf(arg0, arg1)
        )
      }
      return expression
    }

    // Handle other calls with dispatch receiver
    val receiver = expression.dispatchReceiver
    val transformedReceiver = receiver?.transform(this, null)

    val arg = expression.getValueArgument(0)
    val transformedArg = arg?.transform(this, null)

    if (transformedReceiver is IrConst<*> && transformedArg is IrConst<*>) {
      return createConstFromOperation(
        expression.symbol.owner.name,
        listOf(transformedReceiver, transformedArg)
      )
    }

    return expression
  }

  private fun createConstFromOperation(
    operation: Name,
    args: List<IrConst<*>?>
  ): IrExpression {
    return when {
      args.all { it?.value is Int }     -> {
//        println("got to int")
        createIntOperation(operation, args)
      }

      args.all { it?.value is String }  -> createStringOperation(operation, args)
      args.all { it?.value is Boolean } -> createBooleanOperation(operation, args)
      else                              -> throw UnsupportedOperationException("Unsupported types for operation: $operation")
    }
  }

  private fun createIntOperation(operation: Name, args: List<IrConst<*>?>): IrExpression {
    return when (operation.asString()) {
      "plus"           -> createIntConst((args[0]?.value as Int) + (args[1]?.value as Int))
      "minus"          -> createIntConst((args[0]?.value as Int) - (args[1]?.value as Int))
      "times"          -> createIntConst((args[0]?.value as Int) * (args[1]?.value as Int))
      "div"            -> createIntConst(
        (args[0]?.value as Int).let { a ->
          (args[1]?.value as Int).let { b ->
            if (b == 0) throw ArithmeticException("Division by zero")
            a / b
          }
        }
      )

      "compareTo"      -> createIntConst((args[0]?.value as Int).compareTo(args[1]?.value as Int))
      "greater"        -> createBooleanConst((args[0]?.value as Int) > (args[1]?.value as Int))
      "less"           -> createBooleanConst((args[0]?.value as Int) < (args[1]?.value as Int))
      "greaterOrEqual" -> createBooleanConst((args[0]?.value as Int) >= (args[1]?.value as Int))
      "lessOrEqual"    -> createBooleanConst((args[0]?.value as Int) <= (args[1]?.value as Int))
      else             -> throw UnsupportedOperationException("Unknown Int operation: $operation")
    }
  }

  // Add this helper function for boolean constants
  private fun createBooleanConst(value: Boolean): IrConst<Boolean> {
    return IrConstImpl.boolean(
      UNDEFINED_OFFSET,
      UNDEFINED_OFFSET,
      context.irBuiltIns.booleanType,
      value
    )
  }

  private fun createStringOperation(operation: Name, args: List<IrConst<*>?>): IrExpression {
    return when (operation.asString()) {
      "plus"      -> createStringConst((args[0]?.value as String) + (args[1]?.value as String))
      "compareTo" -> createIntConst((args[0]?.value as String).compareTo(args[1]?.value as String))
      "length"    -> createIntConst((args[0]?.value as String).length)
      else        -> throw UnsupportedOperationException("Unknown String operation: $operation")
    }
  }

  private fun createBooleanOperation(operation: Name, args: List<IrConst<*>?>): IrExpression {
    return when (operation.asString()) {
      "and"       -> createBooleanConst((args[0]?.value as Boolean) && (args[1]?.value as Boolean))
      "or"        -> createBooleanConst((args[0]?.value as Boolean) || (args[1]?.value as Boolean))
      "not"       -> createBooleanConst(!(args[0]?.value as Boolean))
      "compareTo" -> createIntConst((args[0]?.value as Boolean).compareTo(args[1]?.value as Boolean))
      else        -> throw UnsupportedOperationException("Unknown Boolean operation: $operation")
    }
  }

  private fun createIntConst(
    value: Int,
    startOffset: Int = UNDEFINED_OFFSET,
    endOffset: Int = UNDEFINED_OFFSET
  ): IrConst<Int> = IrConstImpl(
    startOffset = startOffset,
    endOffset = endOffset,
    type = context.irBuiltIns.intType,
    kind = IrConstKind.Int,
    value = value
  )

  private fun createStringConst(
    value: String,
    startOffset: Int = UNDEFINED_OFFSET,
    endOffset: Int = UNDEFINED_OFFSET
  ): IrConst<String> = IrConstImpl(
    startOffset = startOffset,
    endOffset = endOffset,
    type = context.irBuiltIns.stringType,
    kind = IrConstKind.String,
    value = value,
  )
}
