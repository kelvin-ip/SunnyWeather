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
                val path = FileUtils.copyAssetToFiles(this@MainActivity, "gemma_gen.litertlm")
                deployment = ModelDeployment(path)

                Log.d("FunctionGemmaTest", "正在加载引擎...")
                deployment.initialize()
                // 关键点：延迟 1 秒确保引擎状态彻底同步
                delay(1000)

                Log.d("FunctionGemmaTest", "发起测试提问，请耐心等待 30-60 秒...")
                deployment.testChat("What is the current system status?") { response ->
                    Log.e("SUCCESS_OUTPUT", "Final Answer: $response")
                }


            } catch (e: Exception) {
                Log.e("FunctionGemmaTest", "主流程异常", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        deployment.release()
    }
}