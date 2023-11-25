package com.kylecorry.trail_sense.tools.clinometer.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.kylecorry.andromeda.alerts.dialog
import com.kylecorry.andromeda.alerts.toast
import com.kylecorry.andromeda.core.system.Resources
import com.kylecorry.andromeda.core.time.Throttle
import com.kylecorry.andromeda.core.ui.setCompoundDrawables
import com.kylecorry.andromeda.fragments.BoundFragment
import com.kylecorry.trail_sense.shared.sensors.observe
import com.kylecorry.andromeda.markdown.MarkdownService
import com.kylecorry.andromeda.pickers.Pickers
import com.kylecorry.andromeda.sense.clinometer.CameraClinometer
import com.kylecorry.andromeda.sense.clinometer.IClinometer
import com.kylecorry.andromeda.sense.clinometer.SideClinometer
import com.kylecorry.andromeda.sense.orientation.DeviceOrientation
import com.kylecorry.sol.science.geology.AvalancheRisk
import com.kylecorry.sol.science.geology.Geology
import com.kylecorry.sol.units.Distance
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.databinding.FragmentClinometerBinding
import com.kylecorry.trail_sense.shared.*
import com.kylecorry.trail_sense.shared.DistanceUtils.toRelativeDistance
import com.kylecorry.trail_sense.shared.haptics.HapticSubsystem
import com.kylecorry.trail_sense.shared.permissions.alertNoCameraPermission
import com.kylecorry.trail_sense.shared.permissions.requestCamera
import com.kylecorry.trail_sense.shared.sensors.SensorService
import java.time.Duration
import java.time.Instant
import kotlin.math.max
import kotlin.math.min

class ClinometerFragment : BoundFragment<FragmentClinometerBinding>() {

    private val sensorService by lazy { SensorService(requireContext()) }
    private val cameraClinometer by lazy { CameraClinometer(requireContext()) }
    private val sideClinometer by lazy { SideClinometer(requireContext()) }
    private val deviceOrientation by lazy { sensorService.getDeviceOrientationSensor() }
    private val prefs by lazy { UserPreferences(requireContext()) }
    private val markdown by lazy { MarkdownService(requireContext()) }
    private val formatter by lazy { FormatService.getInstance(requireContext()) }
    private val feedback by lazy {
        HapticSubsystem.getInstance(requireContext()).dial()
    }
    private val throttle = Throttle(20)
    private val hapticsEnabled by lazy { prefs.hapticsEnabled }

    private lateinit var clinometer: IClinometer

    private var slopeIncline: Float? = null
    private var slopeAngle: Float? = null
    private var startIncline: Float = 0f
    private var touchTime = Instant.now()

    private var lockState = ClinometerLockState.Unlocked
    private val holdDuration = Duration.ofMillis(200)

    private var distanceAway: Distance? = null
    private var knownHeight: Distance? = null

    private var useCamera = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        clinometer = getClinometer()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toast(getString(R.string.set_inclination_instructions))

        CustomUiUtils.setButtonState(binding.clinometerTitle.leftButton, false)
        CustomUiUtils.setButtonState(binding.clinometerTitle.rightButton, false)

        binding.cameraViewHolder.clipToOutline = true

        binding.clinometerTitle.leftButton.setOnClickListener {
            if (useCamera) {
                binding.camera.stop()
                binding.clinometerTitle.leftButton.setImageResource(R.drawable.ic_camera)
                CustomUiUtils.setButtonState(binding.clinometerTitle.leftButton, false)
                useCamera = false
                clinometer = getClinometer()
            } else {
                requestCamera { hasPermission ->
                    if (hasPermission) {
                        useCamera = true
                        binding.camera.start()
                        binding.clinometerTitle.leftButton.setImageResource(R.drawable.ic_screen_flashlight)
                        CustomUiUtils.setButtonState(binding.clinometerTitle.leftButton, false)
                        clinometer = getClinometer()
                    } else {
                        alertNoCameraPermission()
                    }
                }
            }
        }

        binding.clinometerTitle.rightButton.setOnClickListener {
            askForHeightOrDistance()
        }

