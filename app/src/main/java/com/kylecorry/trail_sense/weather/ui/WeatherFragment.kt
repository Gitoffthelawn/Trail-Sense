package com.kylecorry.trail_sense.weather.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.kylecorry.andromeda.alerts.dialog
import com.kylecorry.andromeda.alerts.toast
import com.kylecorry.andromeda.core.system.Resources
import com.kylecorry.andromeda.core.topics.generic.asLiveData
import com.kylecorry.andromeda.core.topics.generic.replay
import com.kylecorry.andromeda.core.ui.setCompoundDrawables
import com.kylecorry.andromeda.fragments.BoundFragment
import com.kylecorry.andromeda.fragments.inBackground
import com.kylecorry.trail_sense.shared.sensors.observe
import com.kylecorry.sol.units.Pressure
import com.kylecorry.sol.units.PressureUnits
import com.kylecorry.sol.units.Reading
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.databinding.ActivityWeatherBinding
import com.kylecorry.trail_sense.quickactions.WeatherQuickActionBinder
import com.kylecorry.trail_sense.shared.CustomUiUtils
import com.kylecorry.trail_sense.shared.FeatureState
import com.kylecorry.trail_sense.shared.FormatService
import com.kylecorry.trail_sense.shared.Units
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.alerts.ResettableLoadingIndicator
import com.kylecorry.trail_sense.shared.alerts.ViewLoadingIndicator
import com.kylecorry.trail_sense.shared.extensions.getOrNull
import com.kylecorry.trail_sense.shared.extensions.isDebug
import com.kylecorry.trail_sense.shared.extensions.onIO
import com.kylecorry.trail_sense.shared.extensions.onMain
import com.kylecorry.trail_sense.shared.permissions.RequestRemoveBatteryRestrictionCommand
import com.kylecorry.trail_sense.weather.domain.CurrentWeather
import com.kylecorry.trail_sense.weather.domain.WeatherObservation
import com.kylecorry.trail_sense.weather.infrastructure.WeatherLogger
import com.kylecorry.trail_sense.weather.infrastructure.commands.ChangeWeatherFrequencyCommand
import com.kylecorry.trail_sense.weather.infrastructure.subsystem.WeatherSubsystem
import com.kylecorry.trail_sense.weather.ui.charts.HumidityChart
import com.kylecorry.trail_sense.weather.ui.charts.PressureChart
import com.kylecorry.trail_sense.weather.ui.charts.TemperatureChart
import com.kylecorry.trail_sense.weather.ui.dialogs.ShowHighLowTemperatureDialogCommand
import com.kylecorry.trail_sense.weather.ui.dialogs.ShowHistoricTemperatureDisclaimerCommand
import com.kylecorry.trail_sense.weather.ui.fields.AlertWeatherField
import com.kylecorry.trail_sense.weather.ui.fields.CloudWeatherField
import com.kylecorry.trail_sense.weather.ui.fields.FrontWeatherField
import com.kylecorry.trail_sense.weather.ui.fields.HistoricTemperatureRangeWeatherField
import com.kylecorry.trail_sense.weather.ui.fields.HumidityWeatherField
import com.kylecorry.trail_sense.weather.ui.fields.PressureSystemWeatherField
import com.kylecorry.trail_sense.weather.ui.fields.PressureTendencyWeatherField
import com.kylecorry.trail_sense.weather.ui.fields.PressureWeatherField
import com.kylecorry.trail_sense.weather.ui.fields.TemperatureWeatherField
import java.time.Duration
import java.time.Instant

class WeatherFragment : BoundFragment<ActivityWeatherBinding>() {

    private var useSeaLevelPressure = false
    private var units = PressureUnits.Hpa

    private val prefs by lazy { UserPreferences(requireContext()) }
    private val temperatureUnits by lazy { prefs.temperatureUnits }

    private lateinit var chart: PressureChart

    private val formatService by lazy { FormatService.getInstance(requireContext()) }

    private var history: List<WeatherObservation> = listOf()
    private var rawHistory: List<Reading<Pressure>> = listOf()

