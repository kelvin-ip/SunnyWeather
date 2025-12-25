package com.sunnyweather.android.logic.functiongemma

import android.content.Context
import java.io.File

object FileUtils {
    fun copyAssetToFiles(context: Context, fileName: String): String {
        val destFile = File(context.filesDir, fileName)
        if (!destFile.exists()) {
            context.assets.open(fileName).use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return destFile.absolutePath
    }
}