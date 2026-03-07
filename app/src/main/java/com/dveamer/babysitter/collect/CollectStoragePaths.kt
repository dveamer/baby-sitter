package com.dveamer.babysitter.collect

import android.content.Context
import java.io.File

class CollectStoragePaths(context: Context) {
    private val base: File = context.applicationContext.filesDir

    val collectDir: File = File(base, "collect")
    val memoryDir: File = File(base, "memory")
    val workDir: File = File(base, "work")

    fun ensureDirectories() {
        collectDir.mkdirs()
        memoryDir.mkdirs()
        workDir.mkdirs()
    }
}
