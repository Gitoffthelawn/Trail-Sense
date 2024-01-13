package com.kylecorry.trail_sense.tools.flashlight.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.kylecorry.andromeda.core.ui.setOnProgressChangeListener
import com.kylecorry.andromeda.fragments.BoundFragment
import com.kylecorry.andromeda.torch.ScreenTorch
import com.kylecorry.sol.math.SolMath.map
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.databinding.FragmentToolScreenFlashlightBinding
import com.kylecorry.trail_sense.shared.preferences.PreferencesSubsystem

class FragmentToolScreenFlashlight : BoundFragment<FragmentToolScreenFlashlightBinding>() {

    private val flashlight by lazy { ScreenTorch(requireActivity().window) }
    private val cache by lazy { PreferencesSubsystem.getInstance(requireContext()).preferences }

    override fun generateBinding(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentToolScreenFlashlightBinding {
        return FragmentToolScreenFlashlightBinding.inflate(layoutInflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.offBtn.setOnClickListener {
            flashlight.off()
            requireActivity().onBackPressed()
        }

        if (cache.getBoolean("cache_red_light") == null) {
            cache.putBoolean("cache_red_light", false)
        }

        if (cache.getBoolean("cache_red_light") == true) {
            binding.screenFlashlight.setBackgroundColor(Color.RED)
            binding.redWhiteSwitcher.setBackgroundColor(Color.WHITE)
        } else {
            binding.screenFlashlight.setBackgroundColor(Color.WHITE)
            binding.redWhiteSwitcher.setBackgroundColor(Color.RED)
        }

        binding.redWhiteSwitcher.setOnClickListener {
            if (cache.getBoolean("cache_red_light") == true) {
                binding.screenFlashlight.setBackgroundColor(Color.WHITE)
                binding.redWhiteSwitcher.setBackgroundColor(Color.RED)
                cache.putBoolean("cache_red_light", false)
            } else {
                binding.screenFlashlight.setBackgroundColor(Color.RED)
                binding.redWhiteSwitcher.setBackgroundColor(Color.WHITE)
                cache.putBoolean("cache_red_light", true)
            }
        }

        binding.brightnessSeek.setOnProgressChangeListener { progress, isFromUser ->
            if (isFromUser) {
                setBrightness(progress)
            }
        }
    }

    private fun turnOn(){
        setBrightness(cache.getInt(getString(R.string.pref_screen_torch_brightness)) ?: 100)
    }

    private fun turnOff(){
        flashlight.off()
    }

    private fun setBrightness(percent: Int){
        binding.brightnessSeek.progress = percent
        cache.putInt(getString(R.string.pref_screen_torch_brightness), percent)
        flashlight.on(map(percent / 100f, 0f, 1f, 0.1f, 1f))
    }

    override fun onResume() {
        super.onResume()
        turnOn()
    }

    override fun onPause() {
        super.onPause()
        turnOff()
    }

}