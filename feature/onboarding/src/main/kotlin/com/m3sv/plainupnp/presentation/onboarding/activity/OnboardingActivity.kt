package com.m3sv.plainupnp.presentation.onboarding.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.m3sv.plainupnp.common.ApplicationMode
import com.m3sv.plainupnp.common.ThemeManager
import com.m3sv.plainupnp.common.ThemeOption
import com.m3sv.plainupnp.compose.AppTheme
import com.m3sv.plainupnp.compose.GreetingScreen
import com.m3sv.plainupnp.compose.SelectApplicationModeScreen
import com.m3sv.plainupnp.compose.util.isDarkTheme
import com.m3sv.plainupnp.data.upnp.UriWrapper
import com.m3sv.plainupnp.presentation.onboarding.OnboardingManager
import com.m3sv.plainupnp.presentation.onboarding.OnboardingScreen
import com.m3sv.plainupnp.presentation.onboarding.OnboardingViewModel
import com.m3sv.plainupnp.presentation.onboarding.R
import com.m3sv.plainupnp.presentation.onboarding.screen.*
import com.m3sv.plainupnp.presentation.onboarding.selecttheme.SelectThemeScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class OnboardingActivity : ComponentActivity() {

    @Inject
    lateinit var onboardingManager: OnboardingManager

    @Inject
    lateinit var themeManager: ThemeManager

    private val viewModel: OnboardingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LayoutContainer() }
    }

    @Composable
    private fun LayoutContainer() {
        val contentUris by viewModel.contentUris.collectAsState()
        val currentScreen by viewModel.currentScreen.collectAsState()
        val currentTheme by themeManager.theme.collectAsState()
        val containerState by viewModel.containerState.collectAsState()
        val containersClickCallback by remember {
            mutableStateOf(object : ContainerSwitchCallbacks {
                override fun onImageSwitch() {
                    viewModel.onImageSwitch()
                }

                override fun onVideoSwitch() {
                    viewModel.onVideoSwitch()
                }

                override fun onAudioSwitch() {
                    viewModel.onAudioSwitch()
                }
            })
        }

        Content(
            currentTheme = currentTheme,
            currentScreen = currentScreen,
            onSelectTheme = viewModel::onSelectTheme,
            onSelectApplicationMode = viewModel::onSelectMode,
            contentUris = contentUris,
            onNextClick = viewModel::onNavigateNext,
            onBackClick = viewModel::onNavigateBack,
            containerState = containerState,
            containerSwitchCallbacks = containersClickCallback
        )
    }

    @Composable
    private fun Content(
        currentTheme: ThemeOption,
        currentScreen: OnboardingScreen,
        contentUris: List<UriWrapper> = listOf(),
        containerState: ContainerState,
        containerSwitchCallbacks: ContainerSwitchCallbacks,
        onSelectTheme: (ThemeOption) -> Unit,
        onSelectApplicationMode: (ApplicationMode) -> Unit,
        onNextClick: () -> Unit,
        onBackClick: () -> Unit,
    ) {
        val openDirectoryLauncher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocumentTree()) { uri ->
                if (uri != null) {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    viewModel.saveUri()
                }
            }

        AppTheme(currentTheme.isDarkTheme()) {
            Surface {
                when (currentScreen) {
                    OnboardingScreen.Greeting -> GreetingScreen(onNextClick)
                    OnboardingScreen.SelectTheme -> SelectThemeScreen(
                        titleText = stringResource(R.string.set_theme_label),
                        buttonText = stringResource(id = R.string.next),
                        selectedTheme = currentTheme,
                        onThemeOptionSelected = onSelectTheme,
                        onClick = onNextClick,
                        onBackClick = onBackClick
                    )
                    OnboardingScreen.SelectMode -> SelectApplicationModeScreen(
                        initialMode = ApplicationMode.Streaming,
                        onNextClick = onNextClick,
                        onBackClick = onBackClick,
                        onItemSelected = onSelectApplicationMode
                    )
                    OnboardingScreen.StoragePermission -> StoragePermissionScreen(
                        onBackClick = onBackClick,
                        onPermissionGranted = onNextClick,
                        onPermissionRefused = ::openSettings
                    )
                    OnboardingScreen.SelectPreconfiguredContainers -> SelectPreconfiguredContainersScreen(
                        onBackClick = onBackClick,
                        onNextClick = onNextClick,
                        containerState = containerState,
                        containersCallback = containerSwitchCallbacks
                    )
                    OnboardingScreen.SelectDirectories -> SelectFoldersScreen(
                        contentUris = contentUris,
                        selectDirectory = {
                            openDirectoryLauncher.launch(null)
                        },
                        onBackClick = onBackClick,
                        onNext = onNextClick,
                        onReleaseUri = viewModel::releaseUri,
                    )
                    OnboardingScreen.Finish -> onboardingManager.completeOnboarding(this)
                }
            }
        }
    }

    private fun openSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName")
        )
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    override fun onBackPressed() {
        viewModel.onNavigateBack()
    }
}
