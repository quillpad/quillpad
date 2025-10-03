package org.qosp.notes.ui.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.qosp.notes.BuildConfig
import org.qosp.notes.ui.MainActivity
import org.qosp.notes.ui.theme.QuillpadTheme

class LauncherActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.TESTLAB_BUILD) {
            // Skip the welcome screen in Firebase TestLab builds
            proceedToMainActivity()
            return
        }

        lifecycleScope.launch {
            val whatsNewToShow = viewModel.whatsNewToShow().first()
            if (whatsNewToShow != null) {
                setContent {
                    QuillpadTheme {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            WelcomeScreen(whatsNewItem = whatsNewToShow) {
                                runBlocking { viewModel.setLatestWhatsNewId() }
                                proceedToMainActivity()
                            }
                        }
                    }
                }
            } else {
                proceedToMainActivity()
            }
        }
    }

    private fun proceedToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish() // Finish LauncherActivity after starting MainActivity
    }
}
