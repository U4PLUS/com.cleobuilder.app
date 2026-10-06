package com.sanny.builder.core

import android.content.Context
import android.util.Log
import java.io.File

/** 首次启动把 assets/sanny 数据目录解压到 filesDir/sanny */
object AssetsHelper {
    private const val TAG = "AssetsHelper"
    private const val ASSET_ROOT = "sanny"

    fun extractAssets(context: Context): File {
        val dest = File(context.filesDir, ASSET_ROOT)
        if (dest.exists() && dest.listFiles()?.isNotEmpty() == true) {
            return dest
        }
        Log.i(TAG, "extracting assets to ${dest.absolutePath}")
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
                    // 目录（assets.list 对目录返回子项）
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
