package org.qosp.notes.ui.launcher

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.qosp.notes.BuildConfig
import org.qosp.notes.ui.MainActivity
import org.qosp.notes.ui.theme.QuillpadTheme

@AndroidEntryPoint
class LauncherActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG.not()) {
            lifecycleScope.launch {
                val isCurrentVersionInstalled = viewModel.isCurrentVersionInstalled.firstOrNull() == true
                Log.d("LauncherActivity", "isNewInstall: ${!isCurrentVersionInstalled}.")
                if (isCurrentVersionInstalled) proceedToMainActivity(persistNewVersion = false)
            }
        }
        setContent {
            QuillpadTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WelcomeScreen {
                        lifecycleScope.launch { proceedToMainActivity(persistNewVersion = true) }
                    }
                }
            }
        }
    }

    private suspend fun proceedToMainActivity(persistNewVersion: Boolean) {
        if (persistNewVersion) viewModel.updateLastInstalledVersion()
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish() // Finish LauncherActivity after starting MainActivity
    }
}
