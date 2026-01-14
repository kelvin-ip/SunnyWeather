package com.sunnyweather.android.logic.functiongemma

import android.util.Log
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam // 必须导入
import com.sunnyweather.android.logic.model.Place
import com.sunnyweather.android.logic.network.SunnyWeatherNetwork
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers

class CityWeatherTool(private val onCityFound: (Place) -> Unit) {

    @Tool(description = "根据城市名称查询天气信息。当用户询问特定城市的天气、气温或环境时调用。")
    fun search_weather(
        @ToolParam(description = "城市名称，例如：北京、上海、London") city_name: String
    ): Map<String, Any> { // 规范 1：返回 Map 而非 String
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
            // 规范 4：捕获 429 或网络异常，以成功的方式告诉模型错误原因，防止崩溃
            mapOf("result" to "error", "reason" to (e.message ?: "unknown error"))
        }
    }
}