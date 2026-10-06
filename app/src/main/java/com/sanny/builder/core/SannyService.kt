package com.sanny.builder.core

import android.content.Context
import android.util.Log
import com.sun.jna.ptr.PointerByReference
import java.util.concurrent.Executors

/**
 * 语言服务封装：管理 core 生命周期 + 文档通知 + 符号查询。
 * 数据目录：filesDir/sanny（首次启动从 assets 解压）。
 */
class SannyService(private val context: Context) {

    companion object {
        private const val TAG = "SannyService"
        const val EDITOR_HANDLE = 1
    }

    /** JNA 加载失败时不崩溃，coreReady=false 让 UI 显示降级提示 */
    private val core: SannyCore? = try {
        SannyCore.INSTANCE
    } catch (e: Throwable) {
        Log.e(TAG, "failed to load libcore.so via JNA", e)
        null
    }
    private var server: com.sun.jna.Pointer? = null
    private val executor = Executors.newSingleThreadExecutor()

    fun isReady(): Boolean = server != null
    fun coreReady(): Boolean = core != null

    private var currentDataDir: String? = null

    fun init() {
        executor.execute {
            try {
                val c = core ?: return@execute
                val dataDir = AssetsHelper.extractAssets(context)
                c.language_service_set_data_dir(dataDir.absolutePath)
                server = c.language_service_new()
                Log.i(TAG, "language service created: ${server}")
            } catch (e: Throwable) {
                Log.e(TAG, "init failed", e)
            }
        }
    }

    /** 切换游戏模式：语言服务数据目录固定（data/ 共享），连接参数按模式子目录调整 */
    fun switchMode(mode: com.sanny.builder.compiler.GameMode) {
        currentDataDir = context.filesDir.resolve("sanny/data").resolve(mode.dataDir).absolutePath
    }

    fun connect() {
        val s = server ?: return
        val c = core ?: return
        var dataDir = currentDataDir ?: context.filesDir.resolve("sanny/data/sa_sbl").absolutePath
        var constants = "$dataDir/constants.txt"
        var classes = "$dataDir/classes.db"
        // 模式数据缺失（如 ps2/vcs_ps2 无 constants/classes）时回退到完整数据模式
        if (!java.io.File(constants).isFile || !java.io.File(classes).isFile) {
            Log.w(TAG, "mode data missing (constants=$constants classes=$classes); fallback sa_sbl")
            dataDir = context.filesDir.resolve("sanny/data/sa_sbl").absolutePath
            constants = "$dataDir/constants.txt"
            classes = "$dataDir/classes.db"
        }
        c.language_service_client_connect_in_memory(
            s, EDITOR_HANDLE,
            constants, classes
        )
        Log.i(TAG, "client connected (data=$dataDir)")
    }

    fun notifyTextChanged(text: String) {
        val s = server ?: return
        val c = core ?: return
        c.language_service_client_notify_on_change(s, EDITOR_HANDLE, text)
    }

    fun isEnabled(): Boolean {
        val s = server ?: return false
        val c = core ?: return false
        return c.language_service_is_enabled(s, EDITOR_HANDLE) != 0.toByte()
    }

    fun find(symbol: String, line: Int): SymbolInfo? {
        val s = server ?: return null
        val c = core ?: return null
        val info = SymbolInfo()
        val ok = c.language_service_find(s, symbol, EDITOR_HANDLE, line, info)
        return if (ok != 0.toByte()) info else null
    }

    /** 按名字过滤补全候选（+ 常量/变量/函数名），返回字符串列表 */
    fun filterSymbols(needle: String, line: Int): List<String> {
        val s = server ?: return emptyList()
        val c = core ?: return emptyList()
        val dict = c.dictionary_str_by_str_new()
        try {
            core.language_service_filter_constants_by_name(s, EDITOR_HANDLE, needle, line, dict)
            val count = core.dictionary_str_by_str_get_count(dict).toInt()
            val result = mutableListOf<String>()
            for (i in 0 until count) {
                val key = PointerByReference()
                val value = PointerByReference()
                if (c.dictionary_str_by_str_get_entry(dict, i.toLong(), key, value) != 0.toByte()) {
                    key.pointer?.getString(0)?.let { result.add(it) }
                }
            }
            return result
        } finally {
            c.dictionary_str_by_str_free(dict)
        }
    }

    fun formatFunctionSignature(value: String): String? {
        val s = server ?: return null
        val c = core ?: return null
        val out = PointerByReference()
        val ok = c.language_service_format_function_signature(s, value, out)
        return if (ok != 0.toByte()) out.pointer?.getString(0) else null
    }

    fun disconnect() {
        val s = server ?: return
        val c = core ?: return
        c.language_service_client_disconnect(s, EDITOR_HANDLE)
    }

    fun shutdown() {
        disconnect()
        server?.let { core?.language_service_free(it) }
        server = null
        executor.shutdown()
    }
}
