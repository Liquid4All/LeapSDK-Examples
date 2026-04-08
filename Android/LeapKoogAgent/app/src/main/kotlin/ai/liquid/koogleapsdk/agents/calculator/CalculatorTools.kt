package ai.liquid.koogleapsdk.agents.calculator

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

object CalculatorTools {
    abstract class CalculatorTool(toolName: String, toolDescription: String) :
        Tool<CalculatorTool.Args, CalculatorTool.Result>(
            argsSerializer = Args.serializer(),
            resultSerializer = Result.serializer(),
            name = toolName,
            description = toolDescription,
        ) {
        @Serializable
        data class Args(
            @property:LLMDescription("First number") val a: Float,
            @property:LLMDescription("Second number") val b: Float,
        )

        @Serializable class Result(val result: Float)
    }

    /** 2. Implement the tool (tools). */
    object PlusTool : CalculatorTool(toolName = "plus", toolDescription = "Addition") {
        override suspend fun execute(args: Args): Result {
            return Result(args.a + args.b)
        }
    }

    object MinusTool : CalculatorTool(toolName = "minus", toolDescription = "Subtracts b from a") {
        override suspend fun execute(args: Args): Result {
            return Result(args.a - args.b)
        }
    }

    object DivideTool : CalculatorTool(toolName = "divide", toolDescription = "Divides a and b") {
        override suspend fun execute(args: Args): Result {
            return Result(args.a / args.b)
        }
    }

    object MultiplyTool :
        CalculatorTool(toolName = "multiply", toolDescription = "Multiplies a and b") {
        override suspend fun execute(args: Args): Result {
            return Result(args.a * args.b)
        }
    }
}