        binding.root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                updateLockState(PressState.Down)
            } else if (event.action == MotionEvent.ACTION_UP) {
                updateLockState(PressState.Up)
            }
            true
        }

        observe(sideClinometer) { updateUI() }
        observe(cameraClinometer) { updateUI() }
        observe(deviceOrientation) { updateUI() }
    }

    fun updateLockState(pressState: PressState) {
        when (lockState) {
            ClinometerLockState.Unlocked -> {
                if (pressState == PressState.Down && isOrientationValid()) {
                    setStartAngle()
                    lockState = ClinometerLockState.PartiallyLocked
                }
            }
            ClinometerLockState.PartiallyLocked -> {
                if (pressState == PressState.Up) {
                    if (Duration.between(touchTime, Instant.now()) < holdDuration) {
                        // No sweep angle
                        clearStartAngle()
                    }

                    setEndAngle()

                    lockState = ClinometerLockState.Locked
                }
            }
            ClinometerLockState.Locked -> {
                if (pressState == PressState.Down && isOrientationValid()) {
                    setStartAngle()
                    clearEndAngle()
                    lockState = ClinometerLockState.PartiallyUnlocked
                } else if (pressState == PressState.Down) {
                    clearStartAngle()
                    clearEndAngle()
                    lockState = ClinometerLockState.Unlocked
                }
            }
            ClinometerLockState.PartiallyUnlocked -> {
                if (pressState == PressState.Up) {
                    lockState = if (Duration.between(touchTime, Instant.now()) < holdDuration) {
                        // User wants to unlock
                        clearStartAngle()
                        clearEndAngle()
                        ClinometerLockState.Unlocked
                    } else {
                        // User wants to do another sweep angle
                        setEndAngle()
                        ClinometerLockState.Locked
                    }
                }
            }
        }
    }

    private fun askForHeightOrDistance() {
        Pickers.item(
            requireContext(), getString(R.string.measure), listOf(
                getString(R.string.height),
                getString(R.string.distance)
            ), when {
                distanceAway != null -> 0
                knownHeight != null -> 1
                else -> -1
            }
        ) {
            if (it != null) {
                when (it) {
                    0 -> measureHeightPrompt()
                    1 -> measureDistancePrompt()
                }
            }
        }
    }

    private fun measureHeightPrompt() {
        val units = formatter.sortDistanceUnits(DistanceUtils.hikingDistanceUnits)
        CustomUiUtils.pickDistance(
            requireContext(),
            units,
            distanceAway,
            getString(R.string.clinometer_measure_height_title)
        ) { distance, _ ->
            if (distance != null) {
                distanceAway = distance
                knownHeight = null
                CustomUiUtils.setButtonState(binding.clinometerTitle.rightButton, true)
                if (!prefs.clinometer.measureHeightInstructionsSent) {
                    dialog(
                        getString(R.string.instructions),
                        markdown.toMarkdown(
                            getString(
                                R.string.clinometer_measure_height_instructions,
                                formatter.formatDistance(distance, 2, false)
                            )
                        ),
                        cancelText = null
                    ) {
                        prefs.clinometer.measureHeightInstructionsSent = true
                    }
                }
            }
        }
    }

    private fun measureDistancePrompt() {
        val units = formatter.sortDistanceUnits(DistanceUtils.humanDistanceUnits)

        CustomUiUtils.pickDistance(
            requireContext(),
            units,
            knownHeight,
            getString(R.string.clinometer_measure_distance_title),
            hint = getString(R.string.height),
            showFeetAndInches = true
        ) { distance, _ ->
            if (distance != null) {
                knownHeight = distance
                distanceAway = null
                CustomUiUtils.setButtonState(binding.clinometerTitle.rightButton, true)
                if (!prefs.clinometer.measureDistanceInstructionsSent) {
                    dialog(
                        getString(R.string.instructions),
                        markdown.toMarkdown(
                            getString(
                                R.string.clinometer_measure_distance_instructions,
                                formatter.formatDistance(distance, 2, false)
                            )
                        ),
                        cancelText = null
                    ) {
                        prefs.clinometer.measureDistanceInstructionsSent = true
                    }
                }
            }
        }
    }

    private fun clearStartAngle() {
        startIncline = 0f
        binding.cameraClinometer.startInclination = null
        binding.clinometer.startAngle = null
    }

    private fun setStartAngle() {
        touchTime = Instant.now()
        startIncline = clinometer.incline
        binding.cameraClinometer.startInclination = startIncline
        binding.clinometer.startAngle = clinometer.angle
    }

    private fun setEndAngle() {
        slopeAngle = clinometer.angle
        slopeIncline = clinometer.incline
    }

    private fun clearEndAngle() {
        slopeAngle = null
        slopeIncline = null
    }

    override fun onResume() {
        super.onResume()
        if (distanceAway == null && knownHeight == null) {
            distanceAway = prefs.clinometer.baselineDistance
            CustomUiUtils.setButtonState(
                binding.clinometerTitle.rightButton,
                distanceAway != null
            )
        }
    }

    override fun onPause() {
        super.onPause()
        if (useCamera) {
            binding.camera.stop()
            useCamera = false
            clinometer = getClinometer()
        }
        if (hapticsEnabled) {
            feedback.stop()
        }
    }

    private fun getClinometer(): IClinometer {
        return if (useCamera) {
            cameraClinometer
        } else {
            sideClinometer
        }
    }

    private fun updateUI() {

        if (throttle.isThrottled()) {
            return
        }

        val locked = isLocked()

        binding.clinometerTitle.title.setCompoundDrawables(
            Resources.dp(requireContext(), 24f).toInt(),
            right = if (locked) R.drawable.lock else null
        )

        CustomUiUtils.setImageColor(
            binding.clinometerTitle.title,
            Resources.androidTextColorPrimary(requireContext())
        )

        if (!isOrientationValid() && !locked) {
            binding.clinometerInstructions.isVisible = !useCamera
            binding.cameraClinometerInstructions.isVisible = useCamera
            binding.cameraViewHolder.isVisible = false
            binding.clinometer.isInvisible = true
            return
        }

        binding.clinometerInstructions.isVisible = false
        binding.cameraClinometerInstructions.isVisible = false
        binding.cameraViewHolder.isVisible = useCamera
        binding.clinometer.isInvisible = useCamera

        val angle = slopeAngle ?: clinometer.angle
        val incline = slopeIncline ?: clinometer.incline

        if (hapticsEnabled) {
            feedback.angle = angle
        }

        val avalancheRisk = Geology.getAvalancheRisk(incline)

        binding.clinometer.angle = angle
        binding.cameraClinometer.inclination = incline

        binding.clinometerTitle.title.text = formatter.formatDegrees(incline)
        binding.avalancheRisk.title = getAvalancheRiskString(avalancheRisk)

        binding.clinometerTitle.subtitle.text =
            getString(R.string.slope_amount, formatter.formatPercentage(getSlopePercent(incline)))

        val distanceAway = distanceAway
        val knownHeight = knownHeight

        when {
            distanceAway != null -> {
                binding.estimatedHeight.description = getString(R.string.height)
                binding.estimatedHeight.title = formatter.formatDistance(
                    getHeight(
                        distanceAway,
                        min(startIncline, incline),
                        max(startIncline, incline)
                    ).toRelativeDistance(),
                    1, false
                )
            }
            knownHeight != null -> {
                binding.estimatedHeight.description = getString(R.string.distance)
                binding.estimatedHeight.title = formatter.formatDistance(
                    getDistance(
                        knownHeight,
                        min(startIncline, incline),
                        max(startIncline, incline)
                    ).toRelativeDistance(),
                    1, false
                )
            }
            else -> {
                binding.estimatedHeight.title = getString(R.string.distance_unset)
            }
        }
    }

    private fun isLocked(): Boolean {
        return slopeAngle != null
    }

    private fun getSlopePercent(incline: Float): Float {
        return Geology.getSlopeGrade(incline)
    }

    private fun getAvalancheRiskString(risk: AvalancheRisk): String {
        return when (risk) {
            AvalancheRisk.Low -> getString(R.string.low)
            AvalancheRisk.Moderate -> getString(R.string.moderate)
            AvalancheRisk.High -> getString(R.string.high)
        }
    }

    private fun isOrientationValid(): Boolean {
        val invalidOrientations = if (useCamera) {
            listOf(
                DeviceOrientation.Orientation.Landscape,
                DeviceOrientation.Orientation.LandscapeInverse
            )
        } else {
            listOf(DeviceOrientation.Orientation.Flat, DeviceOrientation.Orientation.FlatInverse)
        }

        return !invalidOrientations.contains(deviceOrientation.orientation)
    }

    override fun generateBinding(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentClinometerBinding {
        return FragmentClinometerBinding.inflate(layoutInflater, container, false)
    }

    private fun getHeight(distanceAway: Distance, bottom: Float, top: Float): Distance {
        return Geology.getHeightFromInclination(
            distanceAway,
            bottom,
            top
        )
    }

    private fun getDistance(height: Distance, bottom: Float, top: Float): Distance {
        return Geology.getDistanceFromInclination(
            height,
            bottom,
            top
        )
    }

    private enum class ClinometerLockState {
        PartiallyUnlocked,
        Unlocked,
        PartiallyLocked,
        Locked
    }


}
