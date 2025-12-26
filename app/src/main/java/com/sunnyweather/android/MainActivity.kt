package com.sunnyweather.android

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
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
        setContentView(R.layout.activity_main)

        val etInput = findViewById<EditText>(R.id.et_input)
        val btnSend = findViewById<Button>(R.id.btn_send)
        val tvConsole = findViewById<TextView>(R.id.tv_console)

        // 1. 初始化逻辑：只在 App 启动时执行一次
        lifecycleScope.launch {
            try {
                tvConsole.text = "正在初始化引擎，请稍候..."
                val path = FileUtils.copyAssetToFiles(this@MainActivity, "mobile-actions_q8_ekv1024.litertlm")
                deployment = ModelDeployment(path)
                deployment.initialize()
                tvConsole.text = "系统就绪。请输入指令并点击发送。"
            } catch (e: Exception) {
                Log.e("GemmaTest", "初始化异常", e)
                tvConsole.text = "初始化失败: ${e.message}"
            }
        }

        // 2. 循环等待的核心：通过按钮点击触发下一次推理
        btnSend.setOnClickListener {
            val inputText = etInput.text.toString().trim()
            if (inputText.isBlank()) return@setOnClickListener

            tvConsole.append("\n\n>>> 用户: $inputText")
            etInput.setText("") // 清空输入框，准备下一次

            lifecycleScope.launch {
                try {
                    // 调用你之前优化过的 testChat
                    deployment.testChat(inputText) { response ->
                        // 只有识别不到函数时才会回调这里
                        runOnUiThread {
                            tvConsole.append("\n模型回答: $response")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GemmaTest", "推理过程异常", e)
                }
            }
        }
    }

    override fun onDestroy() {
        // 只有用户主动退出/杀掉进程，才会执行这里的释放
        if (::deployment.isInitialized) {
            deployment.release()
        }
        super.onDestroy()
    }
}