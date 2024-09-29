package com.kylecorry.trail_sense.tools.tides.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.kylecorry.andromeda.core.time.CoroutineTimer
import com.kylecorry.andromeda.core.ui.flatten
import com.kylecorry.andromeda.fragments.BoundFragment
import com.kylecorry.andromeda.fragments.inBackground
import com.kylecorry.andromeda.list.ListView
import com.kylecorry.sol.science.oceanography.Tide
import com.kylecorry.sol.time.Time.toZonedDateTime
import com.kylecorry.sol.units.Distance
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.databinding.FragmentCreateTideBinding
import com.kylecorry.trail_sense.databinding.ListItemTideEntryBinding
import com.kylecorry.trail_sense.shared.CustomUiUtils
import com.kylecorry.trail_sense.shared.DistanceUtils
import com.kylecorry.trail_sense.shared.FormatService
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.debugging.isDebug
import com.kylecorry.trail_sense.shared.extensions.promptIfUnsavedChanges
import com.kylecorry.trail_sense.shared.withId
import com.kylecorry.trail_sense.tools.guide.infrastructure.UserGuideUtils
import com.kylecorry.trail_sense.tools.tides.domain.TideTable
import com.kylecorry.trail_sense.tools.tides.domain.TideTableIsDirtySpecification
import com.kylecorry.trail_sense.tools.tides.domain.waterlevel.TideEstimator
import com.kylecorry.trail_sense.tools.tides.infrastructure.persistence.TideTableRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZonedDateTime

class CreateTideFragment : BoundFragment<FragmentCreateTideBinding>() {

    private val formatService by lazy { FormatService.getInstance(requireContext()) }
    private var editingId: Long = 0
    private var editingTide: TideTable? = null

    private lateinit var tideTimesList: ListView<TideEntry>
    private var tides = mutableListOf<TideEntry>()

    private val tideRepo by lazy { TideTableRepo.getInstance(requireContext()) }
    private val prefs by lazy { UserPreferences(requireContext()) }
    private val units by lazy { prefs.baseDistanceUnits }

    private var backCallback: OnBackPressedCallback? = null

    private val intervalometer = CoroutineTimer {
        binding.createTideTitle.rightButton.isVisible = formIsValid()
    }

    private var estimateAlgorithm by state(TideEstimator.Clock)
    private var lunitidalIntervalAuto by state(true)

