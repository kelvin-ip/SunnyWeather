package com.sunnyweather.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sunnyweather.android.logic.functiongemma.Tools.CityWeatherTool
import com.sunnyweather.android.logic.functiongemma.FileUtils
import com.sunnyweather.android.logic.functiongemma.ModelDeployment
import com.sunnyweather.android.logic.functiongemma.Tools.SampleToolSet
import com.sunnyweather.android.logic.functiongemma.Tools.SystemStatusTool
import com.sunnyweather.android.logic.service.VoiceAssistantManager
import com.sunnyweather.android.ui.weather.WeatherActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    private lateinit var deployment: ModelDeployment
    private var isListening = false
    private lateinit var btnVoice: Button          // 语音按钮（全局）
    private lateinit var etInput: EditText         // 输入框（可选全局）
    private lateinit var tvConsole: TextView       // 日志控制台（可选全局）
    
    // Vosk 语音助手管理器
    private var voiceAssistant: VoiceAssistantManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d("GemmaTest", "Supported ABIs: ${android.os.Build.SUPPORTED_ABIS.joinToString()}")
         etInput = findViewById<EditText>(R.id.et_input)
        val btnSend = findViewById<Button>(R.id.btn_send)
         btnVoice = findViewById<Button>(R.id.btn_voice)
         tvConsole = findViewById<TextView>(R.id.tv_console)

        val weatherTool = CityWeatherTool { place ->
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    val intent = Intent(this, WeatherActivity::class.java).apply {
                        putExtra("location_lng", place.location.lng)
                        putExtra("location_lat", place.location.lat)
                        putExtra("place_name", place.name)
                    }
                    startActivity(intent)
                }
            }
        }

        lifecycleScope.launch {
            try {
                tvConsole.text = "正在初始化引擎，请稍候..."
                val path = FileUtils.copyAssetToFiles(this@MainActivity, "mobile-actions_q8_ekv1024.litertlm")
                deployment = ModelDeployment(path)
                deployment.setTools(listOf(weatherTool, SampleToolSet(), SystemStatusTool()))
                deployment.initialize()
                // 初始化 Vosk 语音助手（后台监听唤醒词）
                initVoiceAssistant()
                tvConsole.text = "系统就绪。请输入指令或使用语音输入。"
                
            } catch (e: Exception) {
                Log.e("GemmaTest", "初始化异常", e)
                tvConsole.text = "初始化失败: ${e.message}"
            }
        }

        btnSend.setOnClickListener {
            val inputText = etInput.text.toString().trim()
            if (inputText.isBlank()) return@setOnClickListener
            processInput(inputText, etInput, tvConsole)
        }

        // 在 onCreate 中修改 btnVoice 的监听器
        btnVoice.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
                    } else {
                        // 视觉反馈
                        btnVoice.text = "正在录音..."
                        voiceAssistant?.startManualRecognition()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 恢复按钮文字
                    btnVoice.text = "按住 说话"
                    voiceAssistant?.stopManualRecognition()
                    true
                }
                else -> false
            }
        }
    }

    private fun processInput(inputText: String, etInput: EditText, tvConsole: TextView) {
        tvConsole.append("\n\n>>> 用户: $inputText")
        etInput.setText("")

        lifecycleScope.launch {
            try {
                // 不再需要手动重建会话，会话池会自动提供干净的会话
                tvConsole.append("\n模型回答: ") // 预留前缀

                // 调用流式方法（会话池会自动管理上下文）
                deployment.testChat(inputText) { response ->
                    // 因为 collect 在 Dispatchers.Default 执行，更新 UI 需切回主线程
                    runOnUiThread {
                        // 流式追加文本
                        tvConsole.append(response)
                    }
                }
            } catch (e: Exception) {
                Log.e("GemmaTest", "流式交互过程异常", e)
            }
        }
    }

    // 初始化 Vosk 语音助手
    private fun initVoiceAssistant() {
        voiceAssistant = VoiceAssistantManager(this).apply {
            init(object : VoiceAssistantManager.VoiceCallback {
                override fun onWakeWordDetected(wakeWord: String) {
                    runOnUiThread {
                        tvConsole.append("\n\n🎙️ 检测到唤醒词: $wakeWord")
                        tvConsole.append("\n[系统已进入监听模式，请说话...]")
                        btnVoice.text = "正在倾听..." // 更新按钮文字作为反馈
                        Toast.makeText(this@MainActivity, "已唤醒，请说话...", Toast.LENGTH_SHORT).show()
                    }
                }
                
                override fun onSpeechResult(result: String) {
                    runOnUiThread {
                        btnVoice.text = "按住 说话" // 恢复按钮文字
                        if (result.isNotEmpty()) {
                            tvConsole.append("\n👤 语音识别: $result")
                            etInput.setText(result)
                            processInput(result, etInput, tvConsole)
                        } else {
                            tvConsole.append("\n[未检测到有效语音内容]")
                        }
                    }
                }
                
                override fun onError(error: String) {
                    runOnUiThread {
                        btnVoice.text = "按住 说话" // 恢复按钮文字
                        tvConsole.append("\n❌ 识别失败: $error")
                    }
                }
            })
        }
        
        tvConsole.append("\n✅ Vosk 语音助手已启动，正在后台监听唤醒词...")
    }

    // 权限回调
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            voiceAssistant?.startManualRecognition()
        }
        // 拒绝权限时不做任何提示（符合需求）
    }

    override fun onDestroy() {
        deployment.release()
        voiceAssistant?.destroy()
        super.onDestroy()
    }
}