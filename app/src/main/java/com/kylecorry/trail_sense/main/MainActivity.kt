package com.kylecorry.trail_sense.main

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MenuItem
import androidx.core.os.bundleOf
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.kylecorry.andromeda.core.system.GeoUri
import com.kylecorry.andromeda.core.system.Screen
import com.kylecorry.andromeda.fragments.AndromedaActivity
import com.kylecorry.andromeda.fragments.ColorTheme
import com.kylecorry.andromeda.sense.Sensors
import com.kylecorry.sol.units.Coordinate
import com.kylecorry.trail_sense.ColorFilterConstraintLayout
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.astronomy.domain.AstronomyService
import com.kylecorry.trail_sense.backup.BackupService
import com.kylecorry.trail_sense.onboarding.OnboardingActivity
import com.kylecorry.trail_sense.receivers.RestartServicesCommand
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.commands.ComposedCommand
import com.kylecorry.trail_sense.shared.preferences.PreferencesSubsystem
import com.kylecorry.trail_sense.shared.sensors.LocationSubsystem
import com.kylecorry.trail_sense.shared.views.ErrorBannerView
import com.kylecorry.trail_sense.tools.battery.infrastructure.commands.PowerSavingModeAlertCommand
import com.kylecorry.trail_sense.tools.clinometer.ui.ClinometerFragment
import com.kylecorry.trail_sense.tools.flashlight.infrastructure.FlashlightSubsystem
import com.kylecorry.trail_sense.tools.flashlight.ui.FragmentToolFlashlight
import com.kylecorry.trail_sense.tools.whitenoise.infrastructure.WhiteNoiseService
import com.kylecorry.trail_sense.volumeactions.ClinometerLockVolumeAction
import com.kylecorry.trail_sense.volumeactions.FlashlightToggleVolumeAction
import com.kylecorry.trail_sense.volumeactions.VolumeAction


class MainActivity : AndromedaActivity() {

    //    private lateinit var navController: NavController
    private lateinit var bottomNavigation: BottomNavigationView
    val errorBanner: ErrorBannerView by lazy { findViewById(R.id.error_banner) }

    private lateinit var userPrefs: UserPreferences
    private val cache by lazy { PreferencesSubsystem.getInstance(this).preferences }

    val navigation = MyNavController(supportFragmentManager, R.id.fragment_holder)

    private val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        ExceptionHandler.initialize(this)

        userPrefs = UserPreferences(this)
        val mode = when (userPrefs.theme) {
            UserPreferences.Theme.Light -> ColorTheme.Light
            UserPreferences.Theme.Dark, UserPreferences.Theme.Black, UserPreferences.Theme.Night -> ColorTheme.Dark
            UserPreferences.Theme.System -> ColorTheme.System
            UserPreferences.Theme.SunriseSunset -> sunriseSunsetTheme()
        }
        setTheme(R.style.AppTheme)
        setColorTheme(mode, userPrefs.useDynamicColors)
        super.onCreate(savedInstanceState)

        Screen.setAllowScreenshots(window, !userPrefs.privacy.isScreenshotProtectionOn)

        setContentView(R.layout.activity_main)

        if (userPrefs.theme == UserPreferences.Theme.Night) {
            val root = findViewById<ColorFilterConstraintLayout>(R.id.color_filter)
            root.setColorFilter(PorterDuffColorFilter(Color.RED, PorterDuff.Mode.MULTIPLY))
        }

        bottomNavigation = findViewById(R.id.bottom_navigation)

        Navigation.initialize(navigation)

        bottomNavigation.setupWithMyNavController(
            navigation, mapOf(
                Navigation.NAVIGATION to R.id.action_navigation,
                Navigation.WEATHER to R.id.action_weather,
                Navigation.ASTRONOMY to R.id.action_astronomy,
                Navigation.TOOLS to R.id.action_experimental_tools,
                Navigation.SETTINGS to R.id.action_settings
            ), R.id.action_navigation
        )

        if (userPrefs.theme == UserPreferences.Theme.Black || userPrefs.theme == UserPreferences.Theme.Night) {
            window.decorView.rootView.setBackgroundColor(Color.BLACK)
            bottomNavigation.setBackgroundColor(Color.BLACK)
        }

