package com.sanny.builder.ui

import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sanny.builder.R
import com.sanny.builder.compiler.CompilerService
import com.sanny.builder.compiler.GameMode
import com.sanny.builder.core.AssetsHelper
import java.io.File

/**
 * Opcode 表管理页：列出当前模式全部可用表（内置/自定义标识 + 加载状态），
 * 勾选决定加载哪些表；支持重置解压、保存并重载。
 */
class OpcodeManagerActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("cleo_builder", MODE_PRIVATE) }
    private lateinit var mode: GameMode
    private val compiler = CompilerService()
    private val checks = ArrayList<Pair<String, CheckBox>>()

    private lateinit var listBox: LinearLayout
    private lateinit var statusView: TextView

    private val opcodeDir: File
        get() = File(File(Environment.getExternalStorageDirectory(), "cleo"), "CLEO_Builder/opcode")

    private fun enabledPrefKey(m: GameMode) = "opcode_enabled_${m.dataDir}"

    /** 已启用表；null = 未自定义（全部内置） */
    private fun enabledTables(m: GameMode): List<String>? =
        prefs.getString(enabledPrefKey(m), null)?.split("|")?.filter { it.isNotBlank() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_opcode_manager)

        mode = intent.getStringExtra("mode")
            ?.let { runCatching { GameMode.valueOf(it) }.getOrNull() } ?: GameMode.GTASA

        findViewById<TextView>(R.id.txt_title).text = "Opcode 表管理 · ${mode.label}"
        findViewById<TextView>(R.id.txt_close).setOnClickListener { finish() }
        listBox = findViewById(R.id.list_tables)
        statusView = findViewById(R.id.txt_status)

        loadCompiler()
        populate()

        findViewById<Button>(R.id.btn_all).setOnClickListener { setAllChecked(true) }
        findViewById<Button>(R.id.btn_none).setOnClickListener { setAllChecked(false) }
        findViewById<Button>(R.id.btn_reset).setOnClickListener { resetData() }
        findViewById<Button>(R.id.btn_save).setOnClickListener { saveAndReload() }
    }

    /** 以当前用户选择加载表，刷新顶部状态 */
    private fun loadCompiler() {
        val warn = StringBuilder()
        compiler.init(mode, filesDir.resolve("sanny/data").absolutePath, opcodeDir, enabledTables(mode), warn)
        val loaded = compiler.lastLoadedTables()
        statusView.text = "目录：${compiler.lastIniDirPath()}\n" +
                "当前加载 ${loaded.size} 张表 / 共 ${compiler.opcodeCount()} 条 opcode\n" +
                "已加载：${if (loaded.isEmpty()) "（无）" else loaded.joinToString(", ")}\n" +
                (if (warn.isNotEmpty()) "\n校验：$warn\n" else "\n") +
                "勾选要加载的表后点「保存并重载」："
    }

    /** 填充表列表（可勾选） */
    private fun populate() {
        listBox.removeAllViews()
        checks.clear()
        val dir = File(opcodeDir, mode.dataDir)
        val ini = dir.listFiles { f -> f.isFile && f.name.endsWith(".ini") }?.sortedBy { it.name }
        if (ini.isNullOrEmpty()) {
            statusView.text = "opcode 数据尚未解压到\n${dir.absolutePath}\n\n点下方「重置数据」把内置表解压到该目录，之后可浏览/勾选/自定义。"
            return
        }
        val enabled = enabledTables(mode)
        val builtin = mode.iniFiles.toSet()
        val loadedSet = compiler.lastLoadedTables().toSet()
        for (f in ini) {
            val name = f.name
            val cb = CheckBox(this)
            cb.textSize = 14f
            cb.setPadding(dp(16), dp(8), dp(8), dp(8))
            cb.tag = name
            val text = buildItemText(name, builtin, loadedSet, enabled)
            cb.text = text
            cb.isChecked = enabled?.contains(name) ?: true
            cb.setOnCheckedChangeListener { _, _ -> cb.text = buildItemText(name, builtin, loadedSet, enabled) }
            listBox.addView(cb)
            checks.add(name to cb)
        }
    }

    private fun buildItemText(name: String, builtin: Set<String>, loadedSet: Set<String>, enabled: List<String>?): String {
        val tag = if (name in builtin) "[内置]" else "[自定义]"
        val on = if (name in loadedSet) "✓" else "✗"
        return "$tag $on $name"
    }

    private fun setAllChecked(v: Boolean) {
        checks.forEach { it.second.isChecked = v }
    }

    /** 保存勾选 → 重载 */
    private fun saveAndReload() {
        val enabled = checks.filter { it.second.isChecked }.map { it.first }
        if (enabled.isEmpty()) {
            prefs.edit().remove(enabledPrefKey(mode)).apply()
        } else {
            prefs.edit().putString(enabledPrefKey(mode), enabled.joinToString("|")).apply()
        }
        loadCompiler()
        populate()
        Toast.makeText(this, "已保存：当前 opcode ${compiler.opcodeCount()} 条（已加载 ${compiler.lastLoadedTables().size} 张表）", Toast.LENGTH_SHORT).show()
    }

    /** 从内置资源重新解压表数据（同名覆盖，自定义表保留） */
    private fun resetData() {
        try {
            val n = AssetsHelper.extractOpcodeTables(this, opcodeDir)
            Toast.makeText(this, "已重置：解压 $n 个表文件", Toast.LENGTH_SHORT).show()
            loadCompiler()
            populate()
        } catch (e: Exception) {
            Toast.makeText(this, "重置失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
