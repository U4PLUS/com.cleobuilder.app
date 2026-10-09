package com.sanny.builder.core

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 首次启动把 assets/sanny 数据目录解压到 filesDir/sanny；
 * opcode 表可另行解压到外部目录供用户浏览、勾选、自定义。
 */
object AssetsHelper {
    private const val TAG = "AssetsHelper"
    private const val ASSET_ROOT = "sanny"

    /** 把内置 opcode 表（sa 与 sa_mobile 两目录下的 .ini）解压到 destBase/模式下 */
    fun extractOpcodeTables(context: Context, destBase: File): Int {
        var n = 0
        for (modeId in listOf("sa", "sa_mobile")) {
            val srcDir = "$ASSET_ROOT/data/$modeId"
            val dest = File(destBase, modeId).apply { mkdirs() }
            context.assets.list(srcDir)?.forEach { name ->
                if (name.endsWith(".ini")) {
                    try {
                        context.assets.open("$srcDir/$name").use { inp ->
                            File(dest, name).outputStream().use { out -> inp.copyTo(out) }
                        }
                        n++
                    } catch (e: Exception) {
                        Log.w(TAG, "extract $name failed: " + e.message)
                    }
                }
            }
        }
        Log.i(TAG, "extracted $n opcode tables to " + destBase.absolutePath)
        return n
    }

    fun extractAssets(context: Context): File {
        val dest = File(context.filesDir, ASSET_ROOT)
        if (dest.exists() && dest.listFiles()?.isNotEmpty() == true) {
            return dest
        }
        Log.i(TAG, "extracting assets to " + dest.absolutePath)
        copyAssetDir(context, ASSET_ROOT, dest)
        return dest
    }

    private fun copyAssetDir(context: Context, assetPath: String, dest: File) {
        val assets = context.assets
        val list = assets.list(assetPath) ?: return
        dest.mkdirs()
        for (name in list) {
            val childAsset = if (assetPath.isEmpty()) name else "$assetPath/$name"
            val childDest = File(dest, name)
            if (childAsset.endsWith("/")) {
                copyAssetDir(context, childAsset, childDest)
            } else {
                val names = assets.list(childAsset)
                if (names != null && names.isNotEmpty()) {
                    copyAssetDir(context, childAsset, childDest)
                } else {
                    assets.open(childAsset).use { input ->
                        childDest.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }
}
