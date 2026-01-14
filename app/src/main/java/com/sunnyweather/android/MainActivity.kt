package com.sunnyweather.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
import com.sunnyweather.android.logic.functiongemma.CityWeatherTool
import com.sunnyweather.android.logic.functiongemma.FileUtils
import com.sunnyweather.android.logic.functiongemma.ModelDeployment
import com.sunnyweather.android.logic.functiongemma.SampleToolSet
import com.sunnyweather.android.logic.functiongemma.SystemStatusTool
import com.sunnyweather.android.ui.weather.WeatherActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    private lateinit var deployment: ModelDeployment
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    // 全局变量声明（推荐放在类顶部）
    private lateinit var btnVoice: Button          // 语音按钮（全局）
    private lateinit var etInput: EditText         // 输入框（可选全局）
    private lateinit var tvConsole: TextView       // 日志控制台（可选全局）

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
                tvConsole.text = "系统就绪。请输入指令或使用语音输入。"
            } catch (e: Exception) {
                Log.e("GemmaTest", "初始化异常", e)
                tvConsole.text = "初始化失败: ${e.message}"
            }
        }

        // 2. 发送按钮逻辑
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
                        // 视觉反馈：按下变色或改文字（可选）
                        btnVoice.text = "正在录音..."
                        startVoiceRecognition()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 恢复按钮文字
                    btnVoice.text = "按住 说话"
                    stopVoiceRecognition()
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
                deployment.rebuildConversation()

                tvConsole.append("\n模型回答: ") // 预留前缀

                // 2. 调用流式方法
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

    // 启动语音识别（极简版）
    private fun startVoiceRecognition() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    // 可选：这里可以加提示音或振动，但不显示文字
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    // 错误时静默停止，不提示
                    stopVoiceRecognition()
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0].trim()
                        etInput.setText(text)  // 直接填入输入框
                        processInput(text, etInput, tvConsole)  // 处理输入
                    }
                    stopVoiceRecognition()
                }

                override fun onPartialResults(partialResults: Bundle?) {}  // 不显示部分结果
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            stopVoiceRecognition()  // 启动失败也静默停止
        }
    }

    // 立即停止语音识别
// 修改后的停止方法
    private fun stopVoiceRecognition() {
        if (isListening) {
            // stopListening 会触发 onResults，这是长按松开后需要的行为
            speechRecognizer?.stopListening()
            isListening = false
        }
    }

    // 权限回调
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVoiceRecognition()
        }
        // 拒绝权限时不做任何提示（符合需求）
    }

    override fun onDestroy() {
        deployment.release()
        speechRecognizer?.destroy()
        super.onDestroy()
    }
}