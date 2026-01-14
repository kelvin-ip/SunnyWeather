package com.sunnyweather.android.logic.functiongemma

import android.util.Log
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam

class SampleToolSet {
//    @Tool(description = "Get the current weather for a city")
//    fun getCurrentWeather(
//        @ToolParam(description = "The city name, e.g., San Francisco") city: String,
//        @ToolParam(description = "Optional country code, e.g., US") country: String? = null,
//        @ToolParam(description = "Temperature unit (celsius or fahrenheit). Default: celsius") unit: String = "celsius"
//    ): Map<String, Any> {
//        Log.d("GemmaTest", "getCurrentWeather")
//
//        // In a real application, you would call a weather API here
//        return mapOf("temperature" to 25, "unit" to  unit, "condition" to "Sunny")
//    }

    @Tool(description = "Get the sum of a list of numbers.")
    fun sum(
        @ToolParam(description = "The numbers, could be floating point.") numbers: List<Double>,
    ): Double {
        Log.d("GemmaTest", ">>> 拦截成功：正在执行 sum <<<")
        return numbers.sum()
    }
}