package com.m3sv.plainupnp.presentation.onboarding.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.material.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.m3sv.plainupnp.common.ThemeManager
import com.m3sv.plainupnp.common.util.finishApp
import com.m3sv.plainupnp.common.util.pass
import com.m3sv.plainupnp.compose.ActivityNotFoundIndicator
import com.m3sv.plainupnp.compose.AppTheme
import com.m3sv.plainupnp.compose.FadedBackground
import com.m3sv.plainupnp.compose.LifecycleIndicator
import com.m3sv.plainupnp.compose.util.isDarkTheme
import com.m3sv.plainupnp.data.upnp.UriWrapper
import com.m3sv.plainupnp.interfaces.LifecycleManager
import com.m3sv.plainupnp.interfaces.LifecycleState
import com.m3sv.plainupnp.presentation.onboarding.ActivityNotFoundIndicatorState
import com.m3sv.plainupnp.presentation.onboarding.OnboardingViewModel
import com.m3sv.plainupnp.presentation.onboarding.screen.SelectFoldersScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ConfigureFolderActivity : ComponentActivity() {

    @Inject
    lateinit var themeManager: ThemeManager

    @Inject
    lateinit var lifecycleManager: LifecycleManager

    private val viewModel: OnboardingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val contentUris: List<UriWrapper> by viewModel.contentUris.collectAsState()
            val theme by themeManager.theme.collectAsState()
            val activityNotFound: ActivityNotFoundIndicatorState by viewModel.activityNotFound.collectAsState()
            val lifecycleState: LifecycleState by lifecycleManager.lifecycleState.collectAsState()

            val pickDirectoryLauncher =
                rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocumentTree()) { uri ->
                    if (uri != null) {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        viewModel.saveUri()
                    }
                }

            AppTheme(theme.isDarkTheme()) {
                Surface {
                    SelectFoldersScreen(
                        contentUris = contentUris,
                        selectDirectory = { pickDirectoryLauncher.launch(null) },
                        onReleaseUri = viewModel::releaseUri,
                        onBackClick = ::finish
                    )

                    LifecycleIndicator(lifecycleState = lifecycleState, ::finishApp)

                    Crossfade(targetState = activityNotFound) { state ->
                        when (state) {
                            ActivityNotFoundIndicatorState.SHOW -> FadedBackground {
                                ActivityNotFoundIndicator { viewModel.dismissActivityNotFound() }
                            }
                            ActivityNotFoundIndicatorState.DISMISS -> pass
                        }
                    }
                }
            }
        }
    }
}
