package com.sunnyweather.android.logic.functiongemma

import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import kotlinx.coroutines.delay


class ModelDeployment(private val modelPath: String) {

    private var engine: Engine? = null
    private var conversationPool: ConversationPool? = null
    private val registeredTools = mutableListOf<Any>()

    fun setTools(tools: List<Any>) {
        registeredTools.clear()
        registeredTools.addAll(tools)
    }

    suspend fun initialize() = withContext(Dispatchers.Default) {
        try {
            if (engine == null) {
                Log.d("GemmaTest", "正在初始化 Engine...")
                val engineConfig = EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU
                )
                engine = Engine(engineConfig).apply { initialize() }
                Log.d("GemmaTest", "Engine 初始化成功")
            }
            
            // 初始化会话池（预创建 3 个会话）
            if (conversationPool == null) {
                Log.d("GemmaTest", "正在初始化会话池...")
                conversationPool = ConversationPool(
                    engine = engine!!,
                    registeredTools = registeredTools,
                    poolSize = 3  // 可根据设备性能调整（低端机可设为 2）
                )
                conversationPool?.initialize()
            }
            Log.d("GemmaTest", "初始化完全成功（含会话池）")
        } catch (e: Exception) {
            Log.e("GemmaTest", "初始化失败: ${e.message}")
            throw e
        }
    }

    suspend fun testChat(input: String, onResponse: (String) -> Unit) =
        withContext(Dispatchers.Default) {
            val pool = conversationPool ?: run {
                Log.e("GemmaTest", "会话池未初始化")
                return@withContext
            }
            
            // 从池中快速获取一个就绪会话（无需等待重建）
            val conversation = pool.acquire()
            Log.d("GemmaTest", "从会话池获取会话，当前池状态: ${pool.getPoolStatus()}")
//            delay(10000)
            try {
                conversation.sendMessageAsync(Message.of(input.trim())).collect { response ->
                    val rawText = response.toString()
                    Log.d("GemmaTest", "流式输出 Token: $rawText")
                    onResponse(rawText)
                }
            } catch (e: Exception) {
                Log.e("GemmaTest", "推理流被异常中断: ${e.message}")
            } finally {
                Log.d("GemmaTest", "流式推理回答完毕")
                // 用完后归还给池，后台会自动重建
                pool.release(conversation)
                Log.d("GemmaTest", "会话已归还池，当前池状态: ${pool.getPoolStatus()}")
            }
        }

    fun release() {
        Log.d("GemmaTest", ">>> 真正的释放动作被执行 <<<")
        conversationPool?.destroy()
        conversationPool = null
        engine?.close()
        engine = null
    }

}