        if (cache.getBoolean(getString(R.string.pref_onboarding_completed)) != true) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        requestPermissions(permissions) {
            startApp()
        }
    }

    override fun onResume() {
        super.onResume()
        FlashlightSubsystem.getInstance(this).startSystemMonitor()
    }

    override fun onPause() {
        super.onPause()
        FlashlightSubsystem.getInstance(this).stopSystemMonitor()
    }

    private fun startApp() {
        errorBanner.dismissAll()

        if (cache.getBoolean(BackupService.RECENTLY_BACKED_UP_KEY) == true) {
            cache.remove(BackupService.RECENTLY_BACKED_UP_KEY)
            navigation.navigate(Navigation.SETTINGS)
        } else if (navigation.currentRoute == null) {
            navigation.navigate(Navigation.NAVIGATION)
        }

        ComposedCommand(
            ShowDisclaimerCommand(this),
            PowerSavingModeAlertCommand(this),
            RestartServicesCommand(this),
        ).execute()

        if (!Sensors.hasBarometer(this)) {
            val item: MenuItem = bottomNavigation.menu.findItem(R.id.action_weather)
            item.isVisible = false
        }

        handleIntentAction(intent)
    }

    private fun handleIntentAction(intent: Intent) {
        val intentData = intent.data
        if (intent.scheme == "geo" && intentData != null) {
            val geo = GeoUri.from(intentData)
            bottomNavigation.selectedItemId = R.id.action_navigation
            if (geo != null) {
                val bundle = bundleOf("initial_location" to geo)
                navigation.navigate(Navigation.BEACON_LIST, bundle)
            }
        } else if ((intent.type?.startsWith("image/") == true || intent.type?.startsWith("application/pdf") == true)) {
            bottomNavigation.selectedItemId = R.id.action_experimental_tools
            val intentUri = intent.clipData?.getItemAt(0)?.uri
            val bundle = bundleOf("map_intent_uri" to intentUri)
            navigation.navigate(Navigation.MAP_LIST, bundle)
        } else if (MyNavController.hasDeepLink(intent)) {
            navigation.navigateDeepLink(intent)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent ?: return
        setIntent(intent)
        handleIntentAction(intent)
    }

    private fun sunriseSunsetTheme(): ColorTheme {
        val astronomyService = AstronomyService()
        val location = LocationSubsystem.getInstance(this).location
        if (location == Coordinate.zero) {
            return ColorTheme.System
        }
        val isSunUp = astronomyService.isSunUp(location)
        return if (isSunUp) {
            ColorTheme.Light
        } else {
            ColorTheme.Dark
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            return onVolumePressed(isVolumeUp = false, isButtonPressed = true)
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            return onVolumePressed(isVolumeUp = true, isButtonPressed = true)
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            return onVolumePressed(isVolumeUp = false, isButtonPressed = false)
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            return onVolumePressed(isVolumeUp = true, isButtonPressed = false)
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun onVolumePressed(isVolumeUp: Boolean, isButtonPressed: Boolean): Boolean {
        if (!shouldOverrideVolumePress()) {
            return false
        }

        val action =
            (if (isVolumeUp) getVolumeUpAction() else getVolumeDownAction()) ?: return false

        if (isButtonPressed) {
            action.onButtonPress()
        } else {
            action.onButtonRelease()
        }

        return true
    }

    private fun shouldOverrideVolumePress(): Boolean {
        val excluded = listOf(Navigation.WHISTLE, Navigation.WHITE_NOISE)
        if (excluded.contains(navigation.currentRoute)) {
            return false
        }

        // If the white noise service is running, don't override the volume buttons so the user can adjust the volume
        if (WhiteNoiseService.isRunning) {
            return false
        }

        return true
    }


    private fun getVolumeDownAction(): VolumeAction? {

        val fragment = getFragment()
        if (userPrefs.clinometer.lockWithVolumeButtons && fragment is ClinometerFragment) {
            return ClinometerLockVolumeAction(fragment)
        }


        if (userPrefs.flashlight.toggleWithVolumeButtons) {
            return FlashlightToggleVolumeAction(
                this,
                if (fragment is FragmentToolFlashlight) fragment else null
            )
        }

        return null
    }

    private fun getVolumeUpAction(): VolumeAction? {
        val fragment = getFragment()
        if (userPrefs.clinometer.lockWithVolumeButtons && fragment is ClinometerFragment) {
            return ClinometerLockVolumeAction(fragment)
        }

        if (userPrefs.flashlight.toggleWithVolumeButtons) {
            return FlashlightToggleVolumeAction(
                this,
                if (fragment is FragmentToolFlashlight) fragment else null
            )
        }

        return null
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, MainActivity::class.java)
        }
    }

}
