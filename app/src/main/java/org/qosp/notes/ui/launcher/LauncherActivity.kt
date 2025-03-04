package org.qosp.notes.ui.launcher

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.qosp.notes.ui.MainActivity
import org.qosp.notes.ui.theme.QuillpadTheme

@AndroidEntryPoint
class LauncherActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val isCurrentVersionInstalled = viewModel.isCurrentVersionInstalled.firstOrNull() ?: false
            Log.d("LauncherActivity", "isNewInstall: ${!isCurrentVersionInstalled}.")
            if (isCurrentVersionInstalled) proceedToMainActivity(persistNewVersion = false)
        }
        setContent {
            QuillpadTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LauncherScreen { lifecycleScope.launch { proceedToMainActivity(persistNewVersion = true) } }
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

    @Composable
    fun LauncherScreen(onNextClicked: () -> Unit) {
        val context = LocalContext.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    Toast.makeText(context, "Hello there", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Text("Hello")
            }
            Button(onClick = onNextClicked, modifier = Modifier.fillMaxWidth()) {
                Text("Next")
            }
        }
    }
}
