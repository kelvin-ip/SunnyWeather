package com.sunnyweather.android.logic.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.*

class VoiceAssistantManager(private val context: Context) {
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var callback: VoiceCallback? = null
    
    companion object {
        private const val TAG = "VoiceAssistantManager"
    }
    
    interface VoiceCallback {
        fun onWakeWordDetected(wakeWord: String)
        fun onSpeechResult(result: String)
        fun onError(error: String)
    }
    
    // 监听唤醒词广播
    private val wakeWordReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val wakeWord = intent?.getStringExtra("wake_word") ?: return
            Log.d(TAG, "收到唤醒词: $wakeWord")
            callback?.onWakeWordDetected(wakeWord)
            
            // 关键修复：在启动完整识别前，先停止 Vosk 监听以释放麦克风
            toggleWakeUpService(false)
            
            // 启动完整语音识别
            startFullSpeechRecognition()
        }
    }
    
    // 控制 Vosk 服务的监听状态
    private fun toggleWakeUpService(enable: Boolean) {
        val action = if (enable) "START_LISTENING" else "STOP_LISTENING"
        val intent = Intent(context, VoiceWakeUpService::class.java).apply {
            this.action = action
        }
        Log.d(TAG, "发送指令给 Vosk 服务: $action")
        if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun init(callback: VoiceCallback) {
        this.callback = callback
        
        // 注册广播接收器
        val filter = IntentFilter("com.sunnyweather.android.WAKE_WORD_DETECTED")
        // Android 13 (API 33) 及以上需要显式声明标志
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(wakeWordReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            // Android 8.0 (API 26) 到 Android 12 也推荐使用此方法
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(wakeWordReceiver, filter)
        }
        
        // 启动 Vosk 监听服务
        startVoiceWakeUpService()
    }

    /**
     * 手动启动语音识别（用于按钮点击等场景）
     */
    fun startManualRecognition() {
        Log.d(TAG, "手动触发语音识别")
        // 1. 停止 Vosk 监听
        toggleWakeUpService(false)
        // 2. 启动系统识别
        startFullSpeechRecognition()
    }

    /**
     * 手动停止语音识别（用于松开按钮等场景）
     */
    fun stopManualRecognition() {
        Log.d(TAG, "手动停止语音识别")
        // 注意：Vosk 会在 onResults 或 onError 中自动恢复
        // 我们只需让 SpeechRecognizer 停止录音并开始解析
        speechRecognizer?.stopListening()
    }

    private fun startVoiceWakeUpService() {
        val intent = Intent(context, VoiceWakeUpService::class.java).apply {
            action = "START_LISTENING"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
    
    private fun startFullSpeechRecognition() {
        // 检查设备是否支持语音识别
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "设备不支持语音识别")
            callback?.onError("设备不支持语音识别")
            return
        }
        
        // 释放旧的识别器
        speechRecognizer?.destroy()
        
        // 创建新的 SpeechRecognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "准备接收语音输入...")
                }
                
                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "开始说话")
                }
                
                override fun onRmsChanged(rmsdB: Float) {
                    // 音量变化（可用于UI反馈）
                }
                
                override fun onBufferReceived(buffer: ByteArray?) {}
                
                override fun onEndOfSpeech() {
                    Log.d(TAG, "说话结束，等待识别结果...")
                }
                
                override fun onError(error: Int) {
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "音频错误"
                        SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "权限不足"
                        SpeechRecognizer.ERROR_NETWORK -> "网络错误"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                        SpeechRecognizer.ERROR_NO_MATCH -> "无匹配结果"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙"
                        SpeechRecognizer.ERROR_SERVER -> "服务器错误"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有语音输入"
                        else -> "未知错误(代码:$error)"
                    }
                    Log.e(TAG, "识别错误: $errorMsg (错误码: $error)")
                    callback?.onError(errorMsg)
                    
                    // 关键修复：识别出错后，重新开启 Vosk 监听
                    toggleWakeUpService(true)
                }
                
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val result = matches?.firstOrNull() ?: ""
                    Log.d(TAG, "识别结果: $result")
                    callback?.onSpeechResult(result)
                    
                    // 关键修复：识别成功后，重新开启 Vosk 监听
                    toggleWakeUpService(true)
                }
                
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partialText = matches?.firstOrNull() ?: ""
                    if (partialText.isNotEmpty()) {
                        Log.d(TAG, "部分识别结果: $partialText")
                    }
                }
                
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        
        // 启动识别
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // 增加识别等待时间
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }
        
        Log.d(TAG, "启动语音识别...")
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "启动识别失败", e)
            callback?.onError("启动识别失败: ${e.message}")
        }
    }
    
    fun destroy() {
        // 停止服务
        val intent = Intent(context, VoiceWakeUpService::class.java).apply {
            action = "STOP_LISTENING"
        }
        context.startService(intent)
        context.stopService(Intent(context, VoiceWakeUpService::class.java))
        
        // 释放资源
        try {
            context.unregisterReceiver(wakeWordReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "注销广播失败", e)
        }
        
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}

