package com.sunnyweather.android.logic.functiongemma

import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import kotlinx.coroutines.NonCancellable.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ModelDeployment(private val modelPath: String) {
    init {
        // 提前手动加载，消除 SDK 探测时的 nativeCheckLoaded 报错
        try {
            System.loadLibrary("litertlm_jni")
        } catch (e: Exception) {
            Log.w("GemmaTest", "Pre-loading jni library...")
        }
    }

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private val registeredTools = mutableListOf<Any>(SystemStatusTool())

    fun setTools(tools: List<Any>) {
        registeredTools.clear()
        registeredTools.add(SystemStatusTool()) // 保留原有的 SystemStatusTool
        registeredTools.addAll(tools)
    }

    suspend fun initialize() = withContext(Dispatchers.Default) {
        Log.d("GemmaTest", "开始初始化，强制锁定 CPU 模式")
        try {
            // 使用 NonCancellable 防止加载到一半时因为 Activity 抖动导致 Job Cancelled
            withContext(kotlinx.coroutines.NonCancellable) {
                if (engine == null) {
                    val engineConfig = EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.CPU // 必须锁定 CPU
                    )
                    engine = Engine(engineConfig)
                }
                delay(2000)
                engine?.initialize()
                rebuildConversation()
            }
            Log.d("GemmaTest", "初始化完全成功")
        } catch (e: Exception) {
            Log.e("GemmaTest", "初始化失败: ${e.message}")
            throw e
        }
    }

    suspend fun testChat(input: String, onResponse: (String) -> Unit) =
        withContext(Dispatchers.Default) {
            val currentConversation = conversation ?: return@withContext

            var isToolTriggered = false
            var shouldIgnoreRemaining = false // 增加标记位，平滑处理剩余 Token

            try {
                currentConversation.sendMessageAsync(Message.of(input.trim())).collect { response ->
                    if (shouldIgnoreRemaining) return@collect // 发现目标后，仅忽略，不抛异常

                    val rawText = response.toString()
                    if (rawText.isNotEmpty()) Log.d("GemmaTest", "Raw: $rawText")

                    if (rawText.contains("<start_function_call>")) {
                        isToolTriggered = true
                    }

                    // 命中结束标签，标记“处理完成”
                    if (rawText.contains("<end_function_call>") || rawText.contains("</tool_call>")) {
                        Log.w("GemmaTest", ">>> 目标已命中，准备停止流...")
                        shouldIgnoreRemaining = true
                    }
                }

                if (!isToolTriggered && !shouldIgnoreRemaining) {
                    onResponse("无相关函数适合调用")
                }

            } catch (e: Exception) {
                Log.e("GemmaTest", "推理流被异常中断: ${e.message}")
            } finally {
                // 【核心保护层】
                // 强制等待 500ms。这段时间是给 Native 层的 OnError 或 OnToken 回调跑完用的。
                // 只有 Native 停稳了，我们才能 close 它。
                delay(500)
                rebuildConversation()
            }
        }

    // 抽取重置逻辑，确保下个问题的正常返回
    private fun rebuildConversation() {
        runCatching {
            val oldConv = conversation
            conversation = null // 【关键】先置空，切断 Java 通道

            // 异步清理：不要在主流程里等 close 完成
            // 这样 testChat 就可以立刻结束返回，让用户发起下一次
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                delay(200) // 给 Native 层留出排空回调的缓冲
                oldConv?.close()
            }

            val config = ConversationConfig(
                tools = registeredTools,
                systemMessage = Message.of("You are a helpful assistant. You can use tools to check system status or search for city weather.")
            )
            conversation = engine?.createConversation(config)
            Log.d("GemmaTest", "会话已安全重置")
        }.onFailure {
            Log.e("GemmaTest", "重置过程中发生非致命异常", it)
        }
    }

    fun release() {
        Log.d("GemmaTest", ">>> 真正的释放动作被执行 <<<")
        conversation?.close()
        engine?.close()
        conversation = null
        engine = null
    }

}

class SystemStatusTool {
    @Tool(description = "Retrieve the current hardware status, including CPU temperature and system health.")
    fun check_status(): Map<String, String> {
        Log.e("GemmaTest", ">>> 成功执行<<check_status>> <<<")
        return mapOf(
            "temperature" to "35C",
            "status" to "Healthy",
            "details" to "All systems operational"
        )
    }
}
