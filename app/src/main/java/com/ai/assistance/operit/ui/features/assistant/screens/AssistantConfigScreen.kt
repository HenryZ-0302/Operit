package com.ai.assistance.operit.ui.features.assistant.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.WakeWordPreferences
import com.ai.assistance.operit.ui.features.assistant.components.AvatarConfigSection
import com.ai.assistance.operit.ui.features.assistant.components.AvatarPreviewSection
import com.ai.assistance.operit.ui.features.assistant.components.HowToImportSection
import com.ai.assistance.operit.ui.features.assistant.viewmodel.AssistantConfigViewModel
import kotlinx.coroutines.launch

/** 助手配置屏幕 提供DragonBones模型预览和相关配置 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantConfigScreen() {
        val context = LocalContext.current
        val viewModel: AssistantConfigViewModel =
                viewModel(factory = AssistantConfigViewModel.Factory(context))
        val uiState by viewModel.uiState.collectAsState()

        val wakePrefs = remember { WakeWordPreferences(context.applicationContext) }
        val wakeListeningEnabled by wakePrefs.alwaysListeningEnabledFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_ALWAYS_LISTENING_ENABLED)
        val wakePhrase by wakePrefs.wakePhraseFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_WAKE_PHRASE)
        val inactivityTimeoutSeconds by wakePrefs.voiceCallInactivityTimeoutSecondsFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_VOICE_CALL_INACTIVITY_TIMEOUT_SECONDS)
        val wakeGreetingEnabled by wakePrefs.wakeGreetingEnabledFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_WAKE_GREETING_ENABLED)
        val wakeGreetingText by wakePrefs.wakeGreetingTextFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_WAKE_GREETING_TEXT)
        val coroutineScope = rememberCoroutineScope()

        val requestMicPermissionLauncher =
                rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                        if (isGranted) {
                                coroutineScope.launch {
                                        wakePrefs.saveAlwaysListeningEnabled(true)
                                }
                        } else {
                                android.widget.Toast.makeText(
                                                context,
                                                context.getString(R.string.microphone_permission_denied_toast),
                                                android.widget.Toast.LENGTH_SHORT
                                        )
                                        .show()
                        }
                }

        var wakePhraseInput by remember { mutableStateOf("") }
        var inactivityTimeoutInput by remember { mutableStateOf("") }
        var wakeGreetingTextInput by remember { mutableStateOf("") }

        LaunchedEffect(wakePhrase) {
                if (wakePhraseInput.isBlank()) {
                        wakePhraseInput = wakePhrase
                }
        }

        LaunchedEffect(inactivityTimeoutSeconds) {
                if (inactivityTimeoutInput.isBlank()) {
                        inactivityTimeoutInput = inactivityTimeoutSeconds.toString()
                }
        }

        LaunchedEffect(wakeGreetingText) {
                if (wakeGreetingTextInput.isBlank()) {
                        wakeGreetingTextInput = wakeGreetingText
                }
        }

        // 启动文件选择器
        val zipFileLauncher =
                rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                        if (result.resultCode == Activity.RESULT_OK) {
                                result.data?.data?.let { uri ->
                                        // 导入选择的zip文件
                                        viewModel.importAvatarFromZip(uri)
                                }
                        }
                }

        // 打开文件选择器的函数
        val openZipFilePicker = {
                val intent =
                        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "application/zip"
                                putExtra(
                                        Intent.EXTRA_MIME_TYPES,
                                        arrayOf("application/zip", "application/x-zip-compressed")
                                )
                        }
                zipFileLauncher.launch(intent)
        }

        val snackbarHostState = remember { SnackbarHostState() }
        val scrollState = rememberScrollState(initial = uiState.scrollPosition)

        // 在 Composable 函数中获取字符串资源，以便在 LaunchedEffect 中使用
        val operationSuccessString = context.getString(R.string.operation_success)
        val errorOccurredString = context.getString(R.string.error_occurred_simple)

        LaunchedEffect(scrollState) {
                snapshotFlow { scrollState.value }.collect { position ->
                        viewModel.updateScrollPosition(position)
                }
        }

        // 显示操作结果的 SnackBar
        LaunchedEffect(uiState.operationSuccess, uiState.errorMessage) {
                if (uiState.operationSuccess) {
                        snackbarHostState.showSnackbar(operationSuccessString)
                        viewModel.clearOperationSuccess()
                } else if (uiState.errorMessage != null) {
                        snackbarHostState.showSnackbar(uiState.errorMessage ?: errorOccurredString)
                        viewModel.clearErrorMessage()
                }
        }

        CustomScaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
                Box(modifier = Modifier.fillMaxSize()) {
                        // 主要内容
                        Column(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .padding(paddingValues)
                                                .padding(horizontal = 12.dp)
                                                .verticalScroll(scrollState)
                        ) {
                                // Avatar预览区域
                                AvatarPreviewSection(
                                        modifier = Modifier.fillMaxWidth().height(300.dp),
                                        uiState = uiState,
                                        onDeleteCurrentModel =
                                                uiState.currentAvatarConfig?.let { model ->
                                                        { viewModel.deleteAvatar(model.id) }
                                                }
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                AvatarConfigSection(
                                        viewModel = viewModel,
                                        uiState = uiState,
                                        onImportClick = { openZipFilePicker() }
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors =
                                                CardDefaults.cardColors(
                                                        containerColor =
                                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                                )
                                ) {
                                        Column(
                                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                                verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                                Text(
                                                        text = "语音唤醒",
                                                        style = MaterialTheme.typography.titleMedium
                                                )

                                                ListItem(
                                                        headlineContent = { Text(text = "始终监听") },
                                                        supportingContent = {
                                                                Text(text = "在后台持续监听唤醒词，需要麦克风权限")
                                                        },
                                                        trailingContent = {
                                                                Switch(
                                                                        checked = wakeListeningEnabled,
                                                                        onCheckedChange = { enabled ->
                                                                                if (enabled) {
                                                                                        val granted =
                                                                                                ContextCompat.checkSelfPermission(
                                                                                                                context,
                                                                                                                Manifest.permission.RECORD_AUDIO
                                                                                                        ) == PackageManager.PERMISSION_GRANTED
                                                                                        if (granted) {
                                                                                                coroutineScope.launch {
                                                                                                        wakePrefs.saveAlwaysListeningEnabled(true)
                                                                                                }
                                                                                        } else {
                                                                                                requestMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                                                        }
                                                                                } else {
                                                                                        coroutineScope.launch {
                                                                                                wakePrefs.saveAlwaysListeningEnabled(false)
                                                                                        }
                                                                                }
                                                                        }
                                                                )
                                                        },
                                                        colors =
                                                                ListItemDefaults.colors(
                                                                        containerColor = Color.Transparent
                                                                )
                                                )

                                                OutlinedTextField(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        value = wakePhraseInput,
                                                        onValueChange = { newValue ->
                                                                wakePhraseInput = newValue
                                                                coroutineScope.launch {
                                                                        wakePrefs.saveWakePhrase(newValue.ifBlank { WakeWordPreferences.DEFAULT_WAKE_PHRASE })
                                                                }
                                                        },
                                                        singleLine = true,
                                                        label = { Text("唤醒词") },
                                                        supportingText = { Text("例如：小O") }
                                                )

                                                OutlinedTextField(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        value = inactivityTimeoutInput,
                                                        onValueChange = { newValue ->
                                                                val filtered = newValue.filter { it.isDigit() }
                                                                inactivityTimeoutInput = filtered
                                                                val parsed = filtered.toIntOrNull()
                                                                if (parsed != null) {
                                                                        val clamped = parsed.coerceIn(1, 600)
                                                                        coroutineScope.launch {
                                                                                wakePrefs.saveVoiceCallInactivityTimeoutSeconds(clamped)
                                                                        }
                                                                }
                                                        },
                                                        singleLine = true,
                                                        label = { Text("语音无响应超时（秒）") },
                                                        supportingText = { Text("范围：1-600") },
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                                )

                                                ListItem(
                                                        headlineContent = { Text(text = "唤醒后说一句") },
                                                        supportingContent = { Text(text = "通过唤醒词进入语音模式时，先朗读一句再开始聆听") },
                                                        trailingContent = {
                                                                Switch(
                                                                        checked = wakeGreetingEnabled,
                                                                        onCheckedChange = { enabled ->
                                                                                coroutineScope.launch {
                                                                                        wakePrefs.saveWakeGreetingEnabled(enabled)
                                                                                }
                                                                        }
                                                                )
                                                        },
                                                        colors =
                                                                ListItemDefaults.colors(
                                                                        containerColor = Color.Transparent
                                                                )
                                                )

                                                OutlinedTextField(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        value = wakeGreetingTextInput,
                                                        onValueChange = { newValue ->
                                                                wakeGreetingTextInput = newValue
                                                                coroutineScope.launch {
                                                                        wakePrefs.saveWakeGreetingText(newValue.ifBlank { WakeWordPreferences.DEFAULT_WAKE_GREETING_TEXT })
                                                                }
                                                        },
                                                        singleLine = true,
                                                        enabled = wakeGreetingEnabled,
                                                        label = { Text("朗读内容") },
                                                        supportingText = { Text("例如：我在") }
                                                )
                                        }
                                }

                                HowToImportSection()

                                // 底部空间
                                Spacer(modifier = Modifier.height(16.dp))
                        }

                        // 加载指示器覆盖层
                        if (uiState.isLoading || uiState.isImporting) {
                                Box(
                                        modifier =
                                                Modifier.fillMaxSize()
                                                        .background(
                                                                MaterialTheme.colorScheme.surface
                                                                        .copy(alpha = 0.7f)
                                                        ),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                CircularProgressIndicator()
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Text(
                                                        text =
                                                                if (uiState.isImporting) stringResource(R.string.importing_model)
                                                                else stringResource(R.string.processing),
                                                        style = MaterialTheme.typography.bodyMedium
                                                )
                                        }
                                }
                        }
                }
        }
}
