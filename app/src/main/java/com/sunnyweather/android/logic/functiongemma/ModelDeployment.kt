package com.sunnyweather.android.logic.functiongemma

import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import kotlinx.coroutines.delay

class ModelDeployment(private val modelPath: String) {
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    suspend fun initialize() = withContext(Dispatchers.Default) { // 改为 Default
        Log.d("GemmaTest", "开始初始化，强制锁定 CPU 模式...")
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
                    // 关键：明确指出它“拥有”这些工具，并且必须使用。
                    systemMessage = Message.of(
                        // 使用官方推荐的触发句开头
                        "You are a model that can do function calling with the following functions: [checkStatus]. " +
                                "When asked about system metrics, YOU MUST call the checkStatus tool. " +
                                "Please use the checkStatus tool now."
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

    suspend fun testChat(input: String, onResponse: (String) -> Unit) {
        val currentConversation = conversation ?: run {
            Log.e("GemmaTest", "错误：对话对象尚未创建")
            return
        }
        val fullResponse = StringBuilder() // 用于拼接完整回答

        Log.d("GemmaTest", ">>> 消息已发出: $input")
        var hasReceivedAnything = false

        try {
            currentConversation.sendMessageAsync(Message.of(input)).collect { response ->
                hasReceivedAnything = true

                // 日志 A：监控原始包（非常重要，能看到模型是否在“思考”）
//                Log.v("GemmaTest", ">>> [数据流] 收到 Data 包")


                // 日志 C：监控文本输出
                if (response.toString().isNotEmpty()) {
//                    Log.i("GemmaTest", ">>> [文本流] 模型回复: ${response.toString()}")
                    onResponse(response.toString())
                }
            }

            if (!hasReceivedAnything) {
                Log.e("GemmaTest", ">>> [异常] 流结束了，但未收到任何数据包")
            }
        } catch (e: Exception) {
            Log.e("GemmaTest", ">>> [崩溃] 聊天请求异常", e)
        }
    }

    fun release() {
        try {
            conversation?.close()
            // 关键：只有当 engine 确实存在且没有被关闭时才调用 close
            engine?.let {
                // 注意：某些 alpha 版本不提供 isInitialized 属性，
                // 此时直接用 runCatching 包装即可
                runCatching { it.close() }.onFailure {
                    Log.w("GemmaTest", "释放引擎时跳过无效状态")
                }
            }
            engine = null
            conversation = null
        } catch (e: Exception) {
            Log.e("GemmaTest", "资源释放异常", e)
        }
    }
}

class SystemStatusTool {
    @Tool(description = "Fetches the current internal system health and hardware metrics.")    fun checkStatus(): String {
        Log.e("GemmaTest", ">>> [HIT] KOTLIN CODE IS TRIGGERED! <<<")
        // 返回一个格式极其简单的字符串
        return "System Status: Healthy, Temperature: 38C, Load: Low"
    }
}