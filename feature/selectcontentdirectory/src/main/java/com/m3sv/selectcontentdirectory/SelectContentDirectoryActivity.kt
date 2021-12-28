package com.m3sv.selectcontentdirectory

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.m3sv.plainupnp.Router
import com.m3sv.plainupnp.common.ThemeManager
import com.m3sv.plainupnp.common.util.finishApp
import com.m3sv.plainupnp.common.util.pass
import com.m3sv.plainupnp.compose.AppTheme
import com.m3sv.plainupnp.compose.LifecycleIndicator
import com.m3sv.plainupnp.compose.OnePane
import com.m3sv.plainupnp.compose.OneTitle
import com.m3sv.plainupnp.compose.OneToolbar
import com.m3sv.plainupnp.compose.util.isDarkTheme
import com.m3sv.plainupnp.interfaces.LifecycleManager
import com.m3sv.selectcontentdirectory.SelectContentDirectoryViewModel.ViewState
import dagger.hilt.android.AndroidEntryPoint
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
            val viewState: ViewState by viewModel.viewState.collectAsState()
            val currentTheme by themeManager.theme.collectAsState()

            LaunchedEffect(viewState.navigationResult) {
                when (val result = viewState.navigationResult) {
                    is ViewState.NavigationResult.Error -> handleSelectDirectoryError(result.message)
                    is ViewState.NavigationResult.Success -> handleSelectDirectorySuccess()
                    null -> pass
                }

                viewModel.consumeNavigationResult()
            }

            AppTheme(currentTheme.isDarkTheme()) {
                Scaffold {
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
                    }) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            if (viewState.contentDirectories.isEmpty())
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
                                        itemsIndexed(viewState.contentDirectories) { index, item ->
                                            Column(modifier = Modifier.clickable(enabled = viewState.loadingDeviceId == null) {
                                                viewModel.selectContentDirectory(item.id)
                                            }) {
                                                Text(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(16.dp),
                                                    text = item.title
                                                )

                                                val height = 4.dp
                                                Box(modifier = Modifier.height(height)) {
                                                    androidx.compose.animation.AnimatedVisibility(visible = item.id == viewState.loadingDeviceId) {
                                                        LinearProgressIndicator(
                                                            modifier = Modifier
                                                                .height(height)
                                                                .fillMaxWidth()
                                                        )
                                                    }
                                                }

                                                if (viewState.contentDirectories.size > 1 && index != viewState.contentDirectories.size - 1) {
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

    private fun handleSelectDirectoryError(message: String) {
        Toast
            .makeText(this, message, Toast.LENGTH_SHORT)
            .show()
    }

    override fun onBackPressed() {
        finishAndRemoveTask()
    }
}
