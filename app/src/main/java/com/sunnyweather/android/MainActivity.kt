package com.sunnyweather.android

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sunnyweather.android.logic.functiongemma.FileUtils
import com.sunnyweather.android.logic.functiongemma.ModelDeployment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    private lateinit var deployment: ModelDeployment
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // setContentView(R.layout.activity_main)

        lifecycleScope.launch {
            try {
                val path = FileUtils.copyAssetToFiles(this@MainActivity, "mobile-actions_q8_ekv1024.litertlm")
                deployment = ModelDeployment(path)

                Log.d("GemmaTest", "正在加载引擎...")
                deployment.initialize()
                // 关键点：延迟 1 秒确保引擎状态彻底同步
                delay(1000)

                Log.d("GemmaTest", "发起测试提问...")
                deployment.testChat("check system status ") { response ->
                    Log.e("SUCCESS_OUTPUT", "流式回答: $response")
                }


            } catch (e: Exception) {
                Log.e("FunctionGemmaTest", "主流程异常", e)
            }
        }
    }

    override fun onDestroy() {
        // 关键：在 Activity 销毁时才真正关闭大模型
        deployment?.release()
        super.onDestroy()
    }
}