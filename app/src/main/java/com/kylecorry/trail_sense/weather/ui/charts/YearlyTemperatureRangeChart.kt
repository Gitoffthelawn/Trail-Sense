package com.kylecorry.trail_sense.weather.ui.charts

import com.kylecorry.andromeda.core.system.Resources
import com.kylecorry.andromeda.core.ui.Colors.withAlpha
import com.kylecorry.ceres.chart.Chart
import com.kylecorry.ceres.chart.data.FullAreaChartLayer
import com.kylecorry.ceres.chart.data.LineChartLayer
import com.kylecorry.ceres.chart.data.ScatterChartLayer
import com.kylecorry.sol.math.Range
import com.kylecorry.sol.math.SolMath
import com.kylecorry.sol.math.Vector2
import com.kylecorry.sol.units.Temperature
import com.kylecorry.sol.units.TemperatureUnits
import com.kylecorry.trail_sense.shared.colors.AppColor
import java.time.LocalDate


class YearlyTemperatureRangeChart(
    private val chart: Chart,
    private val onClick: (date: LocalDate) -> Unit
) {

    private var year = 2000

    private val lowLine = LineChartLayer(emptyList(), AppColor.Blue.color) {
        onClick(LocalDate.ofYearDay(year, it.x.toInt()))
        true
    }

    private val highLine = LineChartLayer(emptyList(), AppColor.Red.color) {
        onClick(LocalDate.ofYearDay(year, it.x.toInt()))
        true
    }

    private val dewPointLine = LineChartLayer(emptyList(), AppColor.Purple.color) {
        onClick(LocalDate.ofYearDay(year, it.x.toInt()))
        true
    }

    private val humidityLine = LineChartLayer(emptyList(), AppColor.Green.color) {
        onClick(LocalDate.ofYearDay(year, it.x.toInt()))
        true
    }

    private val highlight = ScatterChartLayer(
        emptyList(),
        Resources.androidTextColorPrimary(chart.context),
        8f
    )

    private val freezingArea = FullAreaChartLayer(
        0f,
        -100f,
        AppColor.Gray.color.withAlpha(50)
    )

    init {
        chart.configureXAxis(
            labelCount = 5,
            drawGridLines = true,
            labelFormatter = MonthChartLabelFormatter(chart.context, year)
        )
        chart.configureYAxis(labelCount = 5, drawGridLines = true)
        chart.plot(freezingArea, lowLine, highLine, dewPointLine, humidityLine, highlight)
    }

    fun highlight(date: LocalDate) {
        val x = date.dayOfYear
        val low = lowLine.data.firstOrNull { it.x.toInt() == x }
        val high = highLine.data.firstOrNull { it.x.toInt() == x }
        val humidity = humidityLine.data.firstOrNull { it.x.toInt() == x }
        val dew = dewPointLine.data.firstOrNull { it.x.toInt() == x }
        highlight.data = listOfNotNull(low, high, humidity, dew)
    }

    fun plot(
        data: List<Pair<LocalDate, Range<Temperature>>>,
        dewPoints: List<Pair<LocalDate, Temperature>>,
        units: TemperatureUnits,
        humidity: List<Pair<LocalDate, Float>>
    ) {
        val freezing = Temperature.celsius(0f).convertTo(units)
        val lows = data.map {
            Vector2(
                it.first.dayOfYear.toFloat(),
                it.second.start.convertTo(units).temperature
            )
        }
        val highs = data.map {
            Vector2(
                it.first.dayOfYear.toFloat(),
                it.second.end.convertTo(units).temperature
            )
        }
        val dews = dewPoints.map {
            Vector2(
                it.first.dayOfYear.toFloat(),
                it.second.convertTo(units).temperature
            )
        }
        year = data.firstOrNull()?.first?.year ?: 2000
        val range = Chart.getYRange(lows + highs + dews, 5f, 10f)
        val humidityData = humidity.map {
            Vector2(
                it.first.dayOfYear.toFloat(),
                SolMath.map(it.second, 0f, 100f, range.start, range.end)
            )
        }
        chart.configureYAxis(
            labelCount = 5,
            drawGridLines = true,
            minimum = range.start,
            maximum = range.end
        )
        chart.configureXAxis(
            labelCount = 5,
            drawGridLines = true,
            labelFormatter = MonthChartLabelFormatter(chart.context, year)
        )
        freezingArea.top = freezing.temperature
        freezingArea.bottom = range.start
        lowLine.data = lows
        highLine.data = highs
        dewPointLine.data = dews
        humidityLine.data = humidityData
    }
}