    override fun generateBinding(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentCreateTideBinding {
        return FragmentCreateTideBinding.inflate(layoutInflater, container, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editingId = arguments?.getLong("edit_tide_id") ?: 0L
    }

    override fun onResume() {
        super.onResume()
        intervalometer.interval(20)
    }

    override fun onPause() {
        intervalometer.stop()
        super.onPause()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.createTideTitle.leftButton.flatten()
        binding.createTideTitle.leftButton.setOnClickListener {
            UserGuideUtils.showGuide(this, R.raw.guide_tool_tides)
        }

        CustomUiUtils.setButtonState(binding.createTideTitle.rightButton, true)

        binding.estimateAlgorithmSpinner.setHint(getString(R.string.estimate_method))
        binding.estimateAlgorithmSpinner.setItems(
            listOfNotNull(
                getString(R.string.tide_clock),
                getString(R.string.lunitidal_interval),
                if (isDebug()) getString(R.string.harmonic) + " (!! NEED TO MANUALLY INSERT INTO DB !!)" else null
            )
        )
        binding.estimateAlgorithmSpinner.setSelection(0)

        binding.estimateAlgorithmSpinner.setOnItemSelectedListener { position ->
            estimateAlgorithm = TideEstimator.entries.withId((position?.toLong() ?: 0L) + 1)
                ?: TideEstimator.Clock
        }

        binding.lunitidalIntervalMethod.addOnButtonCheckedListener { _, _, _ ->
            lunitidalIntervalAuto = binding.lunitidalIntervalMethod.checkedButtonId == R.id.lunitidal_interval_method_auto
        }
        binding.lunitidalIntervalMethod.check(R.id.lunitidal_interval_method_auto)
        binding.lunitidalIntervalDuration.showSeconds = false

        binding.tideFrequencyDiurnal.text = buildSpannedString {
            bold {
                append("1")
            }
            append("    ")
            append(getString(R.string.tide_diurnal))
        }

        binding.tideFrequencySemidiurnal.text = buildSpannedString {
            bold {
                append("2")
            }
            append("    ")
            append(getString(R.string.tide_semidiurnal))
        }

        binding.tideFrequency.check(R.id.tide_frequency_semidiurnal)

        tideTimesList = ListView(binding.tideTimes, R.layout.list_item_tide_entry) { view, tide ->
            val itemBinding = ListItemTideEntryBinding.bind(view)

            itemBinding.tideType.text =
                if (tide.isHigh) getString(R.string.high_tide_letter) else getString(
                    R.string.low_tide_letter
                )

            itemBinding.tideType.setOnClickListener {
                tide.isHigh = !tide.isHigh
                itemBinding.tideType.text =
                    if (tide.isHigh) getString(R.string.high_tide_letter) else getString(
                        R.string.low_tide_letter
                    )
            }

            itemBinding.delete.setOnClickListener {
                tides.remove(tide)
                tideTimesList.setData(tides)
                CustomUiUtils.snackbar(
                    this,
                    getString(R.string.tide_deleted),
                    action = getString(R.string.undo)
                ) {
                    tides.add(tide)
                    tideTimesList.setData(tides)
                }
            }

            itemBinding.tideTime.text = getString(R.string.time_not_set)

            tide.time?.let {
                itemBinding.tideTime.text = formatService.formatDateTime(
                    it,
                    false,
                    abbreviateMonth = true
                )
            }

            itemBinding.tideTime.setOnClickListener {
                CustomUiUtils.pickDatetime(
                    requireContext(),
                    prefs.use24HourTime,
                    tide.time?.toLocalDateTime() ?: LocalDateTime.now()
                ) {
                    if (it != null) {
                        tide.time = it.toZonedDateTime()
                        itemBinding.tideTime.text = formatService.formatDateTime(
                            it.toZonedDateTime(),
                            false,
                            abbreviateMonth = true
                        )
                    }
                }
            }

            val initialHeight = tide.height
            itemBinding.tideHeight.text = if (initialHeight == null) {
                getString(R.string.dash)
            } else {
                formatService.formatDistance(initialHeight, 2)
            }

            itemBinding.tideHeight.setOnClickListener {
                val units = formatService.sortDistanceUnits(DistanceUtils.humanDistanceUnits)
                CustomUiUtils.pickDistance(
                    requireContext(),
                    units,
                    tide.height,
                    getString(R.string.height)
                ) { distance, cancelled ->
                    if (!cancelled) {
                        tide.height = distance
                        itemBinding.tideHeight.text = if (distance == null) {
                            getString(R.string.dash)
                        } else {
                            formatService.formatDistance(distance, 2)
                        }
                    }
                }
            }
        }

        tideTimesList.addLineSeparator()

        tides.clear()
        if (editingId != 0L) {
            inBackground {
                withContext(Dispatchers.IO) {
                    editingTide = tideRepo.getTideTable(editingId)
                }
                withContext(Dispatchers.Main) {
                    if (editingTide != null) {
                        fillExistingTideValues(editingTide!!)
                    } else {
                        editingId = 0L
                    }
                }
            }
        } else {
            tides.add(TideEntry(true, null, null))
            tideTimesList.setData(tides)
        }

        binding.addTideEntry.setOnClickListener {
            tides.add(TideEntry(true, null, null))
            tideTimesList.setData(tides)
            tideTimesList.scrollToPosition(tides.lastIndex)
        }

        binding.createTideTitle.rightButton.setOnClickListener {
            val tide = getTide()
            if (tide != null) {
                inBackground {
                    withContext(Dispatchers.IO) {
                        tideRepo.addTideTable(tide)
                    }

                    withContext(Dispatchers.Main) {
                        backCallback?.remove()
                        findNavController().navigateUp()
                    }
                }
            }
        }

        backCallback = promptIfUnsavedChanges(this::hasChanges)

    }


    private fun fillExistingTideValues(tide: TideTable) {
        binding.tideName.setText(tide.name)
        binding.tideLocation.coordinate = tide.location
        binding.tideFrequency.check(if (tide.isSemidiurnal) R.id.tide_frequency_semidiurnal else R.id.tide_frequency_diurnal)
        tides.addAll(tide.tides.map {
            val h = it.height
            TideEntry(
                it.isHigh,
                it.time,
                if (h != null) Distance.meters(h).convertTo(units) else null
            )
        })
        tideTimesList.setData(tides)
        binding.estimateAlgorithmSpinner.setSelection(tide.estimator.id.toInt() - 1)
        binding.lunitidalIntervalMethod.check(if (tide.lunitidalInterval != null) R.id.lunitidal_interval_method_manual else R.id.lunitidal_interval_method_auto)
        binding.lunitidalIntervalDuration.duration = tide.lunitidalInterval
    }

    private fun formIsValid(): Boolean {
        return getTide() != null
    }

    private fun getTide(): TideTable? {
        val tides = tides.mapNotNull {
            val time = it.time ?: return@mapNotNull null
            Tide(
                time,
                it.isHigh,
                it.height?.meters()?.distance
            )
        }

        val estimateAlgorithm =
            TideEstimator.entries.withId(binding.estimateAlgorithmSpinner.selectedItemPosition.toLong() + 1)
                ?: TideEstimator.Clock

        if (editingId != 0L && editingTide == null) {
            return null
        }

        val lunitidalInterval = if (estimateAlgorithm == TideEstimator.LunitidalInterval && !lunitidalIntervalAuto) {
            binding.lunitidalIntervalDuration.duration
        } else {
            null
        }

        // TODO: Eventually this will check for harmonics
        if (
            // Tide clock requires at least one tide
            (tides.isEmpty() && estimateAlgorithm == TideEstimator.Clock) ||
            // Lunitidal interval requires either a manual interval or at least one tide
            (tides.isEmpty() && estimateAlgorithm == TideEstimator.LunitidalInterval && lunitidalInterval == null)
            // Harmonics doesn't require anything right now
        ) {
            return null
        }

        val rawName = binding.tideName.text?.toString()
        val name = if (rawName.isNullOrBlank()) null else rawName
        val location = binding.tideLocation.coordinate

        val isSemidiurnal = binding.tideFrequency.checkedButtonId == R.id.tide_frequency_semidiurnal

        return TideTable(
            editingId,
            tides,
            name,
            location,
            isSemidiurnal = isSemidiurnal,
            isVisible = editingTide?.isVisible ?: true,
            estimator = estimateAlgorithm,
            lunitidalInterval = lunitidalInterval
        )
    }

    private fun hasChanges(): Boolean {
        val specification = TideTableIsDirtySpecification(editingTide)
        return specification.isSatisfiedBy(getTide())
    }

    override fun onUpdate() {
        super.onUpdate()
        binding.lunitidalIntervalMethod.isVisible = estimateAlgorithm == TideEstimator.LunitidalInterval
        binding.lunitidalIntervalLabel.isVisible = estimateAlgorithm == TideEstimator.LunitidalInterval
        binding.lunitidalIntervalDuration.isVisible = estimateAlgorithm == TideEstimator.LunitidalInterval && !lunitidalIntervalAuto
    }


    private data class TideEntry(
        var isHigh: Boolean,
        var time: ZonedDateTime?,
        var height: Distance?
    )

}