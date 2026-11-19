package com.yuanyue.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yuanyue.reader.data.AppearanceSettings
import com.yuanyue.reader.data.UpdateInfo
import com.yuanyue.reader.ui.components.UpdateDialog
import com.yuanyue.reader.ui.navigation.AppNavHost
import com.yuanyue.reader.ui.quickstart.QuickStartScreen
import com.yuanyue.reader.ui.theme.YuanyueTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as YuanyueApplication).container
        setContent {
            val appearance by container.settingsRepository.appearance
                .collectAsState(initial = AppearanceSettings())
            var onboardingDone by remember { mutableStateOf<Boolean?>(null) }
            var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                onboardingDone = container.settingsRepository.onboardingCompleted.first()
            }

            LaunchedEffect(onboardingDone) {
                if (onboardingDone == true) {
                    updateInfo = container.updateChecker.check(BuildConfig.VERSION_NAME)
                }
            }

            YuanyueTheme(appearance = appearance) {
                when {
                    onboardingDone == null -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }

                    onboardingDone == false -> QuickStartScreen(
                        container = container,
                        onFinish = {
                            scope.launch {
                                container.settingsRepository.setOnboardingCompleted()
                                onboardingDone = true
                            }
                        },
                        onSkip = {
                            scope.launch {
                                container.settingsRepository.setOnboardingCompleted()
                                onboardingDone = true
                            }
                        },
                    )

                    else -> AppNavHost(container = container)
                }

                updateInfo?.let { info ->
                    UpdateDialog(
                        info = info,
                        onDismiss = { updateInfo = null },
                    )
                }
            }
        }
    }
}
