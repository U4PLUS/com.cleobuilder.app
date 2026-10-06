package com.sanny.builder.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CLEO/Sanny Builder 脚本的词法分类器（纯 Java，JVM 可测）。
 * 用于语法高亮：返回 token 列表（类型 + 文本区间），Android 侧据此上色。
 */
public final class CodeHighlighter {

    public enum Kind {
        COMMENT,   // //… 或 ;…
        STRING,    // '...'
        LABEL,     // :NAME
        LVAR,      // 0@…、1@…
        GLOBAL,    // $NAME、$123
        NUMBER,    // -12、3.14
        KEYWORD,   // wait/if/then/else/end/while/not/and/or…
        OPCODE,    // 指令名（首词，或已知指令名）
        PLAIN
    }

    /** 单次分类的文本长度上限：超长直接返回空（调用方跳过着色，防卡顿） */
    public static final int MAX_CHARS = 200_000;

    public static final class Token {
        public final Kind kind;
        public final int start;
        public final int end;

        Token(Kind kind, int start, int end) {
            this.kind = kind;
            this.start = start;
            this.end = end;
        }
    }

    private static final Pattern TOKEN = Pattern.compile(
            "//[^\\n]*"                 // 行注释 //
            + "|;[^\\n]*"               // 行注释 ;
            + "|\\{[^\\n]*\\}"          // {VERSION ..} 块注释行
            + "|'[^']*'"                // 字符串 '...'
            + "|:[A-Za-z_][A-Za-z0-9_]*"// 标签 :NAME
            + "|@[A-Za-z_][A-Za-z0-9_]*" // 标签引用 @NAME
            + "|\\$[A-Za-z_][A-Za-z0-9_]*" // 全局变量 $NAME（命名）
            + "|\\$\\d+"                 // 全局变量 $123（数字）
            + "|\\d+@[A-Za-z_][A-Za-z0-9_]*" // 数组槽 12@foo
            + "|\\d+@"                   // 局部变量 0@
            + "|-?\\d+(?:\\.\\d+)?"     // 数字
            + "|[A-Za-z_][A-Za-z0-9_]*" // 词（关键字/指令名/其它）
            );

    private static final java.util.Set<String> KEYWORDS = new java.util.HashSet<>();
    static {
        String[] ks = {
                "wait", "if", "then", "else", "end", "while", "repeat", "until",
                "not", "and", "or", "goto", "jump", "jf", "else_jump", "return", "terminate",
                "terminate_this_script", "script_name", "const", "var", "sbl",
                "for", "break", "continue"
        };
        for (String k : ks) KEYWORDS.add(k);
    }

    private CodeHighlighter() {}

    /** 对文本做词法切分并分类。opcodeNames 可选：已知指令名集合（小写），用于把普通词标为指令色。 */
    public static List<Token> classify(String text, java.util.Set<String> opcodeNamesLower) {
        List<Token> out = new ArrayList<>();
        if (text == null || text.length() > MAX_CHARS) return out; // 超长防护
        if (text == null || text.isEmpty()) return out;
        // /* */ 块注释区间（支持跨行；未闭合到文末）
        java.util.List<int[]> blocks = blockComments(text);
        Matcher m = TOKEN.matcher(text);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) out.add(new Token(commentOr(Kind.PLAIN, last, m.start(), blocks), last, m.start()));
            String t = m.group();
            Kind k = commentOr(kindOf(t, opcodeNamesLower), m.start(), m.end(), blocks);
            out.add(new Token(k, m.start(), m.end()));
            last = m.end();
        }
        if (last < text.length()) out.add(new Token(commentOr(Kind.PLAIN, last, text.length(), blocks), last, text.length()));
        return out;
    }

    private static java.util.List<int[]> blockComments(String text) {
        java.util.List<int[]> blocks = new ArrayList<>();
        int bi = text.indexOf("/*");
        while (bi >= 0) {
            int be = text.indexOf("*/", bi + 2);
            if (be < 0) { blocks.add(new int[] { bi, text.length() }); break; }
            blocks.add(new int[] { bi, be + 2 });
            bi = text.indexOf("/*", be + 2);
        }
        return blocks;
    }

    private static boolean inBlock(int start, int end, java.util.List<int[]> blocks) {
        for (int[] b : blocks) {
            if (start >= b[1]) continue;
            if (end <= b[0]) continue;
            return true; // 区间相交
        }
        return false;
    }

    private static Kind commentOr(Kind k, int start, int end, java.util.List<int[]> blocks) {
        return inBlock(start, end, blocks) ? Kind.COMMENT : k;
    }

    private static Kind kindOf(String t, java.util.Set<String> opcodeNamesLower) {
        char c0 = t.charAt(0);
        switch (c0) {
            case '/': case ';': return Kind.COMMENT;
            case '{': return Kind.COMMENT;
            case '\'': return Kind.STRING;
            case ':': return Kind.LABEL;
            case '@': return Kind.LABEL;
            case '$': return Kind.GLOBAL;
            default: break;
        }
        if (t.indexOf('@') > 0) {
            // \d+@… 数组槽 / \d+@ 局部变量
            return Kind.LVAR;
        }
        if (Character.isDigit(c0) || c0 == '-' || c0 == '+') return Kind.NUMBER;
        String low = t.toLowerCase(Locale.ROOT);
        if (KEYWORDS.contains(low)) return Kind.KEYWORD;
        if (opcodeNamesLower != null && opcodeNamesLower.contains(low)) return Kind.OPCODE;
        return Kind.PLAIN;
    }
}
