package com.sanny.builder.compiler;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GTA SA（无类型参数）CLEO 脚本编译器：文本 -> 字节码。
 *
 * 支持：opcode 行（HEX: 或名称）、:LABEL、{$...} 指令、const 块、注释、
 *       数字/变量（$N、N@、$NAME）/字符串（"..."）、@LABEL 跳转。
 * 暂不支持（返回错误）：数组 0@(i)、SBL 函数、if/while 结构化语法、#MODEL 名。
 */
public class SaCompiler {

    public static class Line {
        public int kind;            // 0=opcode, 1=label, 2=skip, 3=const , 4=end
        public int opcode = -1;
        public String raw;          // 参数部分原文
        public OpcodeTable.OpcodeDef def;
        public List<String> tokens = new ArrayList<>(); // 按占位符出现顺序
        public long offset = -1;    // 本行字节码偏移（pass1 计算）
        public String label;
        public String name;
        public String value;
    }

    public static final Map<String, Integer> EMPTY_GLOBALS = Collections.emptyMap();

    /** 编译完整脚本。返回 .cs 文件内容（header+main）。err 接收错误信息。 */
    public static byte[] compile(String text, OpcodeTable table, Map<String,Integer> globals, StringBuilder err) {
        Map<String, Long> labels = new HashMap<>();
        Map<String, String> constants = new HashMap<>();
        List<Line> lines;
        try {
            lines = parse(text, table, constants, labels, err);
        } catch (RuntimeException e) {
            err.append("parse error: ").append(e.getMessage());
            return null;
        }
        if (err.length() > 0) return null;

        // pass2: generate bytes
        ByteBuffer bb = ByteBuffer.allocate(64 * 1024).order(ByteOrder.LITTLE_ENDIAN);
        for (Line ln : lines) {
            if (ln.kind != 0) continue;
            if (!ln.def.params.isEmpty() && ln.tokens.size() < ln.def.params.size()) {
                err.append("opcode ").append(String.format("%04X", ln.opcode)).append(": 参数不足 (需要 ")
                        .append(ln.def.params.size()).append(" 个, 得到 ").append(ln.tokens.size()).append(" 个)\n");
                return null;
            }
            bb.putShort((short) ln.opcode);
            for (int i = 0; i < ln.def.params.size(); i++) {
                OpcodeTable.Placeholder ph = ln.def.params.get(i);
                String tok = ln.tokens.get(i);
                bb.put(encodeParam(ph, tok, constants, globals, labels, err, ln));
            }
        }
        // 段结尾：NOP NOP (00 00 00 00)
        bb.putShort((short) 0);
        bb.putShort((short) 0);
        byte[] code = new byte[bb.position()];
        System.arraycopy(bb.array(), 0, code, 0, code.length);
        return code;
    }

    public static List<Line> parse(String text, OpcodeTable table, Map<String,String> constants,
                                   Map<String, Long> labels, StringBuilder err) {
        List<Line> lines = new ArrayList<>();
        long offset = 0;
        boolean inConst = false;
        // 先扫 const 块（常量可前向引用……简单起见正序处理）
        List<String> rawLines = Arrays.asList(text.split("\n", -1));
        for (String raw : rawLines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            int c = line.indexOf("//");
            if (c == 0) continue;
            String body = c > 0 ? line.substring(0, c).trim() : line;
            if (body.isEmpty()) continue;
            if (body.startsWith("{") && body.endsWith("}")) continue; // {$...} 或块注释
            String lower = body.toLowerCase();

            if (inConst) {
                if (lower.equals("end")) { inConst = false; continue; }
                int eq = body.indexOf('=');
                if (eq > 0) {
                    String name = body.substring(0, eq).trim();
                    String value = body.substring(eq + 1).trim();
                    constants.put(name.toUpperCase(Locale.ROOT), value);
                    // 常量不产生代码；但占用行号偏移 0
                }
                lines.add(blank());
                continue;
            }
            if (lower.equals("const")) { inConst = true; lines.add(blank()); continue; }

            if (body.startsWith(":")) {
                String name = body.substring(1).trim();
                labels.put(name.toUpperCase(Locale.ROOT), offset);
                Line ln = new Line(); ln.kind = 1; ln.label = name;
                lines.add(ln);
                continue;
            }
            // opcode
            OpcodeTable.OpcodeDef def = null;
            String rest = body;
            int colon = body.indexOf(':');
            if (colon >= 0 && colon <= 6) {
                String hex = body.substring(0, colon).trim();
                try {
                    int id = Integer.parseInt(hex, 16);
                    def = table.get(id);
                    rest = body.substring(colon + 1).trim();
                } catch (NumberFormatException ignored) { }
            }
            if (def == null) {
                // 按名字
                int sp = body.indexOf(' ');
                String first = sp < 0 ? body : body.substring(0, sp);
                Integer id = table.idByName(first);
                if (id == null) {
                    err.append("无法识别: ").append(body).append("\n");
                    continue;
                }
                def = table.get(id);
                rest = sp < 0 ? "" : body.substring(sp + 1).trim();
            }
            Line ln = new Line();
            ln.kind = 0;
            ln.opcode = def.id;
            ln.def = def;
            ln.raw = rest;
            // 用正则提取参数 token
            extractTokens(ln, def, rest);
            ln.offset = offset;
            long lineLen = 2;
            for (OpcodeTable.Placeholder ph : def.params) lineLen += ph.width(GameMode.Encoding.SA);
            offset += lineLen;
            lines.add(ln);
        }
        return lines;
    }

