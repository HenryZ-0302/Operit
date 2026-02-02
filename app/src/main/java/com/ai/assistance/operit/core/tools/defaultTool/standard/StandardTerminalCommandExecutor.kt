package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.*
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.core.tools.system.Terminal
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/** 终端命令执行工具 - 非流式输出版本 执行终端命令并一次性收集全部输出后返回 */
class StandardTerminalCommandExecutor(private val context: Context) {

    private val TAG = "TerminalCommandExecutor"

    companion object {
        // 用于将会话名称映射到会话ID
        private val sessionNameToIdMap = ConcurrentHashMap<String, String>()
    }


    /** 创建或获取一个终端会话 */
    fun createOrGetSession(tool: AITool): ToolResult {
        return runBlocking {
            try {
                val sessionName = tool.parameters.find { it.name == "session_name" }?.value
                if (sessionName.isNullOrBlank()) {
                    return@runBlocking ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = context.getString(R.string.terminal_error_missing_session_name)
                    )
                }

                val terminal = Terminal.getInstance(context)

                // 修正：直接检查 Terminal 单例中是否已存在同名会话，而不是依赖本地缓存
                val existingSession = terminal.terminalState.value.sessions.find { it.title == sessionName }
                if (existingSession != null) {
                    // 如果存在，更新本地缓存并返回该会话
                    sessionNameToIdMap[sessionName] = existingSession.id
                    return@runBlocking ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = TerminalSessionCreationResultData(
                            sessionId = existingSession.id,
                            sessionName = sessionName,
                            isNewSession = false
                        )
                    )
                }

                // 如果 Terminal 中不存在，则创建新会话
                val newSessionId = terminal.createSession(sessionName)
                sessionNameToIdMap[sessionName] = newSessionId

                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = TerminalSessionCreationResultData(
                        sessionId = newSessionId,
                        sessionName = sessionName,
                        isNewSession = true
                    )
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "创建或获取终端会话时出错", e)
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = context.getString(R.string.terminal_error_create_session, e.message ?: "")
                )
            }
        }
    }

    /** 在指定的终端会话中执行命令 */
    fun executeCommandInSession(tool: AITool): ToolResult {
        return runBlocking {
            try {
                val command = tool.parameters.find { param -> param.name == "command" }?.value ?: ""
                val sessionId = tool.parameters.find { param -> param.name == "session_id" }?.value

                if (sessionId.isNullOrBlank()) {
                    return@runBlocking ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = context.getString(R.string.terminal_error_missing_session_id)
                    )
                }

                val timeout =
                        tool.parameters
                                .find { param -> param.name == "timeout_ms" }
                                ?.value
                                ?.toLongOrNull()
                                ?: 1800000L // 30 分钟

                val terminal = Terminal.getInstance(context)

                // 检查会话是否存在
                if (terminal.terminalState.value.sessions.none { it.id == sessionId }) {
                    // 如果会话不存在，也从我们的映射中移除
                    sessionNameToIdMap.entries.removeIf { it.value == sessionId }
                    return@runBlocking ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = context.getString(R.string.terminal_error_session_not_exist, sessionId)
                    )
                }

                val outputFlow = terminal.executeCommandFlow(sessionId, command)

                if (outputFlow != null) {
                    val events = mutableListOf<String>()
                    var exitCode = 0
                    var hasCompleted = false

                    try {
                        withTimeout(timeout) {
                            outputFlow.collect { event ->
                                if (event.outputChunk.isNotEmpty()) {
                                    events.add(event.outputChunk)
                                }
                                if (event.isCompleted) {
                                    exitCode = 0
                                    hasCompleted = true
                                }
                            }
                        }
                    } catch (e: TimeoutCancellationException) {
                        AppLogger.w(TAG, "Command execution timed out after ${timeout}ms")
                        hasCompleted = true
                        exitCode = -1
                    }

                    val fullOutput = events.joinToString("")
                    AppLogger.d(TAG, "Command output collected: '$fullOutput', exitCode: $exitCode")

                    ToolResult(
                            toolName = tool.name,
                            success = hasCompleted && exitCode == 0,
                            result = TerminalCommandResultData(
                                    command = command,
                                    output = fullOutput,
                                    exitCode = exitCode,
                                    sessionId = sessionId
                            )
                    )
                } else {
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = context.getString(R.string.terminal_error_command_failed)
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "执行终端命令时出错", e)
                ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = context.getString(R.string.terminal_error_execute_command, e.message ?: "")
                )
            }
        }
    }

    /** 关闭一个终端会话 */
    fun closeSession(tool: AITool): ToolResult {
        return runBlocking {
            val sessionId = tool.parameters.find { it.name == "session_id" }?.value
            try {
                if (sessionId.isNullOrBlank()) {
                    return@runBlocking ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = context.getString(R.string.terminal_error_missing_session_id)
                    )
                }

                val terminal = Terminal.getInstance(context)
                terminal.closeSession(sessionId)

                // 从名称映射中移除
                sessionNameToIdMap.entries.removeIf { it.value == sessionId }

                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = TerminalSessionCloseResultData(
                        sessionId = sessionId,
                        success = true,
                        message = context.getString(R.string.terminal_session_closed, sessionId)
                    )
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "关闭终端会话时出错", e)
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = context.getString(R.string.terminal_error_close_session, sessionId, e.message ?: "")
                )
            }
        }
    }
}
