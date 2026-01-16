package com.sunnyweather.android.logic.functiongemma.Tools

import android.util.Log
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.sunnyweather.android.logic.model.Place
import com.sunnyweather.android.logic.network.SunnyWeatherNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class CityWeatherTool(
    private val onCityFound: (Place) -> Unit,
    private val onToolExecuting: ((String) -> Unit)? = null  // 工具执行时的回调
) {

    @Tool(description = "根据城市名称查询天气信息。当用户询问特定城市的天气、气温或环境时调用。")
    fun search_weather(
        @ToolParam(description = "城市名称，例如：北京、上海、London") city_name: String
    ): Map<String, Any> {
        // 通知 UI：工具开始执行
        onToolExecuting?.invoke("🔧 正在查询「$city_name」的天气数据...")
        Log.d("GemmaTest", ">>> 拦截成功:开始执行 search_weather: $city_name")

        return try {
            runBlocking(Dispatchers.IO) {
                val response = SunnyWeatherNetwork.searchPlaces(city_name)
                if (response.status == "ok" && response.places.isNotEmpty()) {
                    val place = response.places[0]
                    onCityFound(place)
                    mapOf(
                        "result" to "success",
                        "city_name" to place.name,
                        "context" to "Navigating to weather detail page"
                    )
                } else {
                    mapOf("result" to "error", "message" to "City not found: $city_name")
                }
            }
        } catch (e: Exception) {
            Log.e("CityWeatherTool", "Execution failed", e)
            mapOf("result" to "error", "reason" to (e.message ?: "unknown error"))
        }
    }
}