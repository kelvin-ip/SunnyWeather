package com.sunnyweather.android.logic.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.vosk.Model
import org.vosk.Recognizer
import org.json.JSONObject
import java.io.File

class VoiceWakeUpService : Service() {
    
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var isListening = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        private const val TAG = "VoiceWakeUpService"
        private const val CHANNEL_ID = "voice_wake_up_channel"
        private const val NOTIFICATION_ID = 1
        private const val SAMPLE_RATE = 16000
        
        // 关键词列表（支持多个唤醒词）
        private val WAKE_WORDS = listOf("嘿", "嗨", "哈喽")
    }
    
    override fun onCreate() {
        super.onCreate()
        initVoskModel()
        startForeground(NOTIFICATION_ID, createNotification())
    }
    
    private fun initVoskModel() {
        try {
            // 从 assets 复制模型到内部存储（如果还没有）
            val modelDir = File(filesDir, "vosk-model")
            if (!modelDir.exists()) {
                copyAssetFolder("vosk-model", modelDir.absolutePath)
            }
            
            // 加载模型
            model = Model(modelDir.absolutePath)
            
            // 创建识别器（关键词模式，低功耗）
            recognizer = Recognizer(model, SAMPLE_RATE.toFloat()).apply {
                // 设置关键词语法，只识别唤醒词
                setGrammar(createKeywordGrammar())
            }
            
            Log.d(TAG, "Vosk 模型初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "Vosk 模型初始化失败", e)
        }
    }
    
    private fun createKeywordGrammar(): String {
        // 创建简单的关键词语法（只匹配特定词）
        // Vosk 期望的格式: ["[unk]", "keyword1", "keyword2"]
        val json = JSONObject()
        val wordsArray = org.json.JSONArray()
        wordsArray.put("[unk]")  // 添加未知词标记
        WAKE_WORDS.forEach { word ->
            wordsArray.put(word)
        }
        return wordsArray.toString()
    }
    
    private fun copyAssetFolder(srcFolder: String, destPath: String) {
        val destDir = File(destPath)
        if (!destDir.exists()) {
            destDir.mkdirs()
        }
        
        val assetManager = assets
        val files = assetManager.list(srcFolder) ?: return
        
        for (filename in files) {
            val srcPath = "$srcFolder/$filename"
            val destFilePath = "$destPath/$filename"
            
            try {
                // 检查是文件还是文件夹
                val subFiles = assetManager.list(srcPath)
                if (subFiles != null && subFiles.isNotEmpty()) {
                    // 是文件夹，递归复制
                    copyAssetFolder(srcPath, destFilePath)
                } else {
                    // 是文件，直接复制
                    assetManager.open(srcPath).use { input ->
                        File(destFilePath).outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "复制文件失败: $srcPath", e)
            }
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_LISTENING" -> startListening()
            "STOP_LISTENING" -> stopListening()
        }
        return START_STICKY
    }
    
    private fun startListening() {
        if (isListening) return
        isListening = true
        
        serviceScope.launch {
            try {
                val bufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
                
                audioRecord?.startRecording()
                val buffer = ShortArray(bufferSize)
                
                Log.d(TAG, "开始监听唤醒词...")
                
                while (isListening && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    
                    if (readSize > 0 && recognizer?.acceptWaveForm(buffer, readSize) == true) {
                        val result = recognizer?.result
                        handleResult(result)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "录音错误", e)
            }
        }
    }
    
    private fun handleResult(result: String?) {
        if (result.isNullOrEmpty()) return
        
        try {
            val json = JSONObject(result)
            val text = json.optString("text", "").trim()
            
            Log.d(TAG, "识别到: $text")
            
            // 检查是否匹配唤醒词
            if (WAKE_WORDS.any { wakeWord -> 
                text.contains(wakeWord, ignoreCase = true) 
            }) {
                Log.d(TAG, "✅ 唤醒词匹配成功!")
                onWakeWordDetected(text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析结果失败", e)
        }
    }
    
    private fun onWakeWordDetected(wakeWord: String) {
        // 发送广播通知主界面启动 SpeechRecognizer
        val intent = Intent("com.sunnyweather.android.WAKE_WORD_DETECTED")
        intent.putExtra("wake_word", wakeWord)
        sendBroadcast(intent)
        
        // 可选：震动反馈或播放提示音
        // vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
    }
    
    private fun stopListening() {
        isListening = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
    
    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "语音唤醒服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "正在监听唤醒词"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("语音助手")
            .setContentText("正在监听「嘿，Gemma」")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopListening()
        recognizer?.close()
        model?.close()
        serviceScope.cancel()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}

