package com.sunnyweather.android.logic.functiongemma

import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import kotlinx.coroutines.NonCancellable.cancel
import kotlinx.coroutines.delay

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
    suspend fun initialize() = withContext(Dispatchers.Default) { // 改为 Default
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
                // 此步骤在 CPU 上可能需要 10-20 秒，请耐心等待
                engine?.initialize()

                // 修改 initialize 方法中的 config 部分
                val config = ConversationConfig(
                    tools = listOf(SystemStatusTool()),
                    systemMessage = Message.of(
                        "You are a helpful assistant with access to local system tools. " +
                                "Use the 'checkStatus' tool whenever the user asks about hardware, temperature, or system health."
                    )
                )

                conversation = engine?.createConversation(config)

            }
            Log.d("GemmaTest", "初始化完全成功")
        } catch (e: Exception) {
            Log.e("GemmaTest", "初始化失败: ${e.message}")
            throw e
        }
    }

    // ModelDeployment.kt

    suspend fun testChat(input: String, onResponse: (String) -> Unit) = withContext(Dispatchers.Default) {
        val currentConversation = conversation ?: return@withContext
        Log.d("GemmaTest", ">>> 消息已发出: $input")

        // 使用 coroutineScope 建立一个可以被局部取消的作用域
        kotlinx.coroutines.coroutineScope {
            try {
                currentConversation.sendMessageAsync(Message.of(input)).collect { response ->
                    val text = response.toString()
                    if (text.isNotEmpty()) {
                        onResponse(text)

                        // 物理打断：检测到函数调用结束标签
                        if (text.contains("<end_function_call>") || text.contains("</tool_call>")) {
                            Log.w("GemmaTest", "拦截到死循环，正在停止推理流...")

                            // 正确做法：抛出 CancellationException
                            // 这会安全地停止 collect，并被外层的 try-catch 捕获
                            throw kotlinx.coroutines.CancellationException("Stop loop safely")
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.d("GemmaTest", "死循环已通过抛出异常成功拦截。")
            }
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
        Log.e("GemmaTest", ">>> [HIT] KOTLIN CODE IS TRIGGERED! <<<")
        return mapOf(
            "temperature" to "35C",
            "status" to "Healthy",
            "details" to "All systems operational"
        )    }
}