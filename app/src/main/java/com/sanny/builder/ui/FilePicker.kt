package com.sanny.builder.ui

import android.app.AlertDialog
import android.content.Context
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.view.View
import java.io.File
import java.util.Locale

/**
 * 内建文件浏览对话框（不依赖系统文件选择器）。
 * saveMode=false：打开模式，点击 .sc/.txt 文件直接回调 onOpen。
 * saveMode=true ：保存模式，底部输入文件名，点"保存"回调 onSave(目录, 文件名)。
 */
class FilePicker(
    private val context: Context,
    private val initialDir: File,
    private val saveMode: Boolean,
    private val onOpen: (File) -> Unit,
    private val onSave: (File, String) -> Unit
) {
    private var currentDir: File = initDir(initialDir)

    private companion object {
        /** 回退逻辑：目录不存在/不可读时逐级向上找最近可读目录 */
        private fun initDir(start: File): File {
            var cur = start
            val seen = ArrayList<File>()
            while (!cur.isDirectory) {
                seen.add(cur)
                val p = cur.parentFile ?: break
                cur = p
            }
            // 从最近可读的祖先开始
            while (!cur.canRead()) {
                val p = cur.parentFile ?: break
                cur = p
            }
            return cur
        }
    }
    private var dialog: AlertDialog? = null
    private var nameInput: EditText? = null
    private lateinit var list: ListView
    private lateinit var adapter: FpAdapter

    fun show() {
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        // 关键：对话框容器 wrap_content 时 weight 布局会塌缩，必须给固定高度
        val maxH = (context.resources.displayMetrics.heightPixels * 0.65f).toInt()
        root.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxH)

        val bar = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        bar.addView(Button(context).apply {
            text = "\u2191 \u4e0a\u7ea7"
            setOnClickListener { goUp() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (saveMode) {
            bar.addView(Button(context).apply {
                text = "\u4fdd\u5b58"
                setOnClickListener { onSaveClick() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(bar, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        if (saveMode) {
            nameInput = EditText(context).apply { hint = "\u6587\u4ef6\u540d\uff08\u81ea\u52a8\u8865 .sc\uff09" }
            root.addView(nameInput, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        list = ListView(context)
        adapter = FpAdapter()
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            val f = adapter.files[pos]
            if (f.isDirectory) { currentDir = f; refresh() }
            else if (saveMode) nameInput?.setText(f.nameWithoutExtension)
            else if (isOpenable(f)) { dialog?.dismiss(); onOpen(f) }
            // 打开模式下非文本文件：不响应
        }
        root.addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        dialog = AlertDialog.Builder(context).setView(root).setNegativeButton("\u53d6\u6d88", null).create()
        dialog?.show()
        refresh()
    }

    private fun isOpenable(f: File): Boolean {
        if (f.isDirectory) return true
        val low = f.name.lowercase(Locale.ROOT)
        return low.endsWith(".txt") || low.endsWith(".sc") || low.endsWith(".sb")
    }

    private fun goUp() {
        val parent = currentDir.parentFile
        if (parent != null) { currentDir = parent; refresh() }
    }

    private fun onSaveClick() {
        var name = nameInput?.text?.toString()?.trim() ?: ""
        name = name.filterNot { it == '/' || it == '\\' || it == ':' || it == '*' || it == '?' || it == '<' || it == '>' || it == '|' || it == '\"' }
        if (name.isEmpty()) { nameInput?.error = "\u8bf7\u8f93\u5165\u6587\u4ef6\u540d"; return }
        dialog?.dismiss()
        onSave(currentDir, if (name.endsWith(".txt", true)) name else "$name.txt")
    }

    private fun refresh() {
        val children = currentDir.listFiles()
        if (children == null) {
            dialog?.setTitle("\u26a0 \u65e0\u6cd5\u8bfb\u53d6: " + currentDir.absolutePath + " (\u65e0\u6743\u9650/IO\u9519\u8bef)")
            adapter.files = emptyList()
            adapter.notifyDataSetChanged()
            return
        }
        if (children.isEmpty()) {
            dialog?.setTitle("\u26a0 " + currentDir.absolutePath + "\uff08\u7a7a\u76ee\u5f55\uff09")
        } else {
            dialog?.setTitle(currentDir.absolutePath)
        }
        val files = ArrayList<File>(children.size)
        for (f in children) {
            if (f.isDirectory) files.add(f) else files.add(f) // 全部列出
        }
        files.sortWith(Comparator { a, b ->
            when {
                a.isDirectory && !b.isDirectory -> -1
                !a.isDirectory && b.isDirectory -> 1
                else -> a.name.lowercase(Locale.ROOT).compareTo(b.name.lowercase(Locale.ROOT))
            }
        })
        adapter.files = files
        adapter.notifyDataSetChanged()
    }

    private inner class FpAdapter : BaseAdapter() {
        var files: List<File> = emptyList()
        override fun getCount() = files.size
        override fun getItem(p: Int) = files[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, convert: View?, parent: ViewGroup): View {
            val tv = (convert as? TextView) ?: TextView(context).apply {
                setPadding(32, 28, 16, 28)
                textSize = 15f
            }
            val f = files[p]
            tv.text = if (f.isDirectory) "\u25b8 ${f.name}/" else "\u2022 ${f.name}"
            if (f.isDirectory || isOpenable(f)) {
                tv.setTextColor(0xFFE0E0E0.toInt())
            } else {
                tv.setTextColor(0xFF606060.toInt()) // 不可打开：灰色
                tv.alpha = 0.55f
            }
            return tv
        }
    }
}
