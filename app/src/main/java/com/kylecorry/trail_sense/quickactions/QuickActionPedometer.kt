package com.kylecorry.trail_sense.quickactions

import android.widget.ImageButton
import androidx.navigation.fragment.findNavController
import com.kylecorry.andromeda.core.topics.generic.ITopic
import com.kylecorry.andromeda.core.topics.generic.replay
import com.kylecorry.andromeda.fragments.AndromedaFragment
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.shared.FeatureState
import com.kylecorry.trail_sense.shared.extensions.getOrNull
import com.kylecorry.trail_sense.shared.permissions.alertNoActivityRecognitionPermission
import com.kylecorry.trail_sense.shared.permissions.requestActivityRecognition
import com.kylecorry.trail_sense.tools.pedometer.infrastructure.subsystem.PedometerSubsystem

class QuickActionPedometer(btn: ImageButton, private val andromedaFragment: AndromedaFragment) :
    TopicQuickAction(btn, andromedaFragment, hideWhenUnavailable = false) {

    private val pedometer = PedometerSubsystem.getInstance(context)

    override fun onCreate() {
        super.onCreate()
        button.setImageResource(R.drawable.steps)
        button.setOnClickListener {
            when (pedometer.state.getOrNull()) {
                FeatureState.On -> pedometer.disable()
                FeatureState.Off -> startStepCounter()
                else -> {
                    if (pedometer.isDisabledDueToPermissions()) {
                        startStepCounter()
                    }
                }
            }
        }

        button.setOnLongClickListener {
            fragment.findNavController().navigate(R.id.fragmentToolPedometer)
            true
        }
    }

    private fun startStepCounter() {
        andromedaFragment.requestActivityRecognition { hasPermission ->
            if (hasPermission) {
                pedometer.enable()
            } else {
                pedometer.disable()
                andromedaFragment.alertNoActivityRecognitionPermission()
            }
        }
    }

    override val state: ITopic<FeatureState> = pedometer.state.replay()

}