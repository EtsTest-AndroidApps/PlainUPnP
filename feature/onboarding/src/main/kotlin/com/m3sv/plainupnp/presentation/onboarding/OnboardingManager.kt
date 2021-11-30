package com.m3sv.plainupnp.presentation.onboarding

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.m3sv.plainupnp.common.preferences.PreferencesRepository
import kotlinx.coroutines.launch

class OnboardingManager(
    private val preferencesRepository: PreferencesRepository,
    private val onboardingCompletedListener: (Activity) -> Unit,
) {
    val isOnboardingCompleted
        get() = preferencesRepository.preferences.value.finishedOnboarding

    fun completeOnboarding(activity: ComponentActivity) {
        activity.lifecycleScope.launch {
            preferencesRepository.finishOnboarding()
            onboardingCompletedListener(activity)
            activity.finish()
        }
    }
}
