package com.m3sv.selectcontentdirectory

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.m3sv.plainupnp.Router
import com.m3sv.plainupnp.common.ThemeManager
import com.m3sv.plainupnp.common.util.finishApp
import com.m3sv.plainupnp.compose.*
import com.m3sv.plainupnp.compose.util.isDarkTheme
import com.m3sv.plainupnp.data.upnp.UpnpDevice
import com.m3sv.plainupnp.interfaces.LifecycleManager
import com.m3sv.plainupnp.upnp.manager.Result
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class SelectContentDirectoryActivity : ComponentActivity() {

    @Inject
    lateinit var themeManager: ThemeManager

    @Inject
    lateinit var lifecycleManager: LifecycleManager

    private val viewModel by viewModels<SelectContentDirectoryViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val contentDirectories by viewModel.contentDirectories.collectAsState()
            val currentTheme by themeManager.theme.collectAsState()
            var loadingDeviceDisplay: UpnpDevice? by remember { mutableStateOf(null) }

            fun UpnpDevice.isLoading(): Boolean = loadingDeviceDisplay != null && loadingDeviceDisplay == this

            AppTheme(currentTheme.isDarkTheme()) {
                Surface {
                    OnePane(viewingContent = {
                        OneTitle(text = "Select content directory")
                        OneToolbar {
                            Spacer(modifier = Modifier.weight(1f))

                            Image(
                                modifier = Modifier
                                    .clickable { handleGearClick() }
                                    .padding(8.dp),
                                painter = painterResource(id = R.drawable.ic_settings),
                                contentDescription = null
                            )
                        }
                    }
                    ) {
                        Card(
                            shape = RoundedCornerShape(AppTheme.cornerRadius),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            if (contentDirectories.isEmpty())
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = 24.dp,
                                        vertical = 24.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        getString(R.string.content_directory_search_message),
                                        Modifier.weight(1f),
                                        style = MaterialTheme.typography.body1
                                    )
                                    CircularProgressIndicator(Modifier.size(32.dp))
                                }
                            else
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth(),
                                    content = {
                                        itemsIndexed(contentDirectories) { index, item ->
                                            Column(modifier = Modifier.clickable(enabled = loadingDeviceDisplay == null) {
                                                // TODO move this to ViewModel
                                                loadingDeviceDisplay = item

                                                lifecycleScope.launch(Dispatchers.IO) {
                                                    when (viewModel.selectContentDirectory(item)) {
                                                        Result.Success -> handleSelectDirectorySuccess()
                                                        Result.Error.GENERIC,
                                                        Result.Error.RENDERER_NOT_SELECTED,
                                                        Result.Error.AV_SERVICE_NOT_FOUND -> withContext(Dispatchers.Main) { handleSelectDirectoryError() }
                                                    }

                                                    loadingDeviceDisplay = null
                                                }
                                            }) {
                                                Text(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(16.dp),
                                                    text = item.friendlyName
                                                )

                                                val height = 4.dp
                                                Box(modifier = Modifier.height(height)) {
                                                    androidx.compose.animation.AnimatedVisibility(visible = item.isLoading()) {
                                                        LinearProgressIndicator(
                                                            modifier = Modifier
                                                                .height(height)
                                                                .fillMaxWidth()
                                                        )
                                                    }
                                                }

                                                if (contentDirectories.size > 1 && index != contentDirectories.size - 1) {
                                                    Divider(modifier = Modifier.fillMaxWidth())
                                                }
                                            }
                                        }
                                    }
                                )
                        }
                    }

                    val lifecycleState by lifecycleManager.lifecycleState.collectAsState()

                    LifecycleIndicator(lifecycleState = lifecycleState, ::finishApp)
                }
            }
        }
    }

    private fun handleGearClick() {
        startActivity(Intent(this, SelectApplicationModeActivity::class.java))
    }

    private fun handleSelectDirectorySuccess() {
        startActivity((application as Router).getMainActivityIntent(this))
    }

    private fun handleSelectDirectoryError() {
        Toast
            .makeText(this, "Failed to connect to content directory", Toast.LENGTH_SHORT)
            .show()
    }

    override fun onBackPressed() {
        finishAndRemoveTask()
    }
}
