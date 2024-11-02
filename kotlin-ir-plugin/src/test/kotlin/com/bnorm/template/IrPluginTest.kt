/*
 * Copyright (C) 2020 Brian Norman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(ExperimentalCompilerApi::class)

package com.bnorm.template

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import kotlin.test.assertEquals
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test
import kotlin.io.path.writeText
import kotlin.io.path.createTempFile

class IrPluginTest {
  private class TestCase(private val sourceCode: String) {
    fun assertCompilationSuccess() {
      val tempFile = createTempFile(prefix = "main", suffix = ".kt").also { path ->
        path.writeText(sourceCode)
        path.toFile().deleteOnExit()
      }.toFile()

      val result = compile(sourceFile = SourceFile.fromPath(tempFile))
      assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }
  }

  // This one works
  @Test
  fun `evalAdd(1,2) = 3`() {
    val result = compile(
      sourceFile = SourceFile.kotlin(
        "main.kt", """

fun evalAdd(a: Int, b: Int): Int {
    return a + b
}

fun main() {
    // evalAdd(1, 2) must be evaluated as 3
    println(evalAdd(1, 2))
}  
"""
      )
    )
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
  }

  // This one works
  @Test
  fun `evalAdd(1, evalAdd(1,2)) = 6`() {
    TestCase(
      """
      fun main() {
        println(evalAdd(1, evalAdd(2,3)))
      }
      
      fun evalAdd(a: Int, b: Int): Int {
        return a + b
      }
    """
    ).assertCompilationSuccess()
  }

  // This one works
  @Test
  fun `evalMultiply(4, 5) = 20`() {
    TestCase(
      """
      fun main() {
        println(evalMultiply(4,5))
      }
      
      fun evalMultiply(a: Int, b: Int): Int {
        return a * b
      }
    """
    ).assertCompilationSuccess()
  }

  // This one works
  @Test
  fun `evalComplex(10, 5) = 30`() {
    TestCase(
      """
      fun main() {
        println(evalComplex(10,5))
      }
      
      fun evalComplex(a: Int, b: Int): Int {
        return (a + b) * 2
      }
    """
    ).assertCompilationSuccess()
  }

  // Currently doesn't work
  // TODO()
  @Test
  fun `evalMax(10, 5) = 10`() {
    TestCase(
      """
      fun main() {
        println(evalMax(10,5))
      }
      
      fun evalMax(a: Int, b: Int): Int {
        if (a > b) {
          return a
        } else {
          return b
        }
      }
    """
    ).assertCompilationSuccess()
  }

  // Currently works
  @Test
  fun `evalWhen(-1)=negative`() {
    TestCase(
      """
      fun main() {
        println(evalWhen(-1))
      }
      
      fun evalWhen(x: Int): String {
        return when {
          x > 0 -> "positive"
          x < 0 -> "negative"
        else -> "zero"
        }
      }
    """
    ).assertCompilationSuccess()
  }

  // Currently doesn't work
  // TODO()
  @Test
  fun `evalSum(5)=15`() {
    TestCase(
      """
      fun main() {
        println(evalSum(5))
      }
      
      fun evalSum(n: Int): Int {
          var sum = 0
          var i = 1
          while (i <= n) {
              sum += i
              i++
          }
          return sum
      }
    """
    ).assertCompilationSuccess()
  }

  // Currently doesnt' work
  // TODO()
  @Test
  fun `evalFactorial(120)=120`() {
    TestCase(
      """
      fun main() {
        println(evalFactorial(5))
      }
      
      fun evalFactorial(n: Int): Int {
          var result = 1
          var i = 1
          while (i <= n) {
              result *= i
              i++
          }
          return result
      }
    """
    ).assertCompilationSuccess()
  }

  // Currently works
  @Test
  fun `evalConcat(hello, world)=helloworld`() {
    TestCase(
      """
      fun main() {
        println(evalConcat("Hello", "World"))
      }
      
      fun evalConcat(a: String, b: String): String {
         return a + b
      }
    """
    ).assertCompilationSuccess()
  }


  // Doesn't compile
  @Test
  fun `evalStringLength(Test)=4`() {
    TestCase(
      """
      fun main() {
        println(evalStringLength("Test"))
      }
      
      fun evalStringLength(str: String): Int {
         return str.length
      }
    """
    ).assertCompilationSuccess()
  }

  // Doesn't work, GET_VAR isn't behaving as expected
  // TODO()
  @Test
  fun `evalAnd(true, false) = false`() {
    TestCase(
      """
      fun main() {
        println(evalAnd(true, false))
      }
      
      fun evalAnd(a: Boolean, b: Boolean): Boolean {
         return a && b
      }
    """).assertCompilationSuccess()
  }

  // Doesn't work
  // TODO()
  @Test
  fun `evalAndOr(true, false) = false`() {
    TestCase(
      """
      fun main() {
        println(evalAndOr(true, false))
      }
      
      fun evalAndOr(a: Boolean, b: Boolean): Boolean {
         return (a && b) || b
      }
    """).assertCompilationSuccess()
  }
}

fun compile(
  sourceFiles: List<SourceFile>,
  plugin: CompilerPluginRegistrar = EvalCompilerPluginRegistrar(),
): JvmCompilationResult {
  return KotlinCompilation().apply {
    sources = sourceFiles
    compilerPluginRegistrars = listOf(plugin)
    inheritClassPath = true
  }.compile()
}

fun compile(
  sourceFile: SourceFile,
  plugin: CompilerPluginRegistrar = EvalCompilerPluginRegistrar(),
): JvmCompilationResult {
  return compile(listOf(sourceFile), plugin)
}
