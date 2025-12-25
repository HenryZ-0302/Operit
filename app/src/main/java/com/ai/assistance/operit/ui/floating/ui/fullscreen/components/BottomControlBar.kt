package com.ai.assistance.operit.ui.floating.ui.fullscreen.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.floating.FloatContext
import com.ai.assistance.operit.ui.floating.FloatingMode
import kotlin.math.abs
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 底部控制栏组件
 * 包含返回按钮、麦克风按钮和缩小按钮
 */
@Composable
fun BottomControlBar(
    visible: Boolean,
    isRecording: Boolean,
    isProcessingSpeech: Boolean,
    showDragHints: Boolean,
    floatContext: FloatContext,
    onStartVoiceCapture: () -> Unit,
    onStopVoiceCapture: (Boolean) -> Unit,
    onEnterWaveMode: () -> Unit,
    onEnterEditMode: (String) -> Unit,
    onShowDragHintsChange: (Boolean) -> Unit,
    userMessage: String,
    onUserMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    volumeLevel: Float,
    modifier: Modifier = Modifier
) {
    // 底部输入模式：false = 文本输入框；true = 整条变成“按住说话”按钮
    var isHoldToSpeakMode by remember { mutableStateOf(false) }
    var isCancelRegion by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    // 简单的音量历史，用于在长按时绘制一个从右往左移动的波形条
    val volumeHistory = remember {
        mutableStateListOf<Float>().apply {
            repeat(24) { add(0f) }
        }
    }
    val density = LocalDensity.current
    // 取消区域：大致拖出胶囊高度（56dp）之外才算取消
    val cancelThresholdPx = with(density) { 56.dp.toPx() }

    // 在长按语音时，根据当前音量持续更新历史，用于绘制音量波形
    LaunchedEffect(volumeLevel, isPressed, isRecording) {
        if (isPressed && isRecording && volumeHistory.isNotEmpty()) {
            volumeHistory.removeAt(0)
            volumeHistory.add(volumeLevel.coerceIn(0f, 1f))
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 64.dp, start = 32.dp, end = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            val pillColor = when {
                isCancelRegion && isHoldToSpeakMode -> MaterialTheme.colorScheme.error
                isHoldToSpeakMode && isPressed -> Color(0xFFE0E0E0) // 按下时使用实心浅灰色
                else -> Color.White
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 12.dp,
                        shape = CircleShape,
                        clip = false
                    )
                    .clip(CircleShape)
                    .background(pillColor)
            ) {
                // 文本输入模式与按住说话模式共用的基础布局
                OutlinedTextField(
                    value = userMessage,
                    onValueChange = onUserMessageChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    singleLine = true,
                    placeholder = {
                        if (!isHoldToSpeakMode) {
                            Text(
                                text = "输入文字，或用语音",
                                color = Color.Gray
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    leadingIcon = {
                        VoiceLeadingIcon(
                            isRecording = isRecording,
                            isProcessingSpeech = isProcessingSpeech,
                            isHoldToSpeakMode = isHoldToSpeakMode,
                            isPressed = isPressed,
                            onToggleHoldToSpeakMode = {
                                // 从按住说话模式切回时，如在录音则取消
                                if (isHoldToSpeakMode && isRecording) {
                                    onStopVoiceCapture(true)
                                }
                                isHoldToSpeakMode = !isHoldToSpeakMode
                                isCancelRegion = false
                                isPressed = false
                            }
                        )
                    },
                    trailingIcon = {
                        if (!isHoldToSpeakMode) {
                            IconButton(
                                onClick = onSendClick,
                                enabled = userMessage.isNotBlank()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "发送",
                                    tint = if (userMessage.isNotBlank()) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                    },
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                )

                if (isHoldToSpeakMode) {
                    // 按住说话模式：在文本框之上叠加手势区域和提示文案
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        // 手势区域：尽量避开左右图标，只覆盖中间区域
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(start = 56.dp, end = 56.dp)
                                .pointerInput(cancelThresholdPx) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            // 等待按下
                                            val downEvent = awaitPointerEvent(PointerEventPass.Main)
                                            val downChange = downEvent.changes.firstOrNull()
                                            if (downChange == null || !downChange.pressed) continue

                                            val startPosition = downChange.position
                                            var totalDragY = 0f
                                            isPressed = true
                                            isCancelRegion = false
                                            onStartVoiceCapture()

                                            // 跟踪拖动和抬起
                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Main)
                                                val change = event.changes.firstOrNull() ?: break

                                                if (!change.pressed) {
                                                    // 手指抬起
                                                    onStopVoiceCapture(isCancelRegion)
                                                    isCancelRegion = false
                                                    isPressed = false
                                                    totalDragY = 0f
                                                    break
                                                }

                                                val position = change.position
                                                val dy = position.y - startPosition.y
                                                totalDragY = dy
                                                // 拖出胶囊区域（向上或向下大幅移动）即进入取消状态
                                                isCancelRegion = abs(totalDragY) > cancelThresholdPx
                                            }
                                        }
                                    }
                                }
                        )

                        if (isPressed && !isCancelRegion) {
                            // 在普通长按状态下绘制居中、较小的黑色音量波形，右侧为最新音量
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                            ) {
                                val barCount = volumeHistory.size
                                if (barCount > 0) {
                                    // 保留更大的左右空白，让波形更窄、更居中
                                    val horizontalMargin = size.width * 0.28f
                                    val availableWidth = size.width - horizontalMargin * 2f
                                    val barWidth = availableWidth / (barCount * 1.4f)
                                    val gap = barWidth * 0.4f
                                    // 波形高度占胶囊高度的 30%，围绕垂直中心对称
                                    val maxHeight = size.height * 0.3f
                                    val centerY = size.height / 2f

                                    volumeHistory.forEachIndexed { index: Int, value: Float ->
                                        // index 越大，位置越靠右（最新的在最右侧）
                                        val xRight = size.width - horizontalMargin - (barWidth + gap) * (barCount - 1 - index).toFloat()
                                        val barHeight = (value.coerceIn(0f, 1f)) * maxHeight
                                        val top = centerY - barHeight / 2f
                                        drawRect(
                                            color = Color.Black,
                                            topLeft = Offset(xRight - barWidth, top),
                                            size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                                        )
                                    }
                                }
                            }
                        } else {
                            // 未按下或处于取消区域时，显示提示文案
                            Text(
                                text = if (isCancelRegion) "松手取消" else "按住说话",
                                color = if (isCancelRegion) Color.White else Color.Black,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceLeadingIcon(
    isRecording: Boolean,
    isProcessingSpeech: Boolean,
    isHoldToSpeakMode: Boolean,
    isPressed: Boolean,
    onToggleHoldToSpeakMode: () -> Unit
) {
    val iconColor =
        if (isRecording || isProcessingSpeech || isHoldToSpeakMode) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.primary
        }

    // 按住说话时隐藏图标并禁用点击，但保留占位，保证布局稳定
    val boxModifierBase = Modifier
        .size(32.dp)
        .clip(CircleShape)
        .background(Color.Transparent)

    val boxModifier = if (isHoldToSpeakMode && isPressed) {
        boxModifierBase
    } else {
        boxModifierBase.clickable { onToggleHoldToSpeakMode() }
    }

    Box(
        modifier = boxModifier,
        contentAlignment = Alignment.Center
    ) {
        if (!(isHoldToSpeakMode && isPressed)) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "语音输入",
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 返回按钮
 */
@Composable
private fun BackButton(
    floatContext: FloatContext,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = {
            val targetMode = if (floatContext.previousMode == FloatingMode.FULLSCREEN || 
                                 floatContext.previousMode == FloatingMode.VOICE_BALL) {
                FloatingMode.WINDOW
            } else {
                floatContext.previousMode
            }
            floatContext.onModeChange(targetMode)
        },
        modifier = modifier.size(42.dp)
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = "返回窗口模式",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

/**
 * 缩小成语音球按钮
 */
@Composable
private fun MinimizeToVoiceBallButton(
    floatContext: FloatContext,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = { floatContext.onModeChange(FloatingMode.VOICE_BALL) },
        modifier = modifier.size(42.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Chat,
            contentDescription = "缩小成语音球",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * 麦克风按钮和拖动提示
 */
@Composable
private fun MicrophoneButtonWithHints(
    isRecording: Boolean,
    isProcessingSpeech: Boolean,
    showDragHints: Boolean,
    onStartVoiceCapture: () -> Unit,
    onStopVoiceCapture: (Boolean) -> Unit,
    onEnterWaveMode: () -> Unit,
    onEnterEditMode: (String) -> Unit,
    onShowDragHintsChange: (Boolean) -> Unit,
    userMessage: String,
    modifier: Modifier = Modifier
) {
    var dragOffset by remember { mutableStateOf(0f) }
    val isDraggingToCancel = remember { mutableStateOf(false) }
    val isDraggingToEdit = remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // 左侧编辑提示
        DragHint(
            visible = showDragHints,
            icon = Icons.Default.Edit,
            iconColor = MaterialTheme.colorScheme.primary,
            description = "编辑",
            isLeft = true,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-80).dp)
        )

        // 右侧取消提示
        DragHint(
            visible = showDragHints,
            icon = Icons.Default.Delete,
            iconColor = MaterialTheme.colorScheme.error,
            description = "取消",
            isLeft = false,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 80.dp)
        )

        // 麦克风按钮
        MicrophoneButton(
            isRecording = isRecording,
            isProcessingSpeech = isProcessingSpeech,
            isDraggingToCancel = isDraggingToCancel,
            isDraggingToEdit = isDraggingToEdit,
            onStartVoiceCapture = onStartVoiceCapture,
            onStopVoiceCapture = onStopVoiceCapture,
            onEnterWaveMode = onEnterWaveMode,
            onEnterEditMode = onEnterEditMode,
            onShowDragHintsChange = onShowDragHintsChange,
            onDragOffsetChange = { dragOffset = it },
            onDraggingToCancelChange = { isDraggingToCancel.value = it },
            onDraggingToEditChange = { isDraggingToEdit.value = it },
            userMessage = userMessage,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

/**
 * 拖动提示组件
 */
@Composable
private fun DragHint(
    visible: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    description: String,
    isLeft: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInHorizontally(initialOffsetX = { if (isLeft) -it else it }),
        exit = fadeOut() + slideOutHorizontally(targetOffsetX = { if (isLeft) -it else it }),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLeft) {
                // 编辑图标在左
                Icon(
                    imageVector = icon,
                    contentDescription = description,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
                DashedLine()
            } else {
                // 取消图标在右
                DashedLine()
                Icon(
                    imageVector = icon,
                    contentDescription = description,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * 虚线组件
 */
@Composable
private fun DashedLine() {
    Canvas(
        modifier = Modifier
            .width(40.dp)
            .height(2.dp)
    ) {
        val pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)
        drawLine(
            color = Color.White.copy(alpha = 0.7f),
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 2.dp.toPx(),
            pathEffect = pathEffect
        )
    }
}

/**
 * 麦克风按钮
 */
@Composable
private fun MicrophoneButton(
    isRecording: Boolean,
    isProcessingSpeech: Boolean,
    isDraggingToCancel: MutableState<Boolean>,
    isDraggingToEdit: MutableState<Boolean>,
    onStartVoiceCapture: () -> Unit,
    onStopVoiceCapture: (Boolean) -> Unit,
    onEnterWaveMode: () -> Unit,
    onEnterEditMode: (String) -> Unit,
    onShowDragHintsChange: (Boolean) -> Unit,
    onDragOffsetChange: (Float) -> Unit,
    onDraggingToCancelChange: (Boolean) -> Unit,
    onDraggingToEditChange: (Boolean) -> Unit,
    userMessage: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(80.dp)
            .shadow(elevation = 8.dp, shape = CircleShape)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = if (isRecording || isProcessingSpeech) {
                        listOf(
                            MaterialTheme.colorScheme.secondary,
                            MaterialTheme.colorScheme.secondaryContainer
                        )
                    } else {
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            MaterialTheme.colorScheme.primary
                        )
                    }
                )
            )
            .clickable(enabled = false, onClick = {})
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        onEnterWaveMode()
                    },
                    onLongPress = {
                        onDragOffsetChange(0f)
                        onDraggingToCancelChange(false)
                        onDraggingToEditChange(false)
                        onShowDragHintsChange(true)
                        onStartVoiceCapture()
                    }
                )
            }
            .pointerInput(isRecording) {
                // 仅在录音时追踪拖动和释放
                if (!isRecording) return@pointerInput
                
                awaitPointerEventScope {
                    var previousPosition: Offset? = null
                    var currentOffset = 0f
                    
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull()
                        
                        if (change == null) break
                        
                        // 检查是否手指抬起
                        if (!change.pressed) {
                            // 释放时的 处理
                            onShowDragHintsChange(false)
                            when {
                                isDraggingToCancel.value -> {
                                    onStopVoiceCapture(true)
                                }
                                isDraggingToEdit.value -> {
                                    onEnterEditMode(userMessage)
                                }
                                else -> {
                                    onStopVoiceCapture(false)
                                }
                            }
                            break
                        }
                        
                        val position = change.position
                        
                        if (previousPosition == null) {
                            previousPosition = position
                        } else {
                            // 计算拖动偏移
                            val horizontalDrag = position.x - previousPosition.x
                            currentOffset += horizontalDrag
                            onDragOffsetChange(currentOffset)

                            val dragThreshold = 60f
                            when {
                                currentOffset > dragThreshold -> {
                                    onDraggingToCancelChange(true)
                                    onDraggingToEditChange(false)
                                }
                                currentOffset < -dragThreshold -> {
                                    onDraggingToEditChange(true)
                                    onDraggingToCancelChange(false)
                                }
                                else -> {
                                    onDraggingToCancelChange(false)
                                    onDraggingToEditChange(false)
                                }
                            }
                            
                            previousPosition = position
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // 图标显示
        when {
            isRecording && isDraggingToCancel.value -> {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "取消录音",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
            isRecording && isDraggingToEdit.value -> {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "编辑录音",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "按住说话",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

