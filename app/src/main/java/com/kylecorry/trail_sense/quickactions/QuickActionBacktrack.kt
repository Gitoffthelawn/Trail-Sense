package com.kylecorry.trail_sense.quickactions

import android.widget.ImageButton
import androidx.fragment.app.Fragment
import com.kylecorry.andromeda.alerts.toast
import com.kylecorry.andromeda.core.topics.generic.ITopic
import com.kylecorry.andromeda.core.topics.generic.replay
import com.kylecorry.andromeda.fragments.AndromedaFragment
import com.kylecorry.andromeda.fragments.inBackground
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.main.Navigation
import com.kylecorry.trail_sense.navigation.paths.infrastructure.subsystem.BacktrackSubsystem
import com.kylecorry.trail_sense.shared.FeatureState
import com.kylecorry.trail_sense.shared.requireMyNavigation
import com.kylecorry.trail_sense.shared.permissions.RequestRemoveBatteryRestrictionCommand
import com.kylecorry.trail_sense.shared.permissions.requestBacktrackPermission

class QuickActionBacktrack(btn: ImageButton, private val andromedaFragment: AndromedaFragment) :
    TopicQuickAction(btn, andromedaFragment, hideWhenUnavailable = false) {

    private val backtrack = BacktrackSubsystem.getInstance(context)

    override fun onCreate() {
        super.onCreate()
        button.setImageResource(R.drawable.ic_tool_backtrack)
        button.setOnClickListener {
            when (backtrack.getState()) {
                FeatureState.On -> backtrack.disable()
                FeatureState.Off -> {
                    andromedaFragment.requestBacktrackPermission { success ->
                        if (success) {
                            andromedaFragment.inBackground {
                                backtrack.enable(true)
                                RequestRemoveBatteryRestrictionCommand(andromedaFragment).execute()
                            }
                        }
                    }
                }

                FeatureState.Unavailable -> fragment.toast(context.getString(R.string.backtrack_disabled_low_power_toast))
            }
        }

        button.setOnLongClickListener {
            fragment.requireMyNavigation().navigate(Navigation.PATH_LIST)
            true
        }
    }

    override val state: ITopic<FeatureState> = backtrack.state.replay()

}