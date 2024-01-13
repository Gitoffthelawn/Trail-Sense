package com.kylecorry.trail_sense.quickactions

import com.kylecorry.trail_sense.databinding.ActivityWeatherBinding
import com.kylecorry.trail_sense.weather.infrastructure.WeatherPreferences
import com.kylecorry.trail_sense.weather.ui.WeatherFragment

class WeatherQuickActionBinder(
    private val fragment: WeatherFragment,
    private val binding: ActivityWeatherBinding,
    private val prefs: WeatherPreferences
) : IQuickActionBinder {

    override fun bind() {
        val factory = QuickActionFactory()
        val left = factory.create(prefs.leftButton, binding.weatherTitle.leftButton, fragment)
        val right = factory.create(prefs.rightButton, binding.weatherTitle.rightButton, fragment)
        left.bind(fragment)
        right.bind(fragment)
    }

}