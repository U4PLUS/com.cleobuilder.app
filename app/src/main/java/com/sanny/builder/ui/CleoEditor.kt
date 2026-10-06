package com.sanny.builder.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.EditText
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 基于系统 EditText 的 CLEO 代码编辑器。
 * 光标/点击/输入由系统处理；高亮/撤销/缩放/补全/行号自行实现。
 *
 * 健壮性策略：构造函数与所有绘制/分析路径都 try-catch 兜底，
 * 任何子功能初始化失败都绝不能让 App 崩溃（Log 留痕）。
 */
class CleoEditor @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : EditText(context, attrs) {

    /** 快照：文本 + 变化前光标位置（撤回后恢复原光标，不跳末尾） */
    private data class Snapshot(val text: String, val selStart: Int, val selEnd: Int)

    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()
    private var restoring = false
    private var hlPosted = false
    private val errorLines = HashSet<Int>()

    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            if (!restoring) {
                undoStack.addLast(Snapshot(s?.toString() ?: "", selectionStart, selectionEnd))
                if (undoStack.size > 80) undoStack.removeFirst()
                redoStack.clear()
            }
        }
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            try {
                errorLines.clear()
                maxLineWidthDirty = true
                post { updateLineNumberPadding() }
                scheduleHighlight()
                maybeCompletion()
            } catch (t: Throwable) {
                Log.w("CleoEditor", "afterTextChanged failed", t)
            }
        }
    }

    private val lineNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF808080.toInt()
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.MONOSPACE
    }
    private var lineNumberWidth = 0
    private val lineRect = Rect()

    private val hlRunnable = Runnable {
        try { applyHighlight() } catch (t: Throwable) {
            Log.w("CleoEditor", "applyHighlight failed", t)
        }
    }

    // 缩放器惰性创建：构造期绝不执行（InflateException 防御）
    private val scaleDetector by lazy {
        ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private var startSize = 0f
            private var startX = 0
            private var startY = 0
            private var focusX = 0f
            private var focusY = 0f

            override fun onScaleBegin(d: ScaleGestureDetector): Boolean {
                try {
                    startSize = textSize
                    startX = scrollX
                    startY = scrollY
                    focusX = d.focusX
                    focusY = d.focusY
                } catch (t: Throwable) { Log.w("CleoEditor", "onScaleBegin failed", t) }
                return true
            }

            override fun onScale(d: ScaleGestureDetector): Boolean {
                try {
                    // scaleFactor 是相对上一帧的增量：用当前 textSize 累积
                    val ns = textSize * d.scaleFactor
                    if (ns in 8f..48f) {
                        setTextSize(TypedValue.COMPLEX_UNIT_PX, ns)
                    }
                } catch (t: Throwable) { Log.w("CleoEditor", "onScale failed", t) }
                return true
            }

            override fun onScaleEnd(d: ScaleGestureDetector) {
                try {
                    if (startSize <= 0f) return
                    val factor = textSize / startSize
                    if (abs(factor - 1f) > 0.02f) {
                        val tx = (startX * factor).toInt()
                        val ty = ((startY + focusY) * factor - focusY).toInt()
                        // 等布局重建完成后再滚动，避免按旧行高被裁剪
                        postDelayed({ scrollTo(tx, ty) }, 60)
                    }
                } catch (t: Throwable) { Log.w("CleoEditor", "onScaleEnd failed", t) }
            }
        })
    }

    init {
        try {
            isSingleLine = false
            gravity = Gravity.TOP or Gravity.START
            setTypeface(Typeface.MONOSPACE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setBackgroundColor(0xFF1E1E1E.toInt())
            setTextColor(0xFFD4D4D4.toInt())
            highlightColor = 0x33264F78
            includeFontPadding = false
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setPadding(dp(24), dp(6), dp(10), dp(6))
            setHorizontallyScrolling(true)
            addTextChangedListener(watcher)
        } catch (t: Throwable) {
            Log.w("CleoEditor", "init failed (safe mode)", t)
        }
    }

    private fun dp(v: Int): Int = (resources.displayMetrics.density * v).toInt()

    var onTextSizePxChanged: ((Float) -> Unit)? = null
    var onWantCompletion: ((prefix: String, replaceStart: Int, replaceEnd: Int) -> Unit)? = null

    private var wrapEnabledInternal = true

    /** 自动换行开关（默认开）：关闭后长行水平滚动 */
    var wrapEnabled: Boolean
        get() = wrapEnabledInternal
        set(v) {
            wrapEnabledInternal = v
            setHorizontallyScrolling(!v)
            maxLineWidthDirty = true
            post { updateLineNumberPadding() }
        }

    // ---------- 高亮 ----------

    fun scheduleHighlight() {
        if (!hlPosted) {
            hlPosted = true
            postDelayed(hlRunnable, 50)
        }
    }

    /** 把语法错误行标红（行号 0 起始，可一次标多个） */
    fun setErrorLines(lines: Collection<Int>) {
        try {
            errorLines.clear()
            errorLines.addAll(lines)
            scheduleHighlight()
        } catch (t: Throwable) { Log.w("CleoEditor", "setErrorLines failed", t) }
    }

    fun clearErrorLines() {
        if (errorLines.isEmpty()) return
        errorLines.clear()
        scheduleHighlight()
    }

    private fun applyHighlight() {
        hlPosted = false
        val s = text ?: return
        if (s.length > CodeHighlighter.MAX_CHARS) return
        val oldF = s.getSpans(0, s.length, ForegroundColorSpan::class.java)
        for (sp in oldF) s.removeSpan(sp)
        val oldB = s.getSpans(0, s.length, BackgroundColorSpan::class.java)
        for (sp in oldB) s.removeSpan(sp)

        val src = s.toString()
        val n = src.length
        var lineIndex = 0
        var i = 0
        while (i <= n) {
            var j = src.indexOf('\n', i)
            if (j < 0) j = n
            val lineText = src.substring(i, j)
            for (t in CodeHighlighter.classify(lineText, emptySet())) {
                val color = kindToArgb(t.kind)
                if (color != 0) {
                    s.setSpan(ForegroundColorSpan(color), i + t.start, i + t.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            if (lineIndex in errorLines && lineText.isNotEmpty()) {
                s.setSpan(BackgroundColorSpan(0x33FF3333), i, j, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            lineIndex++
            i = j + 1
        }
    }

    private fun kindToArgb(kind: CodeHighlighter.Kind): Int = when (kind) {
        CodeHighlighter.Kind.COMMENT -> 0xFF6A9955.toInt()
        CodeHighlighter.Kind.STRING -> 0xFF6A9B60.toInt()
        CodeHighlighter.Kind.LABEL -> 0xFF9080D8.toInt()
        CodeHighlighter.Kind.LVAR -> 0xFF5090B0.toInt()
        CodeHighlighter.Kind.GLOBAL -> 0xFFC89840.toInt()
        CodeHighlighter.Kind.NUMBER -> 0xFF6897BB.toInt()
        CodeHighlighter.Kind.KEYWORD -> 0xFFCC5532.toInt()
        CodeHighlighter.Kind.OPCODE -> 0xFF9B8AE8.toInt()
        CodeHighlighter.Kind.PLAIN -> 0
    }

    // ---------- 行号 ----------

    private fun updateLineNumberPadding() {
        try {
            val count = layout?.lineCount ?: 100
            val digits = count.coerceAtLeast(1).toString().length.coerceAtLeast(2)
            val w = (textSize * 0.55f * digits + dp(8)).toInt()
            if (w != lineNumberWidth) {
                lineNumberWidth = w
                setPadding(lineNumberWidth, paddingTop, paddingRight, paddingBottom)
            }
        } catch (t: Throwable) {
            Log.w("CleoEditor", "updateLineNumberPadding failed", t)
        }
    }

    override fun onDraw(canvas: Canvas) {
        try {
            val l = layout
            val lh = lineHeight
            if (l != null && lh > 0 && l.lineCount > 0) {
                // 与行强绑定：getLineBounds 直接返回每行在屏幕上的真实位置
                val topPad = getTotalPaddingTop()
                val first = ((scrollY - topPad) / lh - 1).toInt().coerceIn(0, l.lineCount - 1)
                val last = ((scrollY - topPad + height) / lh + 1).toInt().coerceIn(first, l.lineCount - 1)
                lineNumberPaint.textSize = textSize
                val fm = lineNumberPaint.fontMetricsInt
                val pad = paddingLeft - dp(2)
                val src = text?.toString() ?: ""
                for (i in first..last) {
                    getLineBounds(i, lineRect)
                    // 只给"逻辑行行首"（前一个字符是换行符/行 0）编号，
                    // 自动换行的折行不重复编号
                    val off = l.getLineStart(i)
                    val isStart = off == 0 || src[off - 1] == '\n'
                    if (!isStart) continue
                    val num = logicalLineNumber(off, src)
                    val baseline = (lineRect.top - fm.ascent).toFloat()
                    canvas.drawText(num.toString(), pad.toFloat(), baseline, lineNumberPaint)
                }
            }
        } catch (t: Throwable) {
            Log.w("CleoEditor", "onDraw line number failed", t)
        }
        super.onDraw(canvas)
    }

    override fun setTextSize(unit: Int, size: Float) {
        try {
            super.setTextSize(unit, size)
            maxLineWidthDirty = true
            lineNumberPaint.textSize = textSize
            updateLineNumberPadding()
            onTextSizePxChanged?.invoke(textSize)
        } catch (t: Throwable) {
            Log.w("CleoEditor", "setTextSize failed", t)
        }
    }

    // ---------- 撤销/重做 ----------

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun undo() {
        try {
            if (undoStack.isEmpty()) return
            restoring = true
            redoStack.addLast(Snapshot(text?.toString() ?: "", selectionStart, selectionEnd))
            val prev = undoStack.removeLast()
            setText(prev.text)
            // 恢复变化前光标位置（越界则夹紧），不跳末尾
            setSelection(
                prev.selStart.coerceIn(0, prev.text.length),
                prev.selEnd.coerceIn(0, prev.text.length)
            )
        } catch (t: Throwable) { Log.w("CleoEditor", "undo failed", t) } finally {
            restoring = false
        }
    }

    fun redo() {
        try {
            if (redoStack.isEmpty()) return
            restoring = true
            undoStack.addLast(Snapshot(text?.toString() ?: "", selectionStart, selectionEnd))
            val next = redoStack.removeLast()
            setText(next.text)
            setSelection(
                next.selStart.coerceIn(0, next.text.length),
                next.selEnd.coerceIn(0, next.text.length)
            )
        } catch (t: Throwable) { Log.w("CleoEditor", "redo failed", t) } finally {
            restoring = false
        }
    }

    // ---------- 水平滚动范围（固定最长行） ----------

    private var maxLineWidth = 0
    private var maxLineWidthDirty = true

    /** 重算全篇最长行宽（懒执行，dirty 才算） */
    private fun updateMaxLineWidthIfNeeded() {
        try {
            val l = layout
            if (l != null && l.lineCount > 0 && maxLineWidthDirty) {
                var max = 0
                for (i in 0 until l.lineCount) {
                    val w = l.getLineWidth(i)
                    if (w > max) max = w.toInt()
                }
                maxLineWidth = max
                maxLineWidthDirty = false
            }
        } catch (t: Throwable) {
            Log.w("CleoEditor", "updateMaxLineWidthIfNeeded failed", t)
        }
    }

    /** 固定水平滚动上界 = 全篇最长行宽 - 视口宽（+内边距） */
    private fun maxHorizontalScroll(): Int {
        if (maxLineWidth <= 0) return 0
        return (maxLineWidth + paddingLeft + paddingRight - width).coerceAtLeast(0)
    }

    /**
     * 水平滚动宽度固定为全篇最长行：不随"当前可见行"变化。
     * 否则下滑后长行消失、只剩短行时滚动范围收缩，画面会突然横移。
     */
    override fun computeHorizontalScrollRange(): Int {
        try {
            updateMaxLineWidthIfNeeded()
            return maxLineWidth + paddingLeft + paddingRight
        } catch (t: Throwable) {
            return super.computeHorizontalScrollRange()
        }
    }

    /**
     * 拦截系统对 scrollX 的压缩：无论内部走哪条路径（滚动条拖动、
     * 布局重建、光标跟随），水平位置都被锁死在全篇最长行范围内，
     * 不会因为"当前可见行变短"而被压回右侧。
     */
    override fun scrollTo(x: Int, y: Int) {
        try {
            updateMaxLineWidthIfNeeded()
            val nx = if (maxLineWidth > 0) x.coerceIn(0, maxHorizontalScroll()) else x
            super.scrollTo(nx, y)
        } catch (t: Throwable) {
            super.scrollTo(x, y)
        }
    }

    /** 计算 offset 处的逻辑行号（1 起始）：数前面有多少个换行符 */
    private fun logicalLineNumber(offset: Int, src: String): Int {
        var n = 1
        var i = 0
        while (i < offset) {
            val idx = src.indexOf('\n', i)
            if (idx < 0 || idx >= offset) break
            n++
            i = idx + 1
        }
        return n
    }

    // ---------- 触摸/缩放 ----------

    override fun onTouchEvent(e: MotionEvent): Boolean {
        return try {
            if (e.pointerCount > 1) {
                scaleDetector.onTouchEvent(e)
                true
            } else {
                super.onTouchEvent(e)
            }
        } catch (t: Throwable) {
            Log.w("CleoEditor", "onTouchEvent failed", t)
            super.onTouchEvent(e)
        }
    }

    // ---------- 补全 ----------

    private fun maybeCompletion() {
        try {
            val sel = selectionStart
            if (sel <= 0) return
            val src = text?.toString() ?: return
            if (sel > src.length) return
            var st = sel
            while (st > 0) {
                val c = src[st - 1]
                if (c.isLetterOrDigit() || c == '_' || c == '@' || c == '$') st-- else break
            }
            if (sel - st in 1..24) {
                onWantCompletion?.invoke(src.substring(st, sel), st, sel)
            }
        } catch (t: Throwable) {
            Log.w("CleoEditor", "maybeCompletion failed", t)
        }
    }

    fun applyCompletion(word: String, replaceStart: Int, replaceEnd: Int) {
        try {
            restoring = true
            setText((text?.toString() ?: "").replaceRange(replaceStart, replaceEnd, word))
            setSelection(replaceStart + word.length)
            scheduleHighlight()
        } catch (t: Throwable) { Log.w("CleoEditor", "applyCompletion failed", t) } finally {
            restoring = false
        }
    }

    // ---------- 跳转 ----------

    fun goToLine(line: Int) {
        try {
            val src = text?.toString() ?: return
            if (src.isEmpty()) return
            var off = 0
            var li = 0
            while (li < line) {
                val nl = src.indexOf('\n', off)
                if (nl < 0) return
                off = nl + 1
                li++
            }
            requestFocus()
            setSelection(min(off, src.length))
            post {
                try {
                    val l = layout ?: return@post
                    if (l.lineCount <= 0) return@post
                    val top = l.getLineTop(min(line, l.lineCount - 1))
                    scrollTo(0, max(0, top - height / 3))
                } catch (t: Throwable) { Log.w("CleoEditor", "goToLine scroll failed", t) }
            }
        } catch (t: Throwable) {
            Log.w("CleoEditor", "goToLine failed", t)
        }
    }
}
