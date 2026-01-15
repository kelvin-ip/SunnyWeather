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
    private val poolSize: Int = 3  // 池大小（可根据设备性能调整）
) {
    private val TAG = "ConversationPool"
    
    // 就绪队列（随时可用的干净会话）
    private val readyQueue = ConcurrentLinkedQueue<Conversation>()
    
    // 使用中的会话集合
    private val inUseSet = mutableSetOf<Conversation>()
    
    private val mutex = Mutex()
    private val poolScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // 系统消息模板
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
    
    /**
     * 获取一个就绪的会话（核心接口）
     * 如果没有就绪的会话，会临时创建一个（降级策略）
     */
    suspend fun acquire(): Conversation = mutex.withLock {
        // 尝试从就绪队列获取
        var conversation = readyQueue.poll()
        
        if (conversation == null) {
            Log.w(TAG, "就绪队列为空，临时创建新会话（可能有轻微延迟）")
            conversation = createNewConversation()
        } else {
            Log.d(TAG, "从就绪队列获取会话，剩余数量: ${readyQueue.size}")
        }
        
        // 标记为使用中
        inUseSet.add(conversation)
        
        return conversation
    }
    
    /**
     * 归还会话（用完后调用）
     * 会话会被异步重建，重建完成后自动回到就绪队列
     */
    fun release(conversation: Conversation) {
        poolScope.launch {
            try {
                mutex.withLock {
                    inUseSet.remove(conversation)
                }
                
                Log.d(TAG, "开始重建会话（清理上下文）")
                
                // 关闭旧会话
                conversation.close()
                
                // 创建新的干净会话
                val newConversation = createNewConversation()
                
                mutex.withLock {
                    readyQueue.offer(newConversation)
                    Log.d(TAG, "会话重建完成并回到就绪队列，当前就绪: ${readyQueue.size}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "会话重建失败: ${e.message}", e)
                // 重建失败则尝试补充一个新会话
                try {
                    val newConv = createNewConversation()
                    mutex.withLock {
                        readyQueue.offer(newConv)
                        Log.d(TAG, "已补充新会话")
                    }
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
     * 获取池状态（用于调试）
     */
    fun getPoolStatus(): String {
        return "就绪: ${readyQueue.size}, 使用中: ${inUseSet.size}"
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
        
        inUseSet.forEach { 
            try {
                it.close()
            } catch (e: Exception) {
                Log.e(TAG, "关闭使用中会话失败", e)
            }
        }
        inUseSet.clear()
        
        Log.d(TAG, "会话池已销毁")
    }
}

