package com.sunnyweather.android.logic.functiongemma

import android.content.Context
import java.io.File

object FileUtils {
    fun copyAssetToFiles(context: Context, fileName: String): String {
        val destFile = File(context.filesDir, fileName)
        // 辩证改进：如果文件不存在或文件大小异常（例如小于1MB），重新拷贝
        if (!destFile.exists() || destFile.length() < 1024 * 1024) {
            context.assets.open(fileName).use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return destFile.absolutePath
    }
}