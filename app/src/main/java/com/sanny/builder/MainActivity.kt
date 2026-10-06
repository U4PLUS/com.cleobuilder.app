package com.sanny.builder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sanny.builder.compiler.CompilerService
import com.sanny.builder.compiler.GameMode
import com.sanny.builder.core.SannyService
import com.sanny.builder.ui.FilePicker
import com.sanny.builder.ui.CleoEditor
import android.widget.ArrayAdapter
import android.widget.ListPopupWindow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var editor: CleoEditor
    private lateinit var sanny: SannyService

    private val compiler = CompilerService()

    private val dataRoot: String
        get() = filesDir.resolve("sanny/data").absolutePath

    private var currentMode: GameMode = GameMode.GTASA
    private var currentFile: File? = null
    private var pendingRestoreText: String? = null
    private val log = StringBuilder()

    private val prefs by lazy { getSharedPreferences("cleo_builder", MODE_PRIVATE) }
    private val cleoBuilderDir: File
        get() = File(File(Environment.getExternalStorageDirectory(), "cleo"), "CLEO_Builder")

    /** 底部标点栏符号 */
    private val SYMBOLS = arrayOf(
        "0@", "\$", "@", ":", ";", ",", "(", ")", "[", "]", "{", "}", "=", "+",
        "-", "*", "/", "%", "<", ">", "\"", "'", "#", "&", "|", "!", "?", "."
    )

    private val NEW_SCRIPT_TEMPLATE = "{\$CLEO .cs}\n// 新建 CLEO 脚本\n\n0@ = 0\n\n:MAIN\nwait 0\n0@ += 1\nif 0@ > 100\nthen\n    0@ = 0\nend\ngoto @MAIN\n"

    override fun onCreate(savedInstanceState: Bundle?) {
        // 固定深色模式（代码编辑器场景，浅色无意义）
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)

            editor = findViewById(R.id.editor)
            // 恢复上次字号（双指缩放记忆）
            val savedPx = prefs.getFloat("text_px", 0f)
            if (savedPx in 8f..48f) {
                editor.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, savedPx)
            }
            editor.onTextSizePxChanged = { px -> prefs.edit().putFloat("text_px", px).apply() }
            // 代码补全联想回调（输入时触发）
            editor.onWantCompletion = { prefix, rs, re -> showCompletionPopup(prefix, rs, re) }
            // 自动换行（默认开，记忆用户选择）
            editor.wrapEnabled = prefs.getBoolean("wrap", true)
            buildSymbolBar()

            val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.activity_toolbar)
            setSupportActionBar(toolbar)

            sanny = SannyService(this)
            sanny.init()

            // 恢复上次模式
            val lastMode = prefs.getString("last_mode", null)?.let { runCatching { GameMode.valueOf(it) }.getOrNull() }
            if (lastMode != null) {
                switchMode(lastMode, quiet = true)
            } else {
                compiler.init(GameMode.GTASA, dataRoot)
                currentMode = GameMode.GTASA
            }

            // 文本恢复（主题切换 recreate 时带回）优先于文件恢复
            pendingRestoreText?.let { t ->
                editor.setText(t); pendingRestoreText = null
                editor.setSelection(0, 0)
                editor.postDelayed({ editor.requestLayout() }, 120)
            } ?: run {
                // 默认打开上次打开的文件；没有则不打开
                val lastFile = prefs.getString("last_file", null)?.let { File(it) }
                if (lastFile != null && lastFile.isFile) {
                    loadFile(lastFile)
                } else {
                    editor.setText("")
                    editor.setSelection(0, 0)
                    appendLog("未找到上次文件。点 ⠇ 打开源码，或 ⠇ 新建脚本")
                }
            }
            updateTitle()
            appendLog("CLEO Builder ready. ▶ 编译 -> cleo/CLEO_Builder/")
        } catch (t: Throwable) {
            try {
                // 崩溃详情落盘，方便取回分析
                try {
                    val dir = File(File(Environment.getExternalStorageDirectory(), "cleo"), "CLEO_Builder")
                    dir.mkdirs()
                    File(dir, "crash.log").writeText(Log.getStackTraceString(t))
                } catch (_: Throwable) { }
                val tv = android.widget.TextView(this)
                tv.text = "startup failed:\n" + Log.getStackTraceString(t) +
                        "\n\n(详情已写 cleo/CLEO_Builder/crash.log)"
                tv.setTextColor(Color.RED)
                tv.textSize = 12f
                tv.setPadding(32, 32, 32, 32)
                setContentView(tv)
            } catch (_: Throwable) {
                throw t
            }
        }
    }

    override fun onStart() {
        super.onStart()
        warmLanguageService()
        ensureStoragePermission()
    }

    // ---------- 底部标点栏 ----------

    private fun buildSymbolBar() {
        val grid = findViewById<android.widget.GridLayout>(R.id.symbol_bar)
        grid.removeAllViews()
        val cols = 14
        val rowH = (resources.displayMetrics.density * 30).toInt()
        for ((i, sym) in SYMBOLS.withIndex()) {
            val b = TextView(this)
            b.text = sym
            b.textSize = 13f
            b.gravity = android.view.Gravity.CENTER
            b.setTextColor(0xFFDCDCDC.toInt())
            b.setBackgroundColor(0xFF323232.toInt())
            b.setPadding(0, 0, 0, 0)
            val lp = android.widget.GridLayout.LayoutParams()
            lp.width = 0
            lp.height = rowH
            lp.columnSpec = android.widget.GridLayout.spec(i % cols, 1f)
            lp.rowSpec = android.widget.GridLayout.spec(i / cols)
            lp.setMargins(2, 2, 2, 2)
            b.layoutParams = lp
            b.setOnClickListener { insertSymbol(sym) }
            grid.addView(b)
        }
    }

    /** 在光标处插入符号，光标移到其后 */
    private fun insertSymbol(sym: String) {
        val sel = editor.selectionStart
        editor.text.insert(sel, sym)
        editor.setSelection(sel + sym.length)
        editor.requestFocus()
    }

    // ---------- 关于 ----------

    private fun showAboutDialog() {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) { "1.0.0" }
        val msg = """
            CLEO Builder — Android 上的 GTA CLEO 脚本编辑器与编译器
            版本 $version

            功能：语法高亮 / 行号 / 代码补全 / .cs .csi .csa 编译 / 多游戏模式

            组件与致谢：
            · sora-editor（Apache-2.0）
            · JNA（LGPL-2.1+exception）
            · AndroidX / Material Components（Apache-2.0）
            · opcode 数据：GTA Modding Community Opcode Database
            · 语言服务核心 libcore.so：基于 Sanny Builder 官方 core 编译，致谢 Seemann
            · CLEO 格式规范：cleo.li

            开源主页：
            https://github.com/U4PLUS/com.cleobuilder.app/
        """.trimIndent()
        MaterialAlertDialogBuilder(this)
            .setTitle("关于 CLEO Builder")
            .setMessage(msg)
            .setPositiveButton("打开 GitHub") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/U4PLUS/com.cleobuilder.app/")))
                } catch (e: Exception) {
                    appendLog("无法打开浏览器: ${e.message}")
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    // ---------- 代码补全 ----------

    private val completionWords = listOf(
        "wait", "if", "then", "else", "end", "goto", "gosub", "return", "jump",
        "while", "endwhile", "repeat", "until", "for", "endfor", "var", "endvar",
        "const", "endconst", "thread", "endthread", "script_name", "def", "enddef",
        "jf", "and", "or", "not", "true", "false", "if0", "while0", "until0"
    )
    private var completionPopup: ListPopupWindow? = null

    private fun showCompletionPopup(prefix: String, replaceStart: Int, replaceEnd: Int) {
        val matches = completionWords.filter { it.startsWith(prefix, ignoreCase = true) && it != prefix }
        completionPopup?.dismiss()
        if (matches.isEmpty()) return
        val popup = ListPopupWindow(this)
        popup.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, matches))
        popup.anchorView = editor
        popup.width = (editor.width * 0.5f).toInt().coerceIn(300, 800)
        popup.setOnItemClickListener { _, _, pos, _ ->
            editor.applyCompletion(matches[pos], replaceStart, replaceEnd)
            popup.dismiss()
        }
        popup.show()
        completionPopup = popup
    }

    /** 编译失败时把错误行标红 */
    private fun applyErrorDiagnostics(errText: String, source: String) {
        val m = Regex("第 (\\d+) 行").find(errText) ?: return
        val lineNo = m.groupValues[1].toIntOrNull() ?: return
        val count = source.lines().size
        val lines = Regex("第 (\\d+) 行").findAll(errText)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .filter { it in 1..count }
            .map { it - 1 }
            .toSet()
        editor.setErrorLines(lines)
        // 自动跳到第一个错误行，方便就地修改
        val first = lines.minOrNull()
        if (first != null) editor.goToLine(first)
        appendLog("已标红 ${lines.size} 个错误行，定位到第 ${first?.plus(1)} 行")
    }

    // ---------- 菜单 ----------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        // 动态填充全部游戏模式
        val modeSub = menu.findItem(R.id.action_mode)?.subMenu
        modeSub?.clear()
        GameMode.values().forEach { m ->
            modeSub?.add(12345, m.ordinal, 0, m.label)?.apply {
                isCheckable = true
                isChecked = (m == currentMode)
            }
        }
        menu.findItem(R.id.action_wrap)?.isChecked = editor.wrapEnabled
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when {
            item.itemId == R.id.action_run -> { runCompile(); true }
            item.itemId == R.id.text_undo -> { editor.undo(); true }
            item.itemId == R.id.text_redo -> { editor.redo(); true }
            item.itemId == R.id.action_open -> { openBuiltin(); true }
            item.itemId == R.id.action_save -> { saveSource(); true }
            item.itemId == R.id.action_new -> { newFileDialog(); true }
            item.itemId == R.id.action_output -> { showOutputDialog(); true }
            item.itemId == R.id.action_about -> { showAboutDialog(); true }
            item.itemId == R.id.action_wrap -> {
                val on = !editor.wrapEnabled
                editor.wrapEnabled = on
                item.isChecked = on
                prefs.edit().putBoolean("wrap", on).apply()
                appendLog(if (on) "自动换行: 开" else "自动换行: 关（长行水平滚动）")
                true
            }
            item.groupId == 12345 -> { switchMode(GameMode.values()[item.itemId]); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ---------- 主题与配色 ----------

    // ---------- 语言服务 ----------

    private var lspWarming = false
    private fun warmLanguageService() {
        if (lspWarming) return
        lspWarming = true
        Thread {
            try {
                var attempts = 0
                while (!sanny.isReady() && attempts < 30) { Thread.sleep(300); attempts++ }
                if (!sanny.isReady()) { lspWarming = false; return@Thread }
                sanny.connect()
                sanny.notifyTextChanged(editor.text.toString())
                Thread.sleep(1200)
            } catch (e: Exception) {
                Log.e("MainActivity", "warm language service failed", e)
            } finally {
                lspWarming = false
            }
        }.start()
    }

    private fun switchMode(mode: GameMode, quiet: Boolean = false) {
        currentMode = mode
        sanny.switchMode(mode)
        warmLanguageService()
        compiler.init(mode, dataRoot)
        prefs.edit().putString("last_mode", mode.name).apply()
        updateTitle()
        if (!quiet) appendLog("模式: ${mode.label}  (opcode 表 ${compiler.opcodeCount()} 条)")
        invalidateOptionsMenu()
    }

    // ---------- 文件 ----------

    private fun updateTitle() {
        supportActionBar?.title = currentFile?.name ?: "CLEO Builder"
        supportActionBar?.subtitle = currentMode.label
    }

    /** 打开一个文本源码文件 */
    private fun loadFile(f: File) {
        try {
            val bytes = f.readBytes()
            if (isBinary(bytes)) {
                appendLog("${f.name} 是二进制文件，只能打开 .sc/.txt 文本源码")
                return
            }
            currentFile = f
            // 净化行尾：Windows 文件每行末尾的 \r 是不可见字符，
            // 会导致光标右偏、删除错位（删到\"看不见的东西\"）
            val clean = String(bytes, Charsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', ' ')
            editor.setText(clean)
            editor.setSelection(0, 0)
            // 编辑后重新着色
            editor.postDelayed({ editor.requestLayout() }, 120)
            prefs.edit().putString("last_file", f.absolutePath)
                .putString("last_dir", f.parent).apply()
            updateTitle()
            appendLog("已打开 ${f.absolutePath}")
        } catch (e: Exception) {
            appendLog("打开失败: ${e.message}")
        }
    }

    private fun newFileDialog() {
        val input = EditText(this).apply {
            hint = "文件名"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("在 cleo/CLEO_Builder/ 新建脚本")
            .setView(input)
            .setPositiveButton("创建") { _, _ ->
                var n = input.text.toString().trim()
                n = n.filterNot { it == '/' || it == '\\' || it == ':' || it == '*' || it == '?' || it == '<' || it == '>' || it == '|' || it == '"' }
                if (n.isEmpty()) { appendLog("文件名不能为空"); return@setPositiveButton }
                if (!n.endsWith(".txt", true)) n += ".txt"
                val dir = cleoBuilderDir
                try {
                    dir.mkdirs()
                    val f = File(dir, n)
                    f.writeText(NEW_SCRIPT_TEMPLATE) // 同名覆盖
                    loadFile(f)
                } catch (e: Exception) {
                    appendLog("新建失败: ${e.message}（请检查存储权限）")
                    requestStoragePermission()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 保存源码：当前工作文件直接覆盖；无工作文件则内建对话框另存 */
    private fun saveSource() {
        val f = currentFile
        if (f != null && f.parentFile == cleoBuilderDir && f.name.endsWith(".txt", true)) {
            try {
                f.writeText(editor.text.toString())
                appendLog("已保存 ${f.absolutePath}")
            } catch (e: Exception) {
                appendLog("保存失败: ${e.message}")
                requestStoragePermission()
            }
        } else {
            saveAsBuiltin()
        }
    }

    private fun saveAsBuiltin() {
        val start = Environment.getExternalStorageDirectory()
        FilePicker(this, start, saveMode = true, onOpen = {}, onSave = { dir, name ->
            try {
                val f = File(dir, name)
                f.writeText(editor.text.toString())
                currentFile = f
                prefs.edit().putString("last_file", f.absolutePath)
                    .putString("last_dir", dir.absolutePath).apply()
                updateTitle()
                appendLog("已保存 ${f.absolutePath}")
            } catch (e: Exception) {
                appendLog("保存失败: ${e.message}")
                requestStoragePermission()
            }
        }).show()
    }

    private fun openBuiltin() {
        val start = Environment.getExternalStorageDirectory()
        runCatching {
            val kids = start.listFiles()
            appendLog("\u6253\u5f00\u9009\u62e9\u5668: ${start.absolutePath}\n  canRead=${start.canRead()}  \u5b50\u9879=${kids?.size?.toString() ?: "null"}")
        }
        FilePicker(this, start, saveMode = false, onOpen = { loadFile(it) }, onSave = { _, _ -> }).show()
    }

    // ---------- ▶ 编译（Run）→ cleo/CLEO_Builder/ ----------

    private fun runCompile() {
        val text = editor.text.toString()
        if (text.isBlank()) { appendLog("编辑器为空，请先输入或打开脚本"); return }
        val err = StringBuilder()
        val asCsi = CompilerService.wantsCsi(text)
        val bytes: ByteArray? = if (asCsi) {
            val main = compiler.compileMain(text, err)
            if (main == null) null else compiler.packCsi(main, CompilerService.scriptNameOf(text), text)
        } else {
            compiler.compileToCs(text, err)
        }
        if (bytes == null || err.isNotEmpty()) {
            appendLog("编译失败:\n$err")
            applyErrorDiagnostics(err.toString(), text)
            return
        }

        val dir = cleoBuilderDir
        if (!dir.exists()) dir.mkdirs()
        if (!dir.isDirectory || !dir.canWrite()) {
            appendLog("无法写入 ${dir.absolutePath}\n请到 设置→应用→CLEO Builder→所有文件访问 授予")
            requestStoragePermission()
            return
        }

        // 文件名：源码 script_name 优先，其次当前文件名，最后 script
        val scriptName = CompilerService.scriptNameOf(text)
        val srcName = when {
            scriptName.isNotBlank() && scriptName != "SCRIPT" -> scriptName
            else -> currentFile?.name?.substringBeforeLast('.') ?: "script"
        }
        // 产物扩展名：{$CLEO .csi}->.csi；{$CLEO .csa}->.csa；其他->.cs
        val outExt = when {
            asCsi -> ".csi"
            CompilerService.wantsCsa(text) -> ".csa"
            else -> ".cs"
        }

        // 编译成功，清除旧错误标红
        editor.clearErrorLines()
        try {
            // 同步保存源码（同名覆盖，.txt）
            val srcFile = File(dir, "$srcName.txt")
            srcFile.writeText(text)
            currentFile = srcFile
            prefs.edit().putString("last_file", srcFile.absolutePath)
                .putString("last_dir", dir.absolutePath).apply()
            // 编译产物（同名覆盖）
            val outFile = File(dir, "$srcName$outExt")
            outFile.writeBytes(bytes)
            updateTitle()
            appendLog("✓ 编译 ${outFile.name} (${bytes.size} B) + 源码已同步 ${srcFile.name}")
        } catch (e: Exception) {
            appendLog("写入失败: ${e.message}")
            requestStoragePermission()
        }
    }

    // ---------- 存储权限 ----------

    /** 每次打开检测：无存储权限则直接请求（targetSdk 29 下 Android 5~11 的 WRITE 权限都有效） */
    private fun ensureStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                appendLog("存储权限已授予，请重新点 ▶ 编译")
            } else {
                appendLog("存储权限被拒绝，无法写入 cleo/CLEO_Builder")
            }
        }
    }

    // ---------- 输出 ----------

    private fun appendLog(msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        log.append("[$ts] ").append(msg).append('\n')
        Snackbar.make(findViewById(R.id.activity_toolbar), msg.lines().lastOrNull() ?: "", Snackbar.LENGTH_LONG).show()
    }

    private fun showOutputDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("输出日志")
            .setMessage(log.toString().ifEmpty { "(空)" })
            .setPositiveButton("关闭", null)
            .setNegativeButton("清空") { _, _ -> log.setLength(0) }
            .show()
    }

    private fun isBinary(bytes: ByteArray): Boolean {
        val n = minOf(bytes.size, 4096)
        var nulls = 0
        for (i in 0 until n) if (bytes[i] == 0.toByte()) nulls++
        return nulls > n / 8
    }
}
