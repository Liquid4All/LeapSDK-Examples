package io.github.lemcoder.koogleapsdk.ui.screen.weatherTool

sealed class WeatherToolEvent {
    data class OnSearchClick(val cityName: String) : WeatherToolEvent()
}