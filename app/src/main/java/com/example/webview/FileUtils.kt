package com.example.webview

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

private const val TEMP_FILE_NAME = "temp"
private const val EXTENSION_JPG = ".jpg"
private const val DATA_DIR = "MyDataDir"

val Context.authority get() = "${applicationContext.packageName}.my.provider"

internal fun Context.createTempFileUri(
    fileName: String = TEMP_FILE_NAME,
    extension: String = EXTENSION_JPG
): Uri {
    return FileProvider.getUriForFile(
        this,
        authority,
        createTempFile(fileName, extension)
    )
}

internal fun Context.createTempFile(
    fileName: String = TEMP_FILE_NAME,
    extension: String = EXTENSION_JPG
): File {
    val dataDir = getDataDir(this)
    return File("$dataDir/$fileName$extension")
}

fun getDataDir(context: Context): File {
    val dataDir = File("${context.filesDir}/$DATA_DIR")
    dataDir.mkdir()
    return dataDir
}