    private val weatherSubsystem by lazy { WeatherSubsystem.getInstance(requireContext()) }
    private var weather: CurrentWeather? = null
    private val loadingIndicator by lazy {
        ResettableLoadingIndicator(
            ViewLoadingIndicator(binding.weatherUpdating)
        )
    }

    private val logger by lazy {
        WeatherLogger(
            requireContext(),
            Duration.ofSeconds(30),
            Duration.ofMillis(500),
            loadingIndicator
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        WeatherQuickActionBinder(
            this,
            binding,
            prefs.weather
        ).bind()

        chart = PressureChart(binding.chart) { timeAgo, pressure ->
            if (timeAgo == null || pressure == null) {
                binding.pressureMarker.isVisible = false
            } else {
                val formatted = formatService.formatPressure(
                    Pressure(pressure, units),
                    Units.getDecimalPlaces(units)
                )
                binding.pressureMarker.text = getString(
                    R.string.pressure_reading_time_ago,
                    formatted,
                    formatService.formatDuration(timeAgo, false)
                )
                binding.pressureMarker.isVisible = true
            }
        }

        observe(weatherSubsystem.weatherChanged) {
            updateWeather()
        }

        weatherSubsystem.weatherMonitorState.replay().asLiveData().observe(viewLifecycleOwner) {
            updateStatusBar()
        }

        weatherSubsystem.weatherMonitorFrequency.replay().asLiveData().observe(viewLifecycleOwner) {
            updateStatusBar()
        }

        binding.weatherPlayBar.setOnSubtitleClickListener {
            ChangeWeatherFrequencyCommand(requireContext()) { onUpdate() }.execute()
        }

        binding.weatherPlayBar.setOnPlayButtonClickListener {
            when (weatherSubsystem.weatherMonitorState.getOrNull()) {
                FeatureState.Unavailable -> toast(getString(R.string.weather_monitoring_disabled))
                FeatureState.On -> weatherSubsystem.disableMonitor()
                FeatureState.Off -> {
                    weatherSubsystem.enableMonitor()
                    RequestRemoveBatteryRestrictionCommand(this).execute()
                }
                null -> {}
            }
        }
    }

    private fun updateStatusBar() {
        binding.weatherPlayBar.setState(
            weatherSubsystem.weatherMonitorState.getOrNull() ?: FeatureState.Off,
            weatherSubsystem.weatherMonitorFrequency.getOrNull()
        )
    }

    override fun onResume() {
        super.onResume()
        loadingIndicator.reset()
        logger.start()
        useSeaLevelPressure = prefs.weather.useSeaLevelPressure
        units = prefs.pressureUnits
        ShowHistoricTemperatureDisclaimerCommand(requireContext()).execute()
        updateWeather()
    }

    override fun onPause() {
        super.onPause()
        logger.stop()
        loadingIndicator.hide()
    }

    private fun updateList() {
        if (!isBound) return
        val weather = weather ?: return

        val fields = listOf(
            // Alerts
            AlertWeatherField(weather.prediction.alerts),

            // Pressure
            PressureWeatherField(weather.observation?.pressure),
            PressureTendencyWeatherField(weather.pressureTendency),

            // Temperature
            TemperatureWeatherField(weather.observation?.temperature) { showTemperatureChart() },
            HistoricTemperatureRangeWeatherField(
                weather.prediction.temperature?.low,
                weather.prediction.temperature?.high
            ) { showTemperatureForecast() },

            // Humidity
            HumidityWeatherField(weather.observation?.humidity) { showHumidityChart() },
            CloudWeatherField(weather.clouds),

            // System
            PressureSystemWeatherField(weather.observation?.pressure),
            FrontWeatherField(weather.prediction.front),
        )

        val items = fields.mapNotNull { it.getListItem(requireContext()) }

        binding.weatherList.setItems(items)
    }


    private fun updateWeather() {
        inBackground {
            onIO {
                history = weatherSubsystem.getHistory().filter {
                    Duration.between(it.time, Instant.now()) <= prefs.weather.pressureHistory
                }

                loadRawWeatherReadings()

                weather = weatherSubsystem.getWeather()
            }
            onMain {
                update()
            }
        }
    }

    private suspend fun loadRawWeatherReadings() {
        if (isDebug()) {
            if (prefs.weather.useSeaLevelPressure) {
                val raw = weatherSubsystem.getRawHistory().filter {
                    Duration.between(
                        it.time,
                        Instant.now()
                    ) <= prefs.weather.pressureHistory
                }
                val useTemperature = prefs.weather.seaLevelFactorInTemp
                rawHistory = raw.map {
                    Reading(it.value.seaLevel(useTemperature), it.time)
                }
            }
        }
    }

    private fun update() {
        if (!isBound) return
        displayPressureChart(history, rawHistory)
        updateList()
        inBackground {
            updateForecast()
        }
    }

    private fun displayPressureChart(
        readings: List<WeatherObservation>,
        rawReadings: List<Reading<Pressure>>
    ) {
        val displayReadings =
            readings.map { it.pressureReading() }.map { it.copy(value = it.value.convertTo(units)) }
        if (displayReadings.isNotEmpty()) {
            chart.plot(
                displayReadings,
                rawReadings.map { it.copy(value = it.value.convertTo(units)) }.ifEmpty { null })
        }
    }

    private suspend fun updateForecast() {
        if (!isBound) return
        val weather = weather ?: return
        val prediction = weather.prediction
        onMain {
            binding.weatherTitle.title.text = formatService.formatWeather(prediction.primaryHourly)
            binding.weatherTitle.title.setOnClickListener {
                if (prediction.hourly.isNotEmpty()) {
                    val conditions = prediction.hourly.joinToString("\n") {
                        formatService.formatWeather(it)
                    }
                    dialog(getString(R.string.weather), conditions, cancelText = null)
                }
            }
            binding.weatherTitle.title.setCompoundDrawables(
                size = Resources.dp(requireContext(), 24f).toInt(),
                left = formatService.getWeatherImage(prediction.primaryHourly)
            )
            val arrival =
                formatService.formatWeatherArrival(weather.prediction.hourlyArrival)
            val then = getString(
                R.string.then_weather,
                formatService.formatWeather(prediction.primaryDaily).lowercase()
            )

            val hourlySameAsDaily = prediction.primaryDaily == prediction.primaryHourly

            binding.weatherTitle.subtitle.text =
                if (arrival.isNotEmpty() && (prediction.primaryDaily == null || hourlySameAsDaily)) {
                    arrival
                } else if (arrival.isNotEmpty()) {
                    "$arrival, $then"
                } else if (!hourlySameAsDaily) {
                    then
                } else {
                    ""
                }
            binding.weatherTitle.subtitle.isVisible =
                binding.weatherTitle.subtitle.text.isNotEmpty()
        }
    }

    private fun showHumidityChart() {
        val readings =
            history.filter { it.humidity != null }.map { Reading(it.humidity!!, it.time) }
        if (readings.size < 2) {
            return
        }
        val readingDuration = Duration.between(readings.first().time, Instant.now())
        CustomUiUtils.showChart(
            this, getString(
                R.string.humidity_history,
                formatService.formatDuration(readingDuration, true)
            )
        ) {
            val chart = HumidityChart(it)
            chart.plot(readings)
        }
    }

    private fun showTemperatureForecast() {
        inBackground {
            ShowHighLowTemperatureDialogCommand(this@WeatherFragment).execute()
        }
    }

    private fun showTemperatureChart() {
        val readings = history.map {
            Reading(it.temperature.convertTo(temperatureUnits).temperature, it.time)
        }
        if (readings.size < 2) {
            return
        }
        val readingDuration = Duration.between(readings.first().time, Instant.now())
        CustomUiUtils.showChart(
            this, getString(
                R.string.temperature_history,
                formatService.formatDuration(readingDuration, true)
            )
        ) {
            val chart = TemperatureChart(it)
            chart.plot(readings)
        }
    }


    override fun generateBinding(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): ActivityWeatherBinding {
        return ActivityWeatherBinding.inflate(layoutInflater, container, false)
    }

}
