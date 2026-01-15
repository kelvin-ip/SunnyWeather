package com.sunnyweather.android.logic.functiongemma.Tools

import android.util.Log
import com.google.ai.edge.litertlm.Tool

class SystemStatusTool {

    @Tool(description = "Retrieve current hardware status")
    fun check_status(): Map<String, Any> {
        Log.d("GemmaTest", ">>> 拦截成功：正在执行 check_status <<<")

        return try {
            mapOf(
                "temperature" to 35, // 使用数字而非字符串，方便模型判断（例如：是否过热）
                "unit" to "Celsius",
                "health_status" to "Healthy",
                "system_message" to "All hardware systems operational",
                "timestamp" to System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e("GemmaTest", "系统状态获取失败", e)
            mapOf("status" to "error", "message" to (e.message ?: "Unknown hardware error"))
        }
    }
}