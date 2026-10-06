package com.sanny.builder.ui

import android.os.Bundle
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.analysis.SimpleAnalyzeManager
import io.github.rosemoe.sora.lang.completion.CompletionItem
import io.github.rosemoe.sora.lang.completion.CompletionItemKind
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import io.github.rosemoe.sora.lang.format.Formatter
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler
import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.Spans
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.SpanFactory
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.SymbolPairMatch
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/** 自定义色表 id（对应 MT 管理器 CLEO Script 配色） */
object ScmColors {
    const val OPCODE = 0x6001
    const val KEYWORD = 0x6002
    const val NUMBER = 0x6003
}

/**
 * 编辑器配色方案：重写 getColor 随主题（深/浅）返回配色。
 * 正文/行号等黑白随主题反色（深色=浅字深底，浅色=深字浅底），
 * 高亮色取 MT 管理器 CLEO Script 配色对应深浅列。
 */
class ScmScheme(private val dark: Boolean) : EditorColorScheme() {
    override fun getColor(colorId: Int): Int = when (colorId) {
        EditorColorScheme.WHOLE_BACKGROUND -> if (dark) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt()
        EditorColorScheme.TEXT_NORMAL -> if (dark) 0xFFD4D4D4.toInt() else 0xFF1E1E1E.toInt()
        EditorColorScheme.CURRENT_LINE -> if (dark) 0xFF2A2A2A.toInt() else 0xFFEEEEEE.toInt()
        EditorColorScheme.LINE_NUMBER -> if (dark) 0xFF858585.toInt() else 0xFF777777.toInt()
        EditorColorScheme.LINE_NUMBER_CURRENT -> if (dark) 0xFFC8C8C8.toInt() else 0xFF1E1E1E.toInt()
        EditorColorScheme.LINE_NUMBER_BACKGROUND -> if (dark) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt()
        EditorColorScheme.TEXT_SELECTED -> if (dark) 0xFF264F78.toInt() else 0xFFBBDEFB.toInt()
        EditorColorScheme.UNDERLINE -> if (dark) 0xFF2A2A2A.toInt() else 0xFFEEEEEE.toInt()
        EditorColorScheme.COMMENT -> 0xFF6A9955.toInt()
        EditorColorScheme.LITERAL -> if (dark) 0xFF6A9B60.toInt() else 0xFF167C17.toInt()
        EditorColorScheme.FUNCTION_NAME -> if (dark) 0xFF9080D8.toInt() else 0xFF6B4FA8.toInt()
        EditorColorScheme.IDENTIFIER_VAR -> if (dark) 0xFF5090B0.toInt() else 0xFF0070A2.toInt()
        EditorColorScheme.IDENTIFIER_NAME -> if (dark) 0xFFC89840.toInt() else 0xFF7A4400.toInt()
        EditorColorScheme.OPERATOR -> if (dark) 0xFFD4D4D4.toInt() else 0xFF1E1E1E.toInt()
        EditorColorScheme.COMPLETION_WND_BACKGROUND -> if (dark) 0xFF252526.toInt() else 0xFFFFFFFF.toInt()
        EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY -> if (dark) 0xFFD4D4D4.toInt() else 0xFF1E1E1E.toInt()
        EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY -> if (dark) 0xFF9A9A9A.toInt() else 0xFF6E6E6E.toInt()
        EditorColorScheme.COMPLETION_WND_ITEM_CURRENT -> if (dark) 0xFF37373D.toInt() else 0xFFE3E3E3.toInt()
        EditorColorScheme.COMPLETION_WND_CORNER -> if (dark) 0xFF252526.toInt() else 0xFFFFFFFF.toInt()
        ScmColors.OPCODE -> if (dark) 0xFF9B8AE8.toInt() else 0xFF6848A8.toInt()
        ScmColors.KEYWORD -> if (dark) 0xFFCC5532.toInt() else 0xFF0033B3.toInt()
        ScmColors.NUMBER -> if (dark) 0xFF6897BB.toInt() else 0xFF1750EB.toInt()
        else -> super.getColor(colorId)
    }
}

/** SCM/CLEO 语法高亮语言（sora-editor），复用 CodeHighlighter 词法分类 */
class ScmLanguage : Language {

    // 必须缓存单例：setEditorLanguage 绑定 receiver 与 setText 触发 reset
    // 若每次 new，后者的分析结果没有 receiver 接收，高亮永远不显示
    private val analyzeManager = ScmSimpleAnalyzeManager()

    override fun getAnalyzeManager(): AnalyzeManager = analyzeManager