    private static Line blank() { Line l = new Line(); l.kind = 2; return l; }

    private static void extractTokens(Line ln, OpcodeTable.OpcodeDef def, String rest) {
        // 构建正则
        StringBuilder re = new StringBuilder("^");
        for (Object seg : segments(def)) {
            if (seg instanceof String) re.append(escapeText((String) seg));
            else re.append("(.+?)");
        }
        re.append("\\s*$");
        Pattern p = Pattern.compile(re.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher m = p.matcher(rest);
        if (m.matches()) {
            for (int i = 1; i <= def.params.size(); i++) {
                ln.tokens.add(m.group(i).trim());
            }
        } else {
            // 失败：尝试按空格切分兜底（参数个数匹配）
            String[] parts = rest.split("\\s+");
            for (String s : parts) if (!s.isEmpty() && ln.tokens.size() < def.params.size()) ln.tokens.add(s);
        }
    }

    private static List<Object> segments(OpcodeTable.OpcodeDef def) {
        List<Object> segs = new ArrayList<>();
        int last = 0;
        for (OpcodeTable.Placeholder ph : def.params) {
            segs.add(def.format.substring(last, ph.fmtStart));
            segs.add(ph);
            last = ph.fmtEnd;
        }
        if (last < def.format.length()) segs.add(def.format.substring(last));
        return segs;
    }

    private static String escapeText(String t) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (Character.isWhitespace(ch)) {
                sb.append("\\s+");
                while (i + 1 < t.length() && Character.isWhitespace(t.charAt(i + 1))) i++;
            } else {
                if ("\\.[]{}()<>*+-=!?^$|".indexOf(ch) >= 0) sb.append('\\');
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static byte[] encodeParam(OpcodeTable.Placeholder ph, String tok,
                                      Map<String,String> constants, Map<String,Integer> globals,
                                      Map<String, Long> labels, StringBuilder err, Line ln) {
        ByteBuffer bb = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);
        char t = ph.type;
        String s = tok.trim();
        try {
            if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
                String str = s.substring(1, s.length() - 1);
                if (t == 'k' || t == 'r') {
                    byte[] b = str.getBytes(StandardCharsets.UTF_8);
                    ByteBuffer out = ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN);
                    out.put(b, 0, Math.min(b.length, 127)); out.put((byte) 0);
                    byte[] r = new byte[out.position()];
                    System.arraycopy(out.array(), 0, r, 0, r.length);
                    return r;
                }
                byte[] b = str.getBytes(StandardCharsets.UTF_8);
                return Arrays.copyOf(b, Math.min(b.length, 8));
            }
            long v;
            if (s.startsWith("@")) {
                Long target = labels.get(s.substring(1).toUpperCase(Locale.ROOT));
                if (target == null) { err.append("未定义标签: ").append(s).append(" @行 ").append(ln.raw).append("\n"); return new byte[4]; }
                v = target;
            } else if (s.endsWith("@")) {
                int idx = Integer.parseInt(s.substring(0, s.length() - 1));
                v = 0x8000L + idx;                  // 局部变量 N@ -> 0x8000+N
            } else if (s.startsWith("$")) {
                String name = s.substring(1);
                int idx;
                try { idx = Integer.parseInt(name); }
                catch (NumberFormatException e) {
                    Integer g = globals.get(name.toUpperCase(Locale.ROOT));
                    if (g == null) { err.append("未定义全局变量: ").append(s).append(" @行 ").append(ln.raw).append("\n"); return new byte[4]; }
                    idx = g;
                }
                v = idx;                            // 全局变量 $N -> N
            } else {
                String up = s.toUpperCase(Locale.ROOT);
                if (constants.containsKey(up)) {
                    // 常量值可能自身引用：简单递归一次
                    String val = constants.get(up);
                    return encodeParam(ph, val, constants, globals, labels, err, ln);
                }
                if (t == 'f') {
                    float f = Float.parseFloat(s);
                    bb.putFloat(f);
                    return toArray(bb);
                }
                if (s.startsWith("0x") || s.startsWith("-0x") || s.startsWith("+0x")) {
                    v = Long.parseLong(s.replaceFirst("^[+-]", "").substring(2), 16);
                    if (s.startsWith("-")) v = -v;
                } else {
                    v = Long.parseLong(s);
                }
            }
            bb.putInt((int) v);
        } catch (NumberFormatException e) {
            err.append("参数解析失败: \"").append(s).append("\" @行 ").append(ln.raw).append("\n");
            bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
            bb.putInt(0);
        }
        return toArray(bb);
    }

    private static byte[] toArray(ByteBuffer bb) {
        byte[] r = new byte[bb.position()];
        System.arraycopy(bb.array(), 0, r, 0, r.length);
        return r;
    }
}
