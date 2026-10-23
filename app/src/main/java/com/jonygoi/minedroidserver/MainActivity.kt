package com.jonygoi.minedroidserver

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import com.jonygoi.minedroidserver.server.ServerManager
import com.jonygoi.minedroidserver.theme.*
import com.jonygoi.minedroidserver.ui.*

enum class AppTab(val title: String, val icon: ImageVector) {
    DASHBOARD("Servidores", Icons.Default.Dns),
    CONSOLE("Terminal", Icons.Default.Terminal),
    CONFIG("Configurar", Icons.Default.Settings),
    OPTIMIZATION("Otimizar", Icons.Default.Speed)
}

enum class SubScreen {
    FILES,
    REMOTE
}

class MainActivity : ComponentActivity() {

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize server registry
        ServerManager.init(this)

        // Initialize and start Remote Web Server for cross-device host management
        com.jonygoi.minedroidserver.remote.RemoteWebServer.start(this)

        // Check & request notification permission for Foreground Service on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            MineDroidServerTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp() {
    var selectedTab by remember { mutableStateOf(AppTab.DASHBOARD) }
    var activeSubScreen by remember { mutableStateOf<SubScreen?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = MinecraftDarkSurface,
                contentColor = TextPrimary
            ) {
                AppTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab && activeSubScreen == null
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            selectedTab = tab
                            activeSubScreen = null
                        },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                tint = if (isSelected) MinecraftEmerald else TextSecondary
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                color = if (isSelected) MinecraftEmerald else TextSecondary
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MinecraftCardSurface
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MinecraftObsidian)
                .padding(innerPadding)
        ) {
            if (activeSubScreen != null) {
                when (activeSubScreen) {
                    SubScreen.FILES -> FileManagerScreen(
                        onBack = { activeSubScreen = null }
                    )
                    SubScreen.REMOTE -> RemoteScreen()
                    null -> {}
                }
            } else {
                when (selectedTab) {
                    AppTab.DASHBOARD -> DashboardScreen(
                        onNavigateToConsole = { selectedTab = AppTab.CONSOLE },
                        onNavigateToConfig = { selectedTab = AppTab.CONFIG },
                        onNavigateToFiles = { activeSubScreen = SubScreen.FILES },
                        onNavigateToRemote = { activeSubScreen = SubScreen.REMOTE }
                    )
                    AppTab.CONSOLE -> ConsoleScreen()
                    AppTab.CONFIG -> ServerConfigScreen(
                        onSaved = { selectedTab = AppTab.DASHBOARD }
                    )
                    AppTab.OPTIMIZATION -> OptimizationScreen()
                }
            }
        }
    }
}
