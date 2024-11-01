package com.bnorm.template

import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol

class SharedEvalContext {
  val variables = mutableMapOf<IrValueSymbol, IrExpression>()
}
