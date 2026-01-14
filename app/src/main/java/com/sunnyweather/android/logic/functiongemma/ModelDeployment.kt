package com.sunnyweather.android.logic.functiongemma

import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log


class ModelDeployment(private val modelPath: String) {

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private val registeredTools = mutableListOf<Any>()

    fun setTools(tools: List<Any>) {
        registeredTools.clear()
        registeredTools.addAll(tools)
    }

    suspend fun rebuildConversation() = withContext(Dispatchers.Default) {
        Log.d("GemmaTest", ">>> 正在清理上下文并重建会话 <<<")
        conversation?.close() // 必须显式关闭以释放 Native 层的上下文引用

        val systemMsg = "You are a model that can do function calling with the following functions."
        val convConfig = ConversationConfig(
            tools = registeredTools,
            systemMessage = Message.of(systemMsg)
        )
        conversation = engine?.createConversation(convConfig)
    }

    suspend fun initialize() = withContext(Dispatchers.Default) {
        try {
                if (engine == null) {
                    val engineConfig = EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.CPU
                    )
                    engine = Engine(engineConfig).apply { initialize() }
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
            try {
                currentConversation.sendMessageAsync(Message.of(input.trim())).collect { response ->
                    val rawText = response.toString()
                    // 辩证性检查：如果拦截器配置正确，此处 rawText 不应包含控制标记
                        Log.d("GemmaTest", "流式输出 Token: $rawText")
                        onResponse(rawText)
                }
            } catch (e: Exception) {
                Log.e("GemmaTest", "推理流被异常中断: ${e.message}")
            } finally {
                Log.d("GemmaTest", "流式推理回答完毕")
            }
        }

     fun release()  {
        Log.d("GemmaTest", ">>> 真正的释放动作被执行 <<<")
        conversation?.close()
        engine?.close()
        conversation = null
        engine = null
    }

}

