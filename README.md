# KotlinIRConstEvaluator

## Overview
A partially working KotlinIRConstEvaluator, developed as part of the "Improve constant 
evaluation in Kotlin" task for JetBrains Internship. 

## Features
- Const Evaluation for Arithmetic operations such as (+, -, /, *) 
- Const Evaluation for basic when and if expressions 
- Const Evaluation for String concatenation
- Wasn't able to get the other features working such as constant propagation, WHILE, &&, ||, due 
  to problems with GET_VAR ie IrGetValue (not working as intended)

## Program Structure
I used a template from Brian Norman hence the com.bnorm.template
My files are under src/main/kotlin/com.bnorm.template/

The tests are under test/kotlin/com.bnorm.template/IrPluginTest.kt. 
I currently just my moduleFragment to see if it works.
Feel free to edit the tests and see how they run. 

The entry point is IrEvalFunctionTransformer which transforms the IR. 
It only works on functions starting with eval. It attempts to evaluate the arguments and checks 
if they're IrConst. Then it attempts to evaluate it with the help of EvalIrInterpreter.
EvalIrInterpreter then uses IrConstTransformer to help it with further modifying the Ir.
I maintain a local context for every function call which helps with evaluation.

In summary:
IrEvalFunctionTransformer: top-level interceptor (pulls everything together)
EvalIrInterpreter is the one that handles the most cases such as IrWhen, IrGetValue, IrBlockBody etc
IrConstTransformer just helps with evaluating the type specific stuff and tries to handle cases 
such as calls that use dispatch receivers vs those that use valueArguments.

As I'm writing this, I realise now I should make EvalIrInterpreter / IrConstTransformer call 
IrEvalFunctionTransformer again (ie nested recursion calls). Since this was all new to me, in 
the future I'd probably stick to one main transformer with helpers that don't transform.

# Kotlin IR Plugin Structure
Project Organization
src/
└── main/kotlin/com.bnorm.template/
├── IrEvalFunctionTransformer.kt
├── EvalIrInterpreter.kt
└── IrConstTransformer.kt
test/
└── kotlin/com.bnorm.template/
└── IrPluginTest.kt

# Core Components
1. ## IrEvalFunctionTransformer
   - Acts as the main entry point
   - Primary IR transformer
   - Targets functions prefixed with "eval"
   - Evaluates arguments and checks for IrConst
   - Coordinates with EvalIrInterpreter for evaluation
2. ## EvalIrInterpreter
   Handles complex IR cases including:
   - IrWhen
   - IrGetValue
   - IrBlockBody
   - Maintains local context for function calls
   - Core evaluation logic
3. ## IrConstTransformer
   - Supports type-specific evaluations
   Handles special cases:
    - Dispatch receiver calls
    - Value argument calls
4. ## Testing
   - Located in IrPluginTest.kt
   - Currently focuses on moduleFragment verification
   - Provides framework for testing transformations
5. ## Future Improvements
   - Implement recursive calling between components:
   - EvalIrInterpreter might call IrEvalFunctionTransformer
   - IrConstTransformer should call IrEvalFunctionTransformer
   - Consider simplifying architecture to:
   - Single main transformer
   -Helper classes without transformation capabilities


# Note
Project structure based on Brian Norman's template, maintaining the com.bnorm.template package structure.
