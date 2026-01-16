package com.sunnyweather.android.logic.functiongemma

import android.util.Log
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentLinkedQueue


class ConversationPool(
    private val engine: Engine,
    private val registeredTools: List<Any>,
    private val poolSize: Int = 3  // 池初始大小
) {

    private val TAG = "ConversationPool"
    // 无界、非阻塞的并发队列（FIFO）
    private val readyQueue = ConcurrentLinkedQueue<Conversation>()
    private val poolScope = CoroutineScope(Dispatchers.Default + SupervisorJob()) //协程作用域，确保协程间互不影响
    private val systemMessage = "You are a model that can do function calling with the following functions."

    suspend fun initialize() = withContext(Dispatchers.Default) {
        Log.d(TAG, "开始初始化会话池，目标数量: $poolSize")
        repeat(poolSize) { index ->
            try {
                val conversation = createNewConversation()
                readyQueue.offer(conversation)
                Log.d(TAG, "成功创建会话 #${index + 1}")
            } catch (e: Exception) {
                Log.e(TAG, "创建会话 #${index + 1} 失败: ${e.message}", e)
            }
        }
        Log.d(TAG, "会话池初始化完成，当前就绪数量: ${readyQueue.size}")
    }

     fun acquire(): Conversation{
        // 尝试从就绪队列获取
        var conversation = readyQueue.poll()
        if (conversation == null) {
            Log.w(TAG, "就绪队列为空，临时创建新会话（可能有轻微延迟）")
            conversation = createNewConversation()
        } else {
            Log.d(TAG, "从就绪队列获取会话，剩余数量: ${readyQueue.size}")
        }

        return conversation
    }
    
    /**
     * 归还会话（用完后调用）
     * 会话会被异步重建，重建完成后自动回到就绪队列
     */
    fun release(conversation: Conversation) {
        poolScope.launch {
            try {
                Log.d(TAG, "开始重建会话（清理上下文）")
                conversation.close()
                val newConversation = createNewConversation()
                readyQueue.offer(newConversation)
                Log.d(TAG, "会话重建完成，当前就绪: ${readyQueue.size}")
            } catch (e: Exception) {
                Log.e(TAG, "会话重建失败: ${e.message}", e)
                try {
                    readyQueue.offer(createNewConversation())
                } catch (ex: Exception) {
                    Log.e(TAG, "补充会话失败: ${ex.message}", ex)
                }
            }
        }
    }

    /**
     * 创建新会话
     */
    private fun createNewConversation(): Conversation {
        val convConfig = ConversationConfig(
            tools = registeredTools,
            systemMessage = Message.of(systemMessage)
        )
        return engine.createConversation(convConfig)
    }
    

    /**
     * 销毁整个会话池
     */
    fun destroy() {
        poolScope.cancel()
        
        readyQueue.forEach { 
            try {
                it.close()
            } catch (e: Exception) {
                Log.e(TAG, "关闭会话失败", e)
            }
        }
        readyQueue.clear()
        Log.d(TAG, "会话池已销毁")
    }
}

