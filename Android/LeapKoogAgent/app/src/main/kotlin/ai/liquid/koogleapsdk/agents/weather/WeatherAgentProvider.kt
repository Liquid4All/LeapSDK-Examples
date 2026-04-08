package ai.liquid.koogleapsdk.agents.weather

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.message.Message
import ai.liquid.koogleapsdk.agents.common.AgentProvider
import ai.liquid.koogleapsdk.agents.common.modelsPath
import io.github.lemcoder.koog.edge.leap.LeapModels
import io.github.lemcoder.koog.edge.leap.getLeapLLMClient

/** Factory for creating weather forecast agents */
internal class WeatherAgentProvider : AgentProvider {
    override val title: String = "Weather Forecast"
    override val description: String =
        "Hi, I'm a weather agent. I can provide weather forecasts for any location."

    override suspend fun provideAgent(
        onToolCallEvent: suspend (String) -> Unit,
        onErrorEvent: suspend (String) -> Unit,
        onAssistantMessage: suspend (String) -> String,
    ): AIAgent<String, String> {
        val leapExecutor = SingleLLMPromptExecutor(getLeapLLMClient(modelsPath))

        // Create tool registry with weather tools
        val toolRegistry = ToolRegistry { tool(WeatherTools.WeatherForecastTool) }

        @Suppress("DuplicatedCode")
        val strategy =
            functionalStrategy<String, String>(title) { input ->
                var response = requestLLM(input)

                while (response is Message.Tool.Call) {
                    val tool = response

                    val result = executeTool(tool)
                    response = sendToolResult(result)
                }

                val assistantContent = response.asAssistantMessage().content
                onAssistantMessage(assistantContent)
            }

        // Create agent config with proper prompt
        val agentConfig =
            AIAgentConfig(
                prompt =
                    prompt("test") {
                        system(
                            """
                            You are a helpful weather assistant. Use the tools available to provide accurate weather forecasts.
                            """
                                .trimIndent()
                        )
                    },
                model = LeapModels.Chat.LFM2_1_2B_Instruct,
                maxAgentIterations = 50,
            )

        // Return the agent
        return AIAgent(
            promptExecutor = leapExecutor,
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = toolRegistry,
        )
    }
}
