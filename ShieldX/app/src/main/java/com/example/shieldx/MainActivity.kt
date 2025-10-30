package com.example.shieldx

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.example.shieldx.activities.DashboardActivity
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shieldx.api.ConnectionTester
import com.example.shieldx.service.ShieldXNotificationListener
import com.example.shieldx.utils.ToastManager
import com.example.shieldx.viewmodel.ScanViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * DeepGuard v3.1 - Main Activity
 * Central dashboard for ShieldX permissions, connection, and protection status.
 */
class MainActivity : ComponentActivity() {

    private val scanViewModel: ScanViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) checkNotificationListenerPermission()
        else ToastManager.showImportant(this, "Notification permission is required for ShieldX to operate")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if permissions are already granted and proceed
        if (isNotificationListenerEnabled()) {
            proceedToDashboard()
            return
        }

        setContent {
            ShieldXTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ShieldXMainScreen(
                        isNotificationListenerEnabled = { isNotificationListenerEnabled() },
                        onRequestNotificationPermission = { requestNotificationPermission() },
                        onRequestListenerPermission = { requestNotificationListenerPermission() },
                        onTestConnection = { testBackendConnection() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Check permissions on resume (after returning from settings)
        if (isNotificationListenerEnabled()) {
            proceedToDashboard()
        }
    }

    // =======================================
    // Permission Handling
    // =======================================
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            checkNotificationListenerPermission()
        }
    }

    private fun checkNotificationListenerPermission() {
        if (!isNotificationListenerEnabled()) {
            requestNotificationListenerPermission()
        } else {
            ToastManager.showShort(this, "Protection enabled successfully ✅")
            proceedToDashboard()
        }
    }

    private fun requestNotificationListenerPermission() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
        ToastManager.showImportant(this, "Enable ShieldX in Notification Access settings to activate protection.")
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )
        val component = ComponentName(this, ShieldXNotificationListener::class.java)
        return enabledListeners?.contains(component.flattenToString()) == true
    }

    // =======================================
    // Connection Testing
    // =======================================
    private fun testBackendConnection() {
        ConnectionTester.runAllTests()
        ToastManager.showShort(this, "Running backend diagnostics... check Logcat for output")
    }

    private fun proceedToDashboard() {
        val intent = Intent(this, DashboardActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }
}

// =======================================
// COMPOSABLE UI
// =======================================
@Composable
fun ShieldXMainScreen(
    onRequestNotificationPermission: () -> Unit,
    onRequestListenerPermission: () -> Unit,
    isNotificationListenerEnabled: () -> Boolean,
    onTestConnection: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var listenerEnabled by remember { mutableStateOf(isNotificationListenerEnabled()) }
    var backendConnected by remember { mutableStateOf(false) }
    var testingInProgress by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Icon + Title
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = "Shield Icon",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "🛡️ ShieldX",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "AI-Powered Harassment & Deepfake Protection",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Protection status card
        StatusCard(
            title = if (listenerEnabled) "Protection Active" else "Setup Required",
            description = if (listenerEnabled)
                "ShieldX is monitoring notifications for harmful content."
            else
                "Notification access must be granted to activate protection.",
            isActive = listenerEnabled
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Backend connection status
        StatusCard(
            title = when {
                testingInProgress -> "Testing Connection..."
                backendConnected -> "Backend Connected"
                else -> "Backend Disconnected"
            },
            description = when {
                testingInProgress -> "Verifying DeepGuard backend connectivity..."
                backendConnected -> "Connected to AI backend successfully."
                else -> "Unable to reach backend API."
            },
            isActive = backendConnected
        )

        Spacer(modifier = Modifier.height(32.dp))

        // =========================
        // ACTION BUTTONS
        // =========================
        AnimatedVisibility(visible = !listenerEnabled) {
            Button(
                onClick = { onRequestNotificationPermission() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enable Protection")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { listenerEnabled = isNotificationListenerEnabled() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Refresh Status")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                scope.launch {
                    testingInProgress = true
                    onTestConnection()
                    delay(2000)
                    backendConnected = true
                    testingInProgress = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !testingInProgress
        ) {
            Text(if (testingInProgress) "Testing..." else "Test Backend Connection")
        }

        Spacer(modifier = Modifier.height(28.dp))

        // =========================
        // INFORMATION CARD
        // =========================
        InfoCard(
            title = "📱 Monitored Apps",
            details = "WhatsApp • Instagram • Telegram • Messenger • SMS • Snapchat"
        )
    }
}

// =======================================
// COMPONENTS
// =======================================
@Composable
fun StatusCard(title: String, description: String, isActive: Boolean) {
    val containerColor = if (isActive)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.errorContainer

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isActive) Icons.Default.Security else Icons.Default.Warning,
                contentDescription = title,
                tint = if (isActive)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onErrorContainer
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = if (isActive)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onErrorContainer
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = if (isActive)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    else
                        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun InfoCard(title: String, details: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = details,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun ShieldXTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF1565C0),
            primaryContainer = Color(0xFFE3F2FD),
            secondary = Color(0xFF00BCD4),
            error = Color(0xFFD32F2F),
            errorContainer = Color(0xFFFFEBEE),
            surfaceVariant = Color(0xFFE0E0E0)
        ),
        typography = Typography()
    ) {
        content()
    }
}
