package com.ai.assistance.operit.ui.floating.ui.fullscreen.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.floating.FloatContext
import com.ai.assistance.operit.ui.floating.FloatingMode
import com.ai.assistance.operit.ui.floating.ui.fullscreen.XmlTextProcessor
import com.ai.assistance.operit.ui.floating.ui.fullscreen.components.BottomControlBar
import com.ai.assistance.operit.ui.floating.ui.fullscreen.components.EditPanel
import com.ai.assistance.operit.ui.floating.ui.fullscreen.components.MessageDisplay
import com.ai.assistance.operit.ui.floating.ui.fullscreen.components.WaveVisualizerSection
import com.ai.assistance.operit.ui.floating.ui.fullscreen.viewmodel.rememberFloatingFullscreenModeViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 全屏模式主屏幕
 */
@Composable
fun FloatingFullscreenMode(floatContext: FloatContext) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val viewModel = rememberFloatingFullscreenModeViewModel(context, floatContext, coroutineScope)
    
    // 偏好设置
    val preferencesManager = UserPreferencesManager.getInstance(context)
    val aiAvatarUri by preferencesManager.customAiAvatarUri.collectAsState(initial = null)
    
    val speechServicesPrefs = SpeechServicesPreferences(context)
    val ttsCleanerRegexs by speechServicesPrefs.ttsCleanerRegexsFlow.collectAsState(initial = emptyList())
    
    val volumeLevel by viewModel.volumeLevelFlow.collectAsState()
    
    val speed = 1.2f
    
    // 监听语音识别结果
    LaunchedEffect(Unit) {
        viewModel.recognitionResultFlow.collectLatest { result ->
            viewModel.handleRecognitionResult(result.text, result.isFinal)
        }
    }
    
    // 初始化
    LaunchedEffect(Unit) {
        viewModel.initialize()
    }
    
    // 监听最新的AI消息
    LaunchedEffect(floatContext.messages.lastOrNull()?.timestamp) {
        viewModel.processAndSpeakAiMessage(
            floatContext.messages.lastOrNull(),
            ttsCleanerRegexs
        )
    }
    
    // 清理资源
    DisposableEffect(Unit) {
        onDispose {
            viewModel.cleanup()
        }
    }
    
    // UI 布局
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        // 上半部分完全透明
                        0.0f to Color.Transparent,
                        0.5f to Color.Transparent,
                        // 从屏幕中间往下开始出现更暗一些的蓝绿色渐变（降低明度，不是加厚遮罩）
                        0.75f to Color(0xFF42A5F5).copy(alpha = 0.45f),  // 深一点的蓝
                        0.9f  to Color(0xFF26C6DA).copy(alpha = 0.45f),  // 深一点的蓝绿
                        1.0f  to Color(0xFF66BB6A).copy(alpha = 0.45f)   // 深一点的绿色
                    )
                )
            )
    ) {
        // 顶部控制区域：返回窗口 / 语音模式 / 缩成语音球 / 关闭
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 返回窗口模式（或上一个模式）
            IconButton(onClick = {
                val targetMode =
                    if (floatContext.previousMode == FloatingMode.FULLSCREEN ||
                        floatContext.previousMode == FloatingMode.VOICE_BALL
                    ) {
                        FloatingMode.WINDOW
                    } else {
                        floatContext.previousMode
                    }
                floatContext.onModeChange(targetMode)
            }) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "返回窗口模式",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // 语音模式切换按钮：点击进入语音模式（中间显示头像和波浪），再次点击则退出
            IconButton(onClick = {
                if (viewModel.isWaveActive) {
                    viewModel.exitWaveMode()
                } else {
                    viewModel.enterWaveMode()
                }
            }) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = "语音模式",
                    tint = if (viewModel.isWaveActive) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            // 缩小成语音球
            IconButton(onClick = { floatContext.onModeChange(FloatingMode.VOICE_BALL) }) {
                Icon(
                    imageVector = Icons.Default.Chat,
                    contentDescription = "缩小成语音球",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            // 关闭悬浮窗
            IconButton(onClick = { floatContext.onClose() }) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭悬浮窗",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        
        // 主内容区域
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (viewModel.showBottomControls) 120.dp else 32.dp)
        ) {
            // 波浪可视化和头像：仅在语音模式下显示
            if (viewModel.isWaveActive) {
                WaveVisualizerSection(
                    isWaveActive = viewModel.isWaveActive,
                    isRecording = viewModel.isRecording,
                    volumeLevelFlow = if (viewModel.isWaveActive && viewModel.isRecording)
                        viewModel.volumeLevelFlow else null,
                    aiAvatarUri = aiAvatarUri,
                    avatarShape = CircleShape,
                    onToggleActive = {
                        if (viewModel.isWaveActive) {
                            viewModel.exitWaveMode()
                        } else {
                            viewModel.enterWaveMode()
                        }
                    },
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            
            // 消息显示区域 - 根据模式切换位置
            AnimatedContent(
                targetState = viewModel.isWaveActive,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300, 150)) togetherWith
                    fadeOut(animationSpec = tween(300))
                },
                label = "MessageTransition",
                modifier = Modifier.fillMaxSize()
            ) { targetIsWaveActive ->
                Box(modifier = Modifier.fillMaxSize()) {
                    val modifier = if (targetIsWaveActive) {
                        // 波浪模式：文本在底部
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                            .padding(bottom = 64.dp)
                    } else {
                        // 正常模式：文本在波浪下方
                        Modifier
                            .align(Alignment.Center)
                            .offset(y = 80.dp)
                            .fillMaxWidth()
                            .padding(top = 240.dp, bottom = 120.dp) // Timon: 依照顶部和底部组件距离估算
                            .padding(horizontal = 32.dp)
                    }

                    MessageDisplay(
                        userMessage = viewModel.userMessage,
                        aiMessage = viewModel.aiMessage,
                        modifier = modifier
                    )
                }
            }
        }
        
        // 编辑面板
        EditPanel(
            visible = viewModel.isEditMode,
            editableText = viewModel.editableText,
            onTextChange = { viewModel.editableText = it },
            onCancel = { viewModel.exitEditMode() },
            onSend = { viewModel.sendEditedMessage() },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        
        // 底部控制栏
        BottomControlBar(
            visible = viewModel.showBottomControls && !viewModel.isEditMode,
            isRecording = viewModel.isRecording,
            isProcessingSpeech = viewModel.isProcessingSpeech,
            showDragHints = viewModel.showDragHints,
            floatContext = floatContext,
            onStartVoiceCapture = { viewModel.startVoiceCapture() },
            onStopVoiceCapture = { isCancel -> viewModel.stopVoiceCapture(isCancel) },
            onEnterWaveMode = { 
                viewModel.isWaveActive = true
                viewModel.showBottomControls = false
            },
            onEnterEditMode = { text -> viewModel.enterEditMode(text) },
            onShowDragHintsChange = { viewModel.showDragHints = it },
            userMessage = viewModel.inputText,
            onUserMessageChange = { viewModel.inputText = it },
            onSendClick = { viewModel.sendInputMessage() },
            volumeLevel = volumeLevel,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
