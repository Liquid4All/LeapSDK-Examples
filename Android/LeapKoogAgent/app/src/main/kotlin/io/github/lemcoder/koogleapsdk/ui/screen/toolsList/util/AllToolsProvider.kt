package io.github.lemcoder.koogleapsdk.ui.screen.toolsList.util

import io.github.lemcoder.koogleapsdk.App
import io.github.lemcoder.koogleapsdk.R
import io.github.lemcoder.koogleapsdk.ui.screen.toolsList.ToolsListState
import io.github.lemcoder.koogleapsdk.ui.screen.toolsList.ToolsListState.ToolItem.Companion.TOOL_ID_CALCULATOR
import io.github.lemcoder.koogleapsdk.ui.screen.toolsList.ToolsListState.ToolItem.Companion.TOOL_ID_WEATHER

val allTools by lazy {
    listOf(
        ToolsListState.ToolItem(
            id = TOOL_ID_CALCULATOR,
            name = App.context.getString(R.string.calculator),
            description = App.context.getString(R.string.a_simple_calculator_tool),
        ),
        ToolsListState.ToolItem(
            id = TOOL_ID_WEATHER,
            name = App.context.getString(R.string.weather),
            description = App.context.getString(R.string.get_current_weather_information),
        ),
    )
}
