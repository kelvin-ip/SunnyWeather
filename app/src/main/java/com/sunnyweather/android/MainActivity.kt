package com.sunnyweather.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
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
import com.sunnyweather.android.ui.weather.WeatherActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    private lateinit var deployment: ModelDeployment
    private var speechRecognizer: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etInput = findViewById<EditText>(R.id.et_input)
        val btnSend = findViewById<Button>(R.id.btn_send)
        val btnVoice = findViewById<Button>(R.id.btn_voice)
        val tvConsole = findViewById<TextView>(R.id.tv_console)

        // 1. 初始化工具类和引擎
        val weatherTool = CityWeatherTool { place ->
            runOnUiThread {
                val intent = Intent(this, WeatherActivity::class.java).apply {
                    putExtra("location_lng", place.location.lng)
                    putExtra("location_lat", place.location.lat)
                    putExtra("place_name", place.name)
                }
                startActivity(intent)
            }
        }

        lifecycleScope.launch {
            try {
                tvConsole.text = "正在初始化引擎，请稍候..."
                val path = FileUtils.copyAssetToFiles(this@MainActivity, "mobile-actions_q8_ekv1024.litertlm")
                deployment = ModelDeployment(path)
                deployment.setTools(listOf(weatherTool))
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

        // 3. 语音输入逻辑
        btnVoice.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            } else {
                startVoiceRecognition(etInput, tvConsole)
            }
        }
    }

    private fun processInput(inputText: String, etInput: EditText, tvConsole: TextView) {
        tvConsole.append("\n\n>>> 用户: $inputText")
        etInput.setText("")

        lifecycleScope.launch {
            try {
                deployment.testChat(inputText) { response ->
                    runOnUiThread {
                        tvConsole.append("\n模型回答: $response")
                    }
                }
            } catch (e: Exception) {
                Log.e("GemmaTest", "推理过程异常", e)
            }
        }
    }

    private fun startVoiceRecognition(etInput: EditText, tvConsole: TextView) {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    tvConsole.append("\n[语音识别] 请说话...")
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    tvConsole.append("\n[语音识别] 识别中...")
                }
                override fun onError(error: Int) {
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "音频问题"
                        SpeechRecognizer.ERROR_CLIENT -> "客户端问题"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "权限不足"
                        SpeechRecognizer.ERROR_NETWORK -> "网络问题"
                        SpeechRecognizer.ERROR_NO_MATCH -> "未匹配到语音"
                        else -> "未知错误: $error"
                    }
                    tvConsole.append("\n[语音识别错误] $message")
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        tvConsole.append("\n[语音识别结果] $text")
                        processInput(text, etInput, tvConsole)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
        }
        speechRecognizer?.startListening(intent)
    }

    override fun onDestroy() {
        if (::deployment.isInitialized) {
            deployment.release()
        }
        speechRecognizer?.destroy()
        super.onDestroy()
    }
}