    override fun getInterruptionLevel(): Int = Language.INTERRUPTION_LEVEL_NONE

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle
    ) {
        // 光标前的单词前缀（支持字母/数字/_/@/$）
        val line = content.getLine(position.line)
        var start = position.column
        val lineStr = line.toString()
        while (start > 0) {
            val c = lineStr[start - 1]
            if (c.isLetterOrDigit() || c == '_' || c == '@' || c == '\$') start-- else break
        }
        val len = position.column - start
        if (len < 1) return
        val prefix = lineStr.substring(start, position.column)
        val items = ArrayList<CompletionItem>()
        for (w in CLEO_COMPLETIONS) {
            if (w.startsWith(prefix, ignoreCase = true) && w != prefix) {
                items.add(SimpleCompletionItem(w, len, w).apply {
                    desc("CLEO/SCM")
                    kind = CompletionItemKind.Identifier
                })
            }
        }
        if (items.isNotEmpty()) {
            publisher.addItems(items)
            publisher.updateList()
        }
    }

    override fun getIndentAdvance(content: ContentReference, line: Int, column: Int): Int = 0

    override fun useTab(): Boolean = true

    override fun getFormatter(): Formatter = EmptyLanguage.EmptyFormatter.INSTANCE

    override fun getSymbolPairs(): SymbolPairMatch = SymbolPairMatch()

    override fun getNewlineHandlers(): Array<NewlineHandler> = emptyArray()

    override fun destroy() {}
}

/** 补全词库：CLEO/SCM 常用关键字与指令 */
private val CLEO_COMPLETIONS = listOf(
    "wait", "if", "then", "else", "end", "goto", "gosub", "return", "jump",
    "while", "endwhile", "repeat", "until", "for", "endfor",
    "var", "endvar", "const", "endconst", "thread", "endthread",
    "script_name", "def", "enddef", "jf", "else_jump",
    "and", "or", "not", "true", "false", "if0", "0@"
)

/** SCM 无跨行语法状态 */
private class ScmState

/** token 分类 -> 颜色 id */
private fun kindToColor(kind: CodeHighlighter.Kind): Int = when (kind) {
    CodeHighlighter.Kind.COMMENT -> EditorColorScheme.COMMENT
    CodeHighlighter.Kind.STRING -> EditorColorScheme.LITERAL
    CodeHighlighter.Kind.LABEL -> EditorColorScheme.FUNCTION_NAME
    CodeHighlighter.Kind.LVAR -> EditorColorScheme.IDENTIFIER_VAR
    CodeHighlighter.Kind.GLOBAL -> EditorColorScheme.IDENTIFIER_NAME
    CodeHighlighter.Kind.NUMBER -> ScmColors.NUMBER
    CodeHighlighter.Kind.KEYWORD -> ScmColors.KEYWORD
    CodeHighlighter.Kind.OPCODE -> ScmColors.OPCODE
    CodeHighlighter.Kind.PLAIN -> -1
}

/**
 * 同步全量分析的 Spans：每行一个固定 spans 列表，渲染端读取稳定。
 * （AsyncIncrementalAnalyzeManager 在滚动/编辑并发时可能让部分行
 *   读到空 spans 导致丢色，这里整体替换、无中间态）
 */
private class SimpleScmSpans(private val map: List<List<Span>>) : Spans {
    override fun adjustOnInsert(start: CharPosition, end: CharPosition) {}
    override fun adjustOnDelete(start: CharPosition, end: CharPosition) {}
    override fun read(): Spans.Reader = object : Spans.Reader {
        private var line = 0
        override fun moveToLine(line: Int) { this.line = line }
        override fun getSpanCount(): Int = if (line in map.indices) map[line].size else 0
        override fun getSpanAt(i: Int): Span = map[line][i]
        override fun getSpansOnLine(line: Int): List<Span> = if (line in map.indices) map[line] else emptyList()
    }
    override fun supportsModify(): Boolean = false
    override fun modify(): Spans.Modifier = throw UnsupportedOperationException("read-only spans")
    override fun getLineCount(): Int = map.size
}

/** 同步分析：文本修改后整段分析并整体替换 Styles（不产生半成品，滚动稳定） */
private class ScmSimpleAnalyzeManager : SimpleAnalyzeManager<ScmState>() {
    override fun analyze(text: StringBuilder, delegate: SimpleAnalyzeManager<ScmState>.Delegate<ScmState>): Styles {
        val map = ArrayList<MutableList<Span>>()
        for (lineText in text.toString().split("\n")) {
            val spans = ArrayList<Span>()
            if (lineText.length <= CodeHighlighter.MAX_CHARS) {
                for (t in CodeHighlighter.classify(lineText, emptySet())) {
                    val colorId = kindToColor(t.kind)
                    if (colorId >= 0) spans.add(SpanFactory.obtain(t.start, TextStyle.makeStyle(colorId)))
                }
            }
            map.add(spans)
        }
        return Styles(SimpleScmSpans(map))
    }
}
