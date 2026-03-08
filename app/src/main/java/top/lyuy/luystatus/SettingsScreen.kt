package top.lyuy.luystatus

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import androidx.core.content.edit
import top.lyuy.luystatus.queue.QueueWorker


private fun checkNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}
private const val PREF_IMMEDIATE_LOCK = "queue_immediate_lock"
private const val KEY_LAST_IMMEDIATE_TIME = "last_immediate_time"
private const val IMMEDIATE_INTERVAL_MS = 2_500L

private fun tryEnqueueImmediate(
    context: Context,
    onRejected: () -> Unit = {},
    onAccepted: () -> Unit = {}
) {
    val prefs = context.getSharedPreferences(
        PREF_IMMEDIATE_LOCK,
        Context.MODE_PRIVATE
    )

    val now = System.currentTimeMillis()
    val last = prefs.getLong(KEY_LAST_IMMEDIATE_TIME, 0L)

    if (now - last < IMMEDIATE_INTERVAL_MS) {
        onRejected()
        return
    }

    //  先写锁，防止并发点击
    prefs.edit {
        putLong(KEY_LAST_IMMEDIATE_TIME, now)
    }

    QueueWorker.enqueueImmediate(context)
    onAccepted()
}

@Preview
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val prefs = remember {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    var apiKey by remember {
        mutableStateOf(
            prefs.getString("api_key", "") ?: ""
        )
    }

    var apiKeyVisible by remember { mutableStateOf(false) }

    //  新增：通知权限状态
    var notificationPermissionGranted by remember {
        mutableStateOf(checkNotificationPermission(context))
    }

    LaunchedEffect(Unit) {
        notificationPermissionGranted =
            checkNotificationPermission(context)
    }


    // Snackbar
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // 新增：权限提示
            if (!notificationPermissionGranted) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor =
                            MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "未授予通知权限",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Text(
                text = "API Key 设置",
                style = MaterialTheme.typography.titleLarge
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation =
                    if (apiKeyVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(
                        onClick = { apiKeyVisible = !apiKeyVisible }
                    ) {
                        Icon(
                            imageVector =
                                if (apiKeyVisible)
                                    Icons.Filled.VisibilityOff
                                else
                                    Icons.Filled.Visibility,
                            contentDescription =
                                if (apiKeyVisible) "隐藏"
                                else "显示"
                        )
                    }
                }
            )

            Button(
                onClick = {
                    prefs.edit {
                        putString("api_key", apiKey.trim())
                    }

                    scope.launch {
                        snackbarHostState.showSnackbar("保存成功")
                        Log.d("SettingsScreen","apikey保存成功")
                    }
                },
                enabled = apiKey.isNotBlank()
            ) {
                Text("保存")
            }
            Button(
                onClick = {
                    tryEnqueueImmediate(
                        context = context,
                        onRejected = {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    "操作过于频繁，请稍后再试"
                                )
                            }
                        },
                        onAccepted = {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    "已立即查询一次"
                                )
                            }
                        }
                    )
                },
                enabled = apiKey.isNotBlank()
            )
            {
                Text("立即查询")
            }

        }
    }
}
