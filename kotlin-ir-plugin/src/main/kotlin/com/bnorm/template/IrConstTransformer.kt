package com.bnorm.template

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.codegen.StackValue.Shared
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.Name

class IrConstTransformer(
  private val context: IrPluginContext,
  private val sharedContext: SharedEvalContext
) : IrElementTransformerVoid() {

  override fun visitConst(expression: IrConst<*>): IrExpression {
    println("got to visit const")
    return expression
  }

  override fun visitGetValue(expression: IrGetValue): IrExpression {
    println("Visiting getValue: ${expression.symbol}")
    return sharedContext.variables[expression.symbol] ?: expression
  }

  override fun visitCall(expression: IrCall): IrExpression {
    println("Visiting call: ${expression.symbol.owner.name}")

    // Transform dispatch receiver (preserving IrGetValue)
    val receiver = expression.dispatchReceiver
    println("Receiver: $receiver")
    val transformedReceiver =
     receiver?.transform(this, null)

    println("Transformed receiver: $transformedReceiver")

    // Transform argument (preserving IrGetValue)
    val arg = expression.getValueArgument(0)
    println("Arg: $arg")
    val transformedArg = arg?.transform(this, null)
    println("Transformed arg: $transformedArg")

    // If both are constants, evaluate immediately
    if (transformedReceiver is IrConst<*> && transformedArg is IrConst<*>) {
      println("Both are constants, evaluating immediately")
      return createConstFromOperation(
        expression.symbol.owner.name,
        listOf(transformedReceiver, transformedArg)
        )
    }

    // If they're not both constants, preserve the operation
    if (transformedReceiver != null && transformedArg != null) {
      println("Preserving operation with non-constant values")
      return IrCallImpl(
        startOffset = UNDEFINED_OFFSET,
        endOffset = UNDEFINED_OFFSET,
        type = context.irBuiltIns.intType,
        symbol = expression.symbol,
        typeArgumentsCount = 0,
        valueArgumentsCount = 1
      ).apply {
        dispatchReceiver = transformedReceiver
        putValueArgument(0, transformedArg)
      }
    }

    return expression

  }

  private fun createConstFromOperation(
    operation: Name,
    args: List<IrConst<*>?>
  ): IrExpression {
    return when {
      args.all { it?.value is Int } -> {
        println("got to int")
        createIntOperation(operation, args)
      }
      args.all { it?.value is String } -> createStringOperation(operation, args)
      args.all { it?.value is Boolean } -> createBooleanOperation(operation, args)
      else -> throw UnsupportedOperationException("Unsupported types for operation: $operation")
    }
  }

  private fun createIntOperation(operation: Name, args: List<IrConst<*>?>): IrExpression {
    return when (operation.asString()) {
      "plus" -> createIntConst((args[0]?.value as Int) + (args[1]?.value as Int))
      "minus" -> createIntConst((args[0]?.value as Int) - (args[1]?.value as Int))
      "times" -> createIntConst((args[0]?.value as Int) * (args[1]?.value as Int))
      "div" -> createIntConst(
        (args[0]?.value as Int).let { a ->
          (args[1]?.value as Int).let { b ->
            if (b == 0) throw ArithmeticException("Division by zero")
            a / b
          }
        }
      )
      "compareTo" -> createIntConst((args[0]?.value as Int).compareTo(args[1]?.value as Int))
      else -> throw UnsupportedOperationException("Unknown Int operation: $operation")
    }
  }

  // ... rest of the helper methods remain the same ...
  private fun createStringOperation(operation: Name, args: List<IrConst<*>?>): IrExpression {
    return when (operation.asString()) {
      "plus" -> createStringConst((args[0]?.value as String) + (args[1]?.value as String))
      "compareTo" -> createIntConst((args[0]?.value as String).compareTo(args[1]?.value as String))
      "length" -> createIntConst((args[0]?.value as String).length)
      else -> throw UnsupportedOperationException("Unknown String operation: $operation")
    }
  }

  private fun createBooleanOperation(operation: Name, args: List<IrConst<*>?>): IrExpression {
    return when (operation.asString()) {
      "and" -> createBooleanConst((args[0]?.value as Boolean) && (args[1]?.value as Boolean))
      "or" -> createBooleanConst((args[0]?.value as Boolean) || (args[1]?.value as Boolean))
      "not" -> createBooleanConst(!(args[0]?.value as Boolean))
      "compareTo" -> createIntConst((args[0]?.value as Boolean).compareTo(args[1]?.value as Boolean))
      else -> throw UnsupportedOperationException("Unknown Boolean operation: $operation")
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
    value = value
  )

  private fun createBooleanConst(
    value: Boolean,
    startOffset: Int = UNDEFINED_OFFSET,
    endOffset: Int = UNDEFINED_OFFSET
  ): IrConst<Boolean> = IrConstImpl(
    startOffset = startOffset,
    endOffset = endOffset,
    type = context.irBuiltIns.booleanType,
    kind = IrConstKind.Boolean,
    value = value
  )
}
