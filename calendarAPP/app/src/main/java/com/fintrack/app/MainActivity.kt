package com.fintrack.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import com.fintrack.app.ui.navigation.FinTrackNavGraph
import com.fintrack.app.ui.theme.FinTrackTheme
import com.fintrack.app.ui.tile.QuickExpenseTileService

class MainActivity : FragmentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private var quickEntryRequested by mutableStateOf(false)
    private var sharedUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        quickEntryRequested = intent?.action == QuickExpenseTileService.ACTION_QUICK_ENTRY
        sharedUrl = sharedUrlOf(intent)
        requestNotificationPermission()
        setContent {
            FinTrackTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    FinTrackNavGraph(
                        navController = navController,
                        openQuickEntryOnStart = quickEntryRequested,
                        openShareUrlOnStart = sharedUrl,
                        onShareUrlConsumed = { sharedUrl = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == QuickExpenseTileService.ACTION_QUICK_ENTRY) {
            quickEntryRequested = true
        }
        sharedUrlOf(intent)?.let { sharedUrl = it }
    }

    /** Texto compartido desde Mercado Libre / Amazon / Chrome. */
    private fun sharedUrlOf(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type != "text/plain") return null
        return intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
