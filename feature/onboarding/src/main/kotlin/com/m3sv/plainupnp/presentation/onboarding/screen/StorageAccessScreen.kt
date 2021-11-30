package com.m3sv.plainupnp.presentation.onboarding.screen

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.m3sv.plainupnp.compose.*

@Composable
fun StoragePermissionScreen(
    onBackClick: () -> Unit,
    onPermissionGranted: () -> Unit,
    onPermissionRefused: () -> Unit
) {
    var hasPermission: Boolean? by remember {
        mutableStateOf(null)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted)
            onPermissionGranted()

        hasPermission = isGranted
    }

    OnePane(viewingContent = {
        OneTitle(text = "Storage permission")
        OneToolbar(onBackClick = onBackClick) {}
    }) {
        Column {
            OneSubtitle(
                text = "To stream your files we need to get storage access permission",
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            OneContainedButton(
                text = "Grant permission",
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(bottom = if (hasPermission == false) 8.dp else 24.dp)
            ) {
                launcher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }

            if (hasPermission == false) {
                OneContainedButton(
                    text = "Navigate to settings", modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 8.dp)
                ) {
                    onPermissionRefused()
                }

                OneOutlinedButton(
                    text = "Skip",
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 24.dp),
                    onClick = onPermissionGranted
                )
            }
        }
    }
}
