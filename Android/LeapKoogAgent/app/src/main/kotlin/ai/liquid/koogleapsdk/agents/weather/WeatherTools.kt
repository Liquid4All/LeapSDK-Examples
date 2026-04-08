package ai.liquid.koogleapsdk.agents.weather

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.LLMDescription
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Tools for the weather agent */
object WeatherTools {
    private val openMeteoClient = OpenMeteoClient()

    private val UTC_ZONE: ZoneId = ZoneOffset.UTC

    /** Granularity options for weather forecasts */
    @Serializable
    enum class Granularity {
        @SerialName("daily") DAILY,
        @SerialName("hourly") HOURLY,
    }

    /** Tool for getting the current date and time */
    object CurrentDatetimeTool :
        Tool<CurrentDatetimeTool.Args, CurrentDatetimeTool.Result>(
            name = "current_datetime",
            description = "Get the current date and time in the specified timezone",
            argsSerializer = Args.serializer(),
            resultSerializer = Result.serializer(),
        ) {
        @Serializable
        data class Args(
            @property:LLMDescription(
                "The timezone to get the current date and time in (e.g., 'UTC', 'America/New_York', 'Europe/London'). Defaults to UTC."
            )
            val timezone: String = "UTC"
        )

        @Serializable
        data class Result(
            val datetime: String,
            val date: String,
            val time: String,
            val timezone: String,
        )

        override suspend fun execute(args: Args): Result {
            val zoneId =
                try {
                    ZoneId.of(args.timezone)
                } catch (_: Exception) {
                    UTC_ZONE
                }

            val now = ZonedDateTime.now(zoneId)
            val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

            return Result(
                datetime = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                date = now.toLocalDate().toString(),
                time = now.toLocalTime().format(timeFormatter),
                timezone = zoneId.id,
            )
        }
    }

    /** Tool for adding a duration to a date */
    object AddDatetimeTool :
        Tool<AddDatetimeTool.Args, AddDatetimeTool.Result>(
            name = "add_datetime",
            description =
                "Add a duration to a date. Use this tool when you need to calculate offsets, such as tomorrow, in two days, etc.",
            argsSerializer = Args.serializer(),
            resultSerializer = Result.serializer(),
        ) {
        @Serializable
        data class Args(
            @property:LLMDescription("The date to add to in ISO format (e.g., '2023-05-20')")
            val date: String,
            @property:LLMDescription("The number of days to add") val days: Int,
            @property:LLMDescription("The number of hours to add") val hours: Int,
            @property:LLMDescription("The number of minutes to add") val minutes: Int,
        )

        @Serializable
        data class Result(
            val date: String,
            val originalDate: String,
            val daysAdded: Int,
            val hoursAdded: Int,
            val minutesAdded: Int,
        )

        override suspend fun execute(args: Args): Result {
            val baseDate =
                if (args.date.isNotBlank()) {
                    try {
                        LocalDate.parse(args.date)
                    } catch (_: Exception) {
                        // Use current date if parsing fails
                        LocalDate.now(UTC_ZONE)
                    }
                } else {
                    LocalDate.now(UTC_ZONE)
                }

            val baseDateTime = baseDate.atStartOfDay(UTC_ZONE)
            val resultDateTime =
                baseDateTime
                    .plusDays(args.days.toLong())
                    .plusHours(args.hours.toLong())
                    .plusMinutes(args.minutes.toLong())

            return Result(
                date = resultDateTime.toLocalDate().toString(),
                originalDate = args.date,
                daysAdded = args.days,
                hoursAdded = args.hours,
                minutesAdded = args.minutes,
            )
        }
    }

    /** Tool for getting a weather forecast */
    object WeatherForecastTool :
        Tool<WeatherForecastTool.Args, WeatherForecastTool.Result>(
            name = "weather_forecast",
            description =
                "Get a weather forecast for a location with specified granularity (daily or hourly)",
            argsSerializer = Args.serializer(),
            resultSerializer = Result.serializer(),
        ) {
        @Serializable
        data class Args(
            @property:LLMDescription(
                "The location to get the weather forecast for (e.g., 'New York', 'London', 'Paris')"
            )
            val location: String,
            @property:LLMDescription("The number of days to forecast (1-7)") val days: Int = 1,
        )

        @Serializable
        data class Result(
            val locationName: String,
            val locationCountry: String? = null,
            val forecast: String,
            val date: String,
            val granularity: Granularity,
        )

        override suspend fun execute(args: Args): Result {
            val date = Instant.now().toString()
            // Search for the location
            val locations = openMeteoClient.searchLocation(args.location)
            if (locations.isEmpty()) {
                return Result(
                    locationName = args.location,
                    forecast = "Location not found",
                    date = date,
                    granularity = Granularity.DAILY,
                )
            }

            val location = locations.first()
            val forecastDays = args.days.coerceIn(1, 7)

            // Get the weather forecast
            val forecast =
                openMeteoClient.getWeatherForecast(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    forecastDays = forecastDays,
                )

            // Format the forecast based on granularity
            val formattedForecast = formatDailyForecast(forecast, date)

            return Result(
                locationName = location.name,
                locationCountry = location.country,
                forecast = formattedForecast,
                date = date,
                granularity = Granularity.DAILY,
            )
        }

        private fun formatDailyForecast(forecast: WeatherForecast, date: String): String {
            val daily = forecast.daily ?: return "No daily forecast data available"

            val startDate = date.ifBlank { LocalDate.now(UTC_ZONE).toString() }

            val startIndex = daily.time.indexOfFirst { it >= startDate }.coerceAtLeast(0)

            return buildString {
                for (i in startIndex until daily.time.size) {
                    val dateStr = daily.time[i]
                    val maxTemp = daily.temperature2mMax?.getOrNull(i)?.toString() ?: "N/A"
                    val minTemp = daily.temperature2mMin?.getOrNull(i)?.toString() ?: "N/A"
                    val weatherCode = daily.weatherCode?.getOrNull(i)
                    val weatherDesc = getWeatherDescription(weatherCode)
                    val precipSum = daily.precipitationSum?.getOrNull(i)?.toString() ?: "0"

                    append("$dateStr: $weatherDesc, ")
                    append("Temperature: $minTemp°C to $maxTemp°C, ")
                    append("Precipitation: $precipSum mm")

                    if (i < daily.time.size - 1) {
                        append("\n")
                    }
                }
            }
        }

        private fun getWeatherDescription(code: Int?): String {
            return when (code) {
                0 -> "Clear sky"
                1 -> "Mainly clear"
                2 -> "Partly cloudy"
                3 -> "Overcast"
                45,
                48 -> "Fog"
                51,
                53,
                55 -> "Drizzle"
                56,
                57 -> "Freezing drizzle"
                61,
                63,
                65 -> "Rain"
                66,
                67 -> "Freezing rain"
                71,
                73,
                75 -> "Snow fall"
                77 -> "Snow grains"
                80,
                81,
                82 -> "Rain showers"
                85,
                86 -> "Snow showers"
                95 -> "Thunderstorm"
                96,
                99 -> "Thunderstorm with hail"
                else -> "Unknown"
            }
        }
    }
}
