package com.m3sv.plainupnp.presentation.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.snackbar.Snackbar
import com.m3sv.plainupnp.ThemeManager
import com.m3sv.plainupnp.common.util.doNothing
import com.m3sv.plainupnp.presentation.onboarding.ConfigureFolderActivity
import com.m3sv.plainupnp.upnp.manager.UpnpManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.LazyThreadSafetyMode.NONE

private enum class PreferenceKey(val tag: String) {
    VERSION("version"),
    RATE("rate"),
    GITHUB("github"),
    PRIVACY_POLICY("privacy_policy"),
    CONTACT_US("contact_us"),
    CONFIGURE_FOLDERS("configure_folders");

    companion object {
        fun byTag(tag: String): PreferenceKey? = values().firstOrNull { it.tag == tag }
    }
}

@AndroidEntryPoint
class SettingsFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    Preference.OnPreferenceClickListener {

    @Inject
    lateinit var upnpManager: UpnpManager

    @Inject
    lateinit var themeManager: ThemeManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        PreferenceKey.values().forEach { preference ->
            findPreference<Preference>(preference.tag)?.onPreferenceClickListener = this
        }

        findPreference<Preference>(VERSION)?.summary = appVersion
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        preferenceManager.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    private val setThemeKey by lazy(NONE) { getString(R.string.set_theme_key) }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        when (key) {
            setThemeKey -> themeManager.setDefaultNightMode()
        }
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        val key = PreferenceKey.byTag(preference.key)

        when (key) {
            PreferenceKey.VERSION -> doNothing
            PreferenceKey.RATE -> rateApplication()
            PreferenceKey.GITHUB -> github()
            PreferenceKey.PRIVACY_POLICY -> privacyPolicy()
            PreferenceKey.CONTACT_US -> openEmail()
            PreferenceKey.CONFIGURE_FOLDERS -> requireActivity().startActivity(Intent(requireContext(),
                ConfigureFolderActivity::class.java))
            null -> doNothing
        }

        return key != null
    }

    private fun openEmail() {
        val result = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("$MAIL_TO$EMAIL")
        }.startIntentIfAble()

        if (!result) {
            Snackbar
                .make(requireView(), EMAIL, Snackbar.LENGTH_LONG)
                .setAction(android.R.string.copy) { copyEmailToClipboard() }
                .show()
        }
    }

    private fun copyEmailToClipboard() {
        val clipboardManager =
            requireActivity().getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val data = ClipData.newPlainText("PlainUPnP developer email", EMAIL)
        clipboardManager.setPrimaryClip(data)
    }

    private fun privacyPolicy() = Intent(
        Intent.ACTION_VIEW,
        Uri.parse(PRIVACY_POLICY_LINK)
    ).startIntentIfAble()

    private fun github() =
        Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_LINK)).startIntentIfAble()

    private fun rateApplication() = try {
        playMarketIntent()
    } catch (e: Throwable) {
        playMarketFallbackIntent()
    }

    private fun playMarketIntent() =
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$MARKET_PREFIX$packageName")))

    private fun playMarketFallbackIntent() =
        Intent(Intent.ACTION_VIEW, Uri.parse("$PLAY_STORE_PREFIX$packageName")).startIntentIfAble()

    private fun Intent.startIntentIfAble(): Boolean =
        if (resolveActivity(requireContext().packageManager) != null) {
            startActivity(this)
            true
        } else {
            false
        }

    private companion object {
        private const val VERSION = "version"

        private const val MAIL_TO = "mailto:"
        private const val EMAIL = "m3sv.dev@gmail.com"
        private const val GITHUB_LINK = "https://github.com/m3sv/PlainUPnP"
        private const val PRIVACY_POLICY_LINK =
            "https://www.freeprivacypolicy.com/privacy/view/bf0284b77ca1af94b405030efd47d254"
        private const val PLAY_STORE_PREFIX = "http://play.google.com/store/apps/details?id="
        private const val MARKET_PREFIX = "market://details?id="

        private val Fragment.packageName
            get() = requireActivity().packageName

        private val Fragment.appVersion: String
            get() = requireActivity()
                .packageManager
                .getPackageInfo(requireActivity().packageName, 0)
                .versionName
    }
}
