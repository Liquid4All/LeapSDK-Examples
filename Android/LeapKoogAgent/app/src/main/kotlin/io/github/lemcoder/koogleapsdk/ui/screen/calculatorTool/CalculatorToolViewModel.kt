package io.github.lemcoder.koogleapsdk.ui.screen.calculatorTool

import android.util.Log
import androidx.lifecycle.viewModelScope
import io.github.lemcoder.koogleapsdk.agents.calculator.CalculatorAgentProvider
import io.github.lemcoder.koogleapsdk.ui.common.MviViewModel
import io.github.lemcoder.koogleapsdk.ui.util.SnackbarUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CalculatorToolViewModel : MviViewModel<CalculatorToolState, CalculatorToolEvent>() {
    private val _state = MutableStateFlow(CalculatorToolState())
    override val state: StateFlow<CalculatorToolState> = _state.asStateFlow()

    override fun onEvent(event: CalculatorToolEvent) {
        when (event) {
            is CalculatorToolEvent.Calculate -> {
                calculateUsingAgent(event.expression)
            }
        }
    }

    internal fun calculateUsingAgent(expression: String) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isCalculating = true
                )
            }
            val prompt = "What is the result of: $expression"
            try {
                val agent = CalculatorAgentProvider().provideAgent(
                    onToolCallEvent = {
                        _state.update { state ->
                            state.copy(
                                toolCalls = _state.value.toolCalls + it
                            )
                        }
                    },
                    onErrorEvent = {
                        SnackbarUtil.showSnackbar(
                            "Error occurred: $it",
                        )
                    },
                    onAssistantMessage = {
                        Log.d("CalculatorToolViewModel", "Assistant message: $it")
                        _state.update { state ->
                            state.copy(
                                answer = it
                            )
                        }
                        it
                    }
                )
                val result = agent.run(prompt)

                _state.update {
                    it.copy(
                        isCalculating = false,
                        answer = result
                    )
                }
            } catch (ex: Exception) {
                _state.update {
                    it.copy(
                        isCalculating = false,
                    )
                }
                Log.e(this@CalculatorToolViewModel::class.simpleName, "Error occurred", ex)
            }
        }
    }
}