package com.sunnyweather.android.logic.functiongemma

import android.util.Log
import com.google.ai.edge.litertlm.Tool
import com.sunnyweather.android.logic.model.Place
import com.sunnyweather.android.logic.network.SunnyWeatherNetwork
import kotlinx.coroutines.runBlocking

class CityWeatherTool(private val onCityFound: (Place) -> Unit) {

    @Tool(description = "根据城市名称查询天气。如果用户提到想看某个城市的天气，请调用此函数。参数 city_name 是城市名。")
    fun search_weather(city_name: String): String {
        Log.d("CityWeatherTool", "Searching for city: $city_name")
        return try {
            val response = runBlocking {
                SunnyWeatherNetwork.searchPlaces(city_name)
            }
            if (response.status == "ok" && response.places.isNotEmpty()) {
                val place = response.places[0]
                onCityFound(place)
                "已找到城市：${place.name}，正在跳转到天气详情页。"
            } else {
                "未能找到名为 $city_name 的城市。"
            }
        } catch (e: Exception) {
            Log.e("CityWeatherTool", "Error searching city", e)
            "查询城市时出错: ${e.message}"
        }
    }
}

