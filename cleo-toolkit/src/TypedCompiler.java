package com.sanny.builder.compiler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GTA III/VC/SA Mobile 类型化参数编译器。
 * 参数类型字节由值决定：整数按范围选 int8/int16/int32，浮点 -> float32，
 * $N -> var(u16 = N*4)，N@ -> lvar(u16 N)，'xxx' -> string8，
 * @LABEL -> int32 = -(11 + 主代码偏移)。
 * "not <name> ..." 前缀 -> opcode 第二字节 | 0x80。
 */
public final class TypedCompiler {

    /** 名称别名（SB 常用写法 -> opcode 模板名）；json 名未命中时使用 */
    private static final Map<String, String> ALIASES = new HashMap<>();
    static {
        ALIASES.put("get_widget_value", "0A5A");
        ALIASES.put("cset_lvar_int_to_lvar_float", "0092");
        ALIASES.put("add_shop_item", "add_shop_widget_menu_item");
        ALIASES.put("create_shop_widget", "create_shop_widget_menu");
        ALIASES.put("is_widget_released", "is_widget_released");
        ALIASES.put("jump_if_false", "jump_if_false");
        ALIASES.put("goto_if_false", "jump_if_false");
        ALIASES.put("goto", "jump");
        ALIASES.put("terminate_this_custom_script", "terminate_this_script");
        ALIASES.put("end_thread", "terminate_this_script");
        ALIASES.put("wait", "wait");
        ALIASES.put("if", "if");
    }

    private static final Pattern OP_LINE = Pattern.compile(
            "^(not\\s+)?(\\w+)\\s*:?\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEX_LINE = Pattern.compile(
            "^([0-9a-fA-F]{4})\\s*:\\s*(.*)$");

    static class PendingLine {
        int label = -1;
        OpcodeTable.OpcodeDef def;
        boolean neg;
        String[] argTokens;
        int bytePos; // 每条指令长度（第二遍）
        int[] argWidths;
        byte[] extra; // if 行尾随条件指令
        java.io.ByteArrayOutputStream condBytes; // if 头：后续条件行字节
        boolean isJump;
        boolean jumpIfFalse;
        int jumpTarget; // 004D/0002 跳转目标偏移
        boolean isWhileHead; // while 头
    }

    public static byte[] compile(String src, OpcodeTable table,
                                 Map<String, Integer> globalsByName, StringBuilder err) {
        err.setLength(0);
        PendingLine whileIfJumpPending = null; // while 条件假跳转（end 处填目标）
        int whileBeginPos = 0;                 // while 头偏移（end 处跳回）
        List<PendingLine> lines = new ArrayList<>();
        TreeMap<Integer, String> labels = new TreeMap<>(); // 偏移 -> 标签名
        Map<String, Integer> labelOffsets = new HashMap<>();
        Map<String, Integer> consts = new HashMap<>();

        String[] raw = src.split("\n");
        // 预处理：剥离 /* */ 块注释（跨行）后过滤注释/指令行
        List<String> body = new ArrayList<>();
        boolean inBlockComment = false;
        boolean inConst = false;
        for (String s : raw) {
            String t = s.trim();
            if (t.endsWith("\r")) t = t.substring(0, t.length() - 1).trim();
            // 多行 /* */ 块注释
            if (inBlockComment) {
                int e = t.indexOf("*/");
                if (e >= 0) { inBlockComment = false; t = t.substring(e + 2).trim(); }
                else continue;
            }
            int bs = t.indexOf("/*");
            while (bs >= 0) {
                int be = t.indexOf("*/", bs + 2);
                if (be >= 0) { t = (t.substring(0, bs) + t.substring(be + 2)).trim(); bs = t.indexOf("/*"); }
                else { t = t.substring(0, bs).trim(); inBlockComment = true; break; }
            }
            if (t.isEmpty()) continue;
            if (t.toLowerCase(Locale.ROOT).startsWith("script_name")) continue;
            if (t.equalsIgnoreCase("const") || t.equalsIgnoreCase("{const")) { inConst = true; continue; }
            if (inConst && t.equalsIgnoreCase("end")) { inConst = false; continue; }
            if (inConst) continue;
            if (t.startsWith("{") || t.startsWith("//") || t.startsWith(";")) continue;
            body.add(t);
        }

        int pos = 0;
        int lineNo = 0;
        java.util.ArrayDeque<Frame> st = new java.util.ArrayDeque<>(); // 块嵌套栈
        for (String line : body) {
            line = stripComment(line);
            lineNo++;
            Matcher lm = Pattern.compile("^:([A-Za-z0-9_]+)\\s*$").matcher(line);
            if (lm.matches()) {
                String label = lm.group(1);
                if (labelOffsets.containsKey(label)) continue; // 重复标签忽略
                labelOffsets.put(label, pos);
                labels.put(pos, label);
                continue;
            }
            String low = line.toLowerCase(Locale.ROOT);
            if (low.equals("then")) {
                if (st.isEmpty() || st.peek().kind != Frame.K_IF) { err.insert(0, "第 " + lineNo + " 行: "); err.append("then 出现在 if 之外"); return null; }
                Frame f = st.peek();
                f.condOpen = false;
                PendingLine j = new PendingLine();
                j.isJump = true; j.jumpIfFalse = true; j.bytePos = pos;
                lines.add(j);
                pos += 7; // 4D 00 01 + int32
                f.jumpPending = j;
                continue;
            }
            if (low.equals("else")) {
                if (st.isEmpty() || st.peek().kind != Frame.K_IF) { err.insert(0, "第 " + lineNo + " 行: "); err.append("else 出现在 if 之外"); return null; }
                Frame f = st.peek();
                if (f.jumpPending != null) f.jumpPending.jumpTarget = pos + 7;
                PendingLine j = new PendingLine();
                j.isJump = true; j.jumpIfFalse = false; j.bytePos = pos;
                lines.add(j);
                pos += 7; // 02 00 01 + int32
                f.elseJump = j;
                f.jumpPending = null;
                f.condOpen = false;
                continue;
            }
            if (low.equals("end")) {
                if (st.isEmpty()) { err.append("end 出现在块外"); return null; }
                Frame f = st.peek();
                if (f.kind == Frame.K_WHILE) {
                    // while end：生成 0002 jump @begin，004D 目标 = end 后（goto 之后）
                    PendingLine g = new PendingLine();
                    g.isJump = true; g.jumpIfFalse = false; g.bytePos = pos;
                    g.jumpTarget = f.whileBeginPos;
                    lines.add(g);
                    pos += 7;
                    if (f.jumpPending != null) f.jumpPending.jumpTarget = pos; // 条件假 → end 后（回跳 0002 之后）
                    st.pop();
                    continue;
                }
                // IF end
                if (f.elseJump != null) { f.elseJump.jumpTarget = pos; st.pop(); continue; }
                if (f.jumpPending != null) { f.jumpPending.jumpTarget = pos; st.pop(); continue; }
                if (f.condOpen) { st.pop(); continue; } // if cond then 之间直接 end？——不应发生，保守放行
                err.append("end 出现在块外");
                return null;
            }
            int start = pos;
            PosIO pio = new PosIO();
            parseAndMeasure(line, table, globalsByName, err, pio);
            if (err.length() > 0) { err.insert(0, "第 " + lineNo + " 行: "); return null; }
            if (pio.isIfHead) {
                // if/while 头行：先闭合当前收集期，再压栈
                if (!st.isEmpty() && st.peek().condOpen) pos += closeCond(st.peek(), pos, lines);
                Frame f = new Frame();
                f.kind = pio.isWhileHead ? Frame.K_WHILE : Frame.K_IF;
                f.ifHeadPos = start;
                f.condOpen = true;
                f.isWhileHead = pio.isWhileHead;
                st.push(f);
            } else if (!st.isEmpty() && st.peek().condOpen) {
                if (table.isCondition(pio.def.id)) {
                    // 条件行：编译进栈顶 if 头
                    byte[] cb = compileLine(line, table, globalsByName, err);
                    if (err.length() > 0) { err.insert(0, "第 " + lineNo + " 行: "); return null; }
                    PendingLine hl = st.peek().headLine;
                    if (hl.condBytes == null) hl.condBytes = new java.io.ByteArrayOutputStream();
                    hl.condBytes.write(cb, 0, cb.length);
                    pos += cb.length;
                    continue;
                }
                // 非条件行：条件块结束；无 then 的 if 帧此时完成（SB 旧式：条件 + goto_if_false，无需 end）
                Frame cur = st.peek();
                pos += closeCond(cur, pos, lines);
                if (cur.kind == Frame.K_IF) st.pop();
            }
            PendingLine pl = new PendingLine();
            pl.def = pio.def; pl.neg = pio.neg;
            pl.argTokens = pio.tokens;
            pl.argWidths = pio.widths;
            pl.bytePos = start;
            int len = 2;
            for (int w : pl.argWidths) len += w;
            if (pio.extra != null) {
                pl.extra = pio.extra;
                len += pio.extra.length;
            }
            pos += len;
            lines.add(pl);
            if (pio.isIfHead && !st.isEmpty()) st.peek().headLine = pl;
        }
        if (!st.isEmpty()) {
            err.append("if/then/else/end 或 while 块未闭合");
            return null;
        }
        int mainSize = pos;

        // 第二遍生成
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (PendingLine pl : lines) {
            if (pl.isJump) {
                int jid = pl.jumpIfFalse ? table.jumpIfFalseId() : 0x02;
                out.write(jid & 0xFF);
                out.write((jid >> 8) & 0xFF);
                out.write(0x01);
                // SA: jump 距离 = -(11 + 目标偏移)
                int dist = -(11 + pl.jumpTarget);
                write32(out, dist);
                continue;
            }
            int op = pl.def.id;
            if (pl.neg) op = (op & 0xFF) | ((op >> 8 | 0x80) << 8);
            out.write(op & 0xFF);
            out.write((op >> 8) & 0xFF);
            java.util.List<OpcodeTable.Placeholder> ps = new ArrayList<>(pl.def.params);
            ps.sort(java.util.Comparator.comparingInt(a -> a.index));
            for (OpcodeTable.Placeholder ph : ps) {
                String tok = pl.argTokens[ph.index - 1];
                encodeArg(pl, ph, tok, pl.bytePos, mainSize, labelOffsets, globalsByName, consts, out, err);
                if (err.length() > 0) return null;
            }
            if (pl.extra != null) out.write(pl.extra, 0, pl.extra.length);
            if (pl.condBytes != null) out.write(pl.condBytes.toByteArray(), 0, pl.condBytes.size());
        }
        return out.toByteArray();
    }

    /** 块结构帧（if/while 嵌套栈元素） */
    static class Frame {
        static final int K_IF = 0, K_WHILE = 1;
        int kind;
        int ifHeadPos;       // 头行字节偏移（while 回跳用）
        boolean isWhileHead;
        boolean condOpen;    // 条件收集期
        PendingLine headLine; // 头行 PendingLine（条件字节写入其 condBytes）
        PendingLine jumpPending; // then 的 004D / while 条件的 004D
        PendingLine elseJump;    // else 的 0002
        int whileBeginPos;
    }

    /** 闭合条件收集期：if 直接关；while 生成 004D 占位并记录回跳起点 */
    private static int closeCond(Frame f, int pos, java.util.List<PendingLine> lines) {
        int w = 0;
        if (f.isWhileHead) {
            PendingLine j = new PendingLine();
            j.isJump = true; j.jumpIfFalse = true; j.bytePos = pos;
            lines.add(j);
            f.whileBeginPos = f.ifHeadPos;
            f.jumpPending = j;
            w = 7;
        }
        f.condOpen = false;
        return w;
    }

    static class PosIO {
        boolean neg;
        OpcodeTable.OpcodeDef def;
        String[] tokens;
        int[] widths;
        byte[] extra; // if 行尾随条件
        boolean isIfHead;
        boolean isWhileHead;
    }

    private static void parseAndMeasure(String line, OpcodeTable table,
                                        Map<String, Integer> globalsByName, StringBuilder err, PosIO io) {
        line = stripComment(line);
        Matcher lm = HEX_LINE.matcher(line);
        String namePart, argsPart;
        if (lm.matches()) {
            int hx = Integer.parseInt(lm.group(1), 16);
            // mobile 条件指令：0x8000 位 = not 前缀（反编译输出 8118: ... 形式）。
            // 查模板时剥标志；输出时由 io.neg 重新置 0x8000（与名字行 not 一致）
            boolean negHex = (hx & 0x8000) != 0;
            OpcodeTable.OpcodeDef def = table.get(hx);
            if (def == null && negHex) def = table.get(hx & 0x7FFF);
            if (def == null) { err.append("未知 opcode ").append(lm.group(1)); return; }
            io.def = def; io.neg = negHex; namePart = null; argsPart = lm.group(2);
            // hex 行：宽松参数提取（跳过名称/字面量词 token，如 "0175: set_car_heading 7@ to 6@"）
            List<String> toks0 = splitArgs(line);
            List<String> params = new ArrayList<>();
            for (int k = 1; k < toks0.size(); k++) {
                String tk = toks0.get(k);
                if (!isParamToken(tk)) continue;
                params.add(tk);
            }
            if (params.size() < def.params.size()) {
                err.append("参数不足: ").append(line)
                   .append(" (需要 ").append(def.params.size()).append(" 个)");
                return;
            }
            String[] rawArgs = params.subList(0, def.params.size()).toArray(new String[0]);
            io.tokens = new String[def.params.size()];
            io.widths = new int[def.params.size()];
            for (int k = 0; k < def.params.size(); k++) {
                OpcodeTable.Placeholder ph = def.params.get(k);
                int w = argWidth(ph, rawArgs[k], hx, err);
                if (w < 0) { err.append(line).append(": ").append(err); return; }
                io.tokens[ph.index - 1] = rawArgs[k];
                io.widths[ph.index - 1] = w;
            }
            return;
        } else {
            Matcher m = OP_LINE.matcher(line);
            if (!m.matches()) {
                // 行首非词字符（如 "$X == 1"、"0@ == 1"）：无名称模板匹配
                OpcodeTable.OpcodeDef byLess = matchByNameLess(normalizeCmp(splitArgs(line)), table);
                if (byLess == null) { err.append("无法解析: ").append(line); return; }
                io.def = byLess;
                io.neg = false;
                namePart = null; argsPart = line;
            } else {
            io.neg = m.group(1) != null;
            String nm = m.group(2);
            String rest = m.group(3);
            if (nm.equalsIgnoreCase("while")) {
                // while <条件>：00D6 头 + 条件（同行或后续行吸收），条件假 → 004D 退出
                OpcodeTable.OpcodeDef d = table.getByName("if");
                if (d == null) { err.append("缺少 if opcode 定义"); return; }
                io.def = d;
                io.tokens = new String[] { "0" };
                io.widths = new int[] { 2 };
                io.isIfHead = true;
                io.isWhileHead = true;
                String cond = rest.trim();
                if (!cond.isEmpty() && !cond.equalsIgnoreCase("not")) {
                    byte[] extra = compileLine(cond, table, globalsByName, err);
                    if (err.length() > 0) return;
                    io.extra = extra;
                }
                return;
            }
            if (nm.equalsIgnoreCase("if")) {
                // if / if and / if or / if not；00D6 参数 0/3/2，条件部分另编译
                String lr = rest.trim().toLowerCase();
                int ifArg = 0;
                String cond = "";
                if (lr.startsWith("and ")) { ifArg = 3; cond = rest.trim().substring(4); }
                else if (lr.startsWith("or ")) { ifArg = 2; cond = rest.trim().substring(3); }
                else if (lr.startsWith("and")) { ifArg = 3; cond = rest.trim().substring(3); }
                else if (lr.startsWith("or")) { ifArg = 2; cond = rest.trim().substring(2); }
                else cond = rest.trim();
                OpcodeTable.OpcodeDef d = table.getByName("if");
                if (d == null) { err.append("缺少 if opcode 定义"); return; }
                io.def = d;
                io.tokens = new String[] { String.valueOf(ifArg) };
                io.widths = new int[] { 2 }; // int8: 类型+值
                io.isIfHead = true;
                if (!cond.isEmpty() && !cond.equalsIgnoreCase("not")) {
                    byte[] extra = compileLine(cond, table, globalsByName, err);
                    if (err.length() > 0) return;
                    io.extra = extra;
                }
                return;
            }
            OpcodeTable.OpcodeDef def = table.getByName(nm);
            if (def == null) {
                String alias = ALIASES.get(nm.toLowerCase(Locale.ROOT));
                if (alias != null) {
                    def = table.getByName(alias);
                    if (def == null) {
                        try { def = table.get(Integer.parseInt(alias, 16)); } catch (NumberFormatException ignored) { }
                    }
                }
            }
            if (def == null) {
                // 名称未命中：尝试无名称模板匹配（如 "2@ = 3" -> 0006 "%1d% = %2d%"）
                OpcodeTable.OpcodeDef byLess = matchByNameLess(normalizeCmp(splitArgs(line)), table);
                if (byLess == null) { err.append("未知 opcode: ").append(nm); return; }
                io.def = byLess;
                io.neg = false;
            } else {
                io.def = def;
            }
            namePart = nm; argsPart = m.group(3);
            }
        }
        // 按模板消费字面量词，提取参数
        List<String> toks = normalizeCmp(splitArgs(line));
        List<OpcodeTable.Placeholder> phs = io.def.params;
        String[] args = extractArgs(toks, io.def, io.neg);
        if (args == null) {
            // 无名称模板匹配（如 "2@ = 3" -> 0006 "%1d% = %2d%"）
            OpcodeTable.OpcodeDef byLess = matchByNameLess(toks, table);
            if (byLess == null) {
                err.append("参数不足或无法匹配: ").append(line)
                   .append(" (需要 ").append(phs.size()).append(" 个)");
                return;
            }
            io.def = byLess;
            io.neg = false;
            args = extractArgs(toks, byLess);
            if (args == null) { err.append("无法匹配: ").append(line); return; }
            phs = byLess.params;
        }
        // 按占位符编号（%1d%、%2d%…）重排参数：SB 编码顺序 = 编号顺序
        io.tokens = new String[phs.size()];
        io.widths = new int[phs.size()];
        for (int k = 0; k < phs.size(); k++) {
            OpcodeTable.Placeholder ph = phs.get(k);
            String tok = args[k];
            int w = argWidth(ph, tok, io.def.id, err);
            if (w < 0) { err.append(line).append(": ").append(err); return; }
            io.tokens[ph.index - 1] = tok;
            io.widths[ph.index - 1] = w;
        }
    }

    /** 遍历"以占位符开头"的模板，按字面量与参数类型匹配，取 id 最小者 */
    private static OpcodeTable.OpcodeDef matchByNameLess(List<String> toks, OpcodeTable table) {
        OpcodeTable.OpcodeDef best = null;
        for (OpcodeTable.OpcodeDef d : table.allDefs()) {
            if (d.params.isEmpty()) continue;
            if (!d.format.trim().startsWith("%")) continue;
            String[] args = extractArgs(toks, d);
            if (args == null) continue;
            if (!typesOk(args, d, table)) continue;
            if (best == null || d.id < best.id) best = d;
        }
        return best;
    }

    /** 参数形式 vs opcode input 类型表（type/source） */
    private static boolean typesOk(String[] args, OpcodeTable.OpcodeDef d, OpcodeTable table) {
        String[][] types = table.typesOf(d.id);
        if (types == null || types.length == 0) return true; // 未知类型宽松
        if (types.length != args.length) return true;
        for (int i = 0; i < args.length; i++) {
            String type = types[i].length > 0 ? types[i][0] : null;
            String source = types[i].length > 1 ? types[i][1] : null;
            if (!typeOk(args[i], type, source)) return false;
        }
        return true;
    }

    /** 参数 token 判定（hex 行宽松提取用）：变量/数字/字符串/标签；符号与词跳过 */
    private static boolean isParamToken(String t) {
        if (t.isEmpty()) return false;
        if (isNameWord(t)) return false; // 纯字母词 → 字面量/名称
        if (arrayOf(t) != null) return true; // 数组 0@(1@)、$X(0@)
        if (t.startsWith("$") || t.startsWith("@") || t.startsWith("'")) return true;
        if (t.endsWith("@") && Character.isDigit(t.charAt(0))) return true;
        if (t.startsWith("0x") || t.startsWith("0X")) return true;
        return t.matches("[-+]?\\d+(\\.\\d+)?");
    }

    private static boolean isNameWord(String t) {
        if (t.isEmpty()) return false;
        char c = t.charAt(0);
        return Character.isLetter(c) || c == '_';
    }

    private static boolean typeOk(String tok, String type, String source) {
        String[] arrTok = arrayOf(tok);
        boolean isLvar = (tok.endsWith("@") && Character.isDigit(tok.charAt(0))) || (arrTok != null && arrTok[0].equals("L"));
        boolean isVar = tok.startsWith("$") || (arrTok != null && arrTok[0].equals("V"));
        boolean isStr = tok.startsWith("'") && tok.endsWith("'");
        boolean isLabel = tok.startsWith("@");
        boolean isNum = !isLvar && !isVar && !isStr && !isLabel && isNumeric(tok);
        // 纯字母词（如 cset_lvar_int_to_lvar_float）不能作为参数
        if (!isLvar && !isVar && !isStr && !isLabel && !isNum) return false;
        if (source != null && !source.isEmpty()) {
            if (source.equals("var_local")) return isLvar;
            if (source.equals("var_global")) return isVar;
            if (source.equals("label")) return isLabel;
            if (source.equals("var_any")) return isLvar || isVar;
            if (source.equals("literal")) {
                if (!isNum) return false;
                boolean floatLit = tok.contains(".") || tok.contains("e") || tok.contains("E") || tok.endsWith("f") || tok.endsWith("F");
                if (type != null) {
                    if (type.equals("float")) return true;          // float 参数接任意数字
                    if (type.equals("int") || type.equals("bool")) return !floatLit;
                }
                return true;
            }
        }
        if (type != null && !type.isEmpty()) {
            if (type.equals("string") || type.equals("gxt_key") || type.equals("istring")) return isStr;
            if (type.equals("float") || type.equals("int") || type.equals("bool")) return isNum || isLvar || isVar;
            if (type.equals("label")) return isLabel;
        }
        return true;
    }

    private static boolean isNumeric(String tok) {
        try {
            if (tok.startsWith("0x") || tok.startsWith("0X")) { Long.parseLong(tok.substring(2), 16); return true; }
            Double.parseDouble(tok);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 按空白分割模板中的字面量词（保留 = + 等符号；避免 String.split 去尾空串陷阱） */
    private static java.util.List<String> literalWords(String s) {
        java.util.List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (!Character.isWhitespace(c)) {
                cur.append(c);
            } else if (cur.length() > 0) {
                out.add(cur.toString());
                cur.setLength(0);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    /** 剥离行内 // 注释（字符串内保留） */
    private static String stripComment(String line) {
        boolean inQ = false;
        for (int i = 0; i + 1 < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'') inQ = !inQ;
            if (!inQ && c == '/' && line.charAt(i + 1) == '/') {
                return line.substring(0, i).trim();
            }
        }
        return line;
    }

    /**
     * 按 opcode 模板从源码行提取参数。
     * SB 语法：行 = [名称] 参数…，模板中的字面量词（can_move/price/ms…）会被消费跳过。
     * 模板以 % 开头（如 "%1d% = %2d%"）时行首无名称。
     */
    private static String[] extractArgs(List<String> tokens, OpcodeTable.OpcodeDef def) {
        return extractArgs(tokens, def, false);
    }
    private static String[] extractArgs(List<String> tokens, OpcodeTable.OpcodeDef def, boolean neg) {
        String fmt = def.format.trim();
        boolean noName = fmt.startsWith("%");
        List<OpcodeTable.Placeholder> phs = def.params;
        String[] args = new String[phs.size()];
        // 行首 token 若是字母词（如别名 cset_lvar_int_to_lvar_float），跳过名称 token
        boolean nameGiven = !tokens.isEmpty() && isNameWord(tokens.get(0));
        // hex 前缀行（如 "00A1: set_actor ..."）多一个前缀 token
        boolean hexPref = !tokens.isEmpty() && tokens.get(0).matches("[0-9a-fA-F]+:");
        int base = noName ? (nameGiven ? (neg ? 2 : 1) : 0) : (neg ? 2 : 1);
        int i = base + (hexPref ? 1 : 0);
        // 无名称模板从 0 起；有名称模板的名称段不参与字面量消费
        int prev = noName ? 0 : (phs.isEmpty() ? 0 : phs.get(0).fmtStart);
        int a = 0;
        for (OpcodeTable.Placeholder ph : phs) {
            // 前字面量段（占位符之间的文本词）——必须全部按序匹配
            String lit = def.format.substring(prev, ph.fmtStart);
            for (String w : literalWords(lit)) {
                if (i < tokens.size() && tokens.get(i).equalsIgnoreCase(w)) {
                    i++;
                } else return null;
            }
            if (i >= tokens.size()) return null;
            args[a++] = tokens.get(i++);
            prev = ph.fmtEnd;
        }
        // 尾部校验：剩余 token 必须全为模板尾部字面量词
        String tail = def.format.substring(prev);
        for (String w : literalWords(tail)) {
            if (i < tokens.size() && tokens.get(i).equalsIgnoreCase(w)) {
                i++;
            } else break;
        }
        if (i < tokens.size()) return null;
        return args;
    }

    /** 编译单个条件行（if X / if not X 等）为字节 */
    private static byte[] compileLine(String line, OpcodeTable table,
                                      Map<String, Integer> globalsByName, StringBuilder err) {
        PosIO io = new PosIO();
        parseAndMeasure(line, table, globalsByName, err, io);
        if (err.length() > 0) return null;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int op = io.def.id;
        if (io.neg) op = (op & 0xFF) | ((op >> 8 | 0x80) << 8);
        out.write(op & 0xFF);
        out.write((op >> 8) & 0xFF);
        PendingLine pl = new PendingLine();
        pl.def = io.def; pl.neg = io.neg;
        pl.argTokens = io.tokens; pl.argWidths = io.widths;
        int i = 0;
        for (OpcodeTable.Placeholder ph : io.def.params) {
            encodeArg(pl, ph, io.tokens[i], 0, 0, new HashMap<String,Integer>(),
                    globalsByName, new HashMap<String,Integer>(), out, err);
            if (err.length() > 0) return null;
            i++;
        }
        if (io.extra != null) out.write(io.extra, 0, io.extra.length);
        return out.toByteArray();
    }

    /** 参数编码宽度（含类型字节） */
    private static int argWidth(OpcodeTable.Placeholder ph, String tok, int op, StringBuilder err) {
        if (ph.type == 'p') return 1 + 4; // int32
        if (ph.type == 'f') return 1 + 4; // float32
        if (ph.type == 'g' || ph.type == 's' || ph.type == 'k') return 1 + 8; // string8
        if (arrayOf(tok) != null) return 6; // [类型][基址2][索引2][元素大小1]（须在 $ 判定前）
        if (tok.startsWith("$")) {
            String v = tok.substring(1);
            if (ph.type == 'o' || ph.type == 'm') return 1 + 4; // 目标参数按 int32
            return 1 + 2; // var
        }
        if (tok.endsWith("@")) return 1 + 2; // lvar
        if (tok.contains(".") || tok.contains("e") || tok.contains("E")) return 1 + 4; // float32
        return 1 + intWidth(parseNum(tok, err));
    }

    private static int intWidth(long v) {
        if (v >= -128 && v <= 127) return 1;
        if (v >= -32768 && v <= 32767) return 2;
        return 4;
    }

    private static long parseNum(String tok, StringBuilder err) {
        try {
            if (tok.startsWith("0x") || tok.startsWith("0X")) return Long.parseLong(tok.substring(2), 16);
            return Long.parseLong(tok);
        } catch (NumberFormatException e) {
            err.append("无法解析数字: ").append(tok);
            return 0;
        }
    }

    private static void encodeArg(PendingLine pl, OpcodeTable.Placeholder ph, String tok, int instrOffset,
                                  int mainSize, Map<String, Integer> labelOffsets,
                                  Map<String, Integer> globalsByName, Map<String, Integer> consts,
                                  java.io.ByteArrayOutputStream out, StringBuilder err) {
        int t = typeFor(ph, tok);
        out.write(t);
        switch (t) {
            case TypedDecoder.T_INT32: {
                int v;
                if (tok.startsWith("@")) {
                    v = encodeJump(ph, tok, instrOffset, labelOffsets, err);
                    if (err.length() > 0) return;
                } else {
                    v = (int) parseNum(tok, err);
                    if (err.length() > 0) return;
                }
                write32(out, v);
                break;
            }
            case TypedDecoder.T_INT8:
                out.write((int) parseNum(tok, err)); break;
            case TypedDecoder.T_INT16:
                write16(out, (int) parseNum(tok, err)); break;
            case TypedDecoder.T_FLOAT32: {
                float v;
                try { v = Float.parseFloat(tok); }
                catch (NumberFormatException ex) { err.append("无法解析浮点数: ").append(tok); return; }
                write32(out, Float.floatToIntBits(v));
                break;
            }
            case TypedDecoder.T_VAR: {
                String v = tok.substring(1);
                int idx = resolveGlobal(v, globalsByName, err);
                write16(out, idx * 4);
                break;
            }
            case TypedDecoder.T_LVAR: {
                int idx = Integer.parseInt(tok.substring(0, tok.length() - 1));
                write16(out, idx);
                break;
            }
            case TypedDecoder.T_VAR_ARRAY: case TypedDecoder.T_LVAR_ARRAY: {
                String[] arr = arrayOf(tok);
                if (arr == null) { err.append("数组语法错误: ").append(tok); return; }
                boolean varArr = arr[0].equals("V");
                String base = arr[1];
                if (varArr) {
                    int bi = resolveGlobal(base.substring(1), globalsByName, err);
                    if (err.length() > 0) return;
                    write16(out, bi * 4); // 全局字节偏移（与单变量 var 编码一致）
                } else {
                    write16(out, Integer.parseInt(base.substring(0, base.length() - 1)));
                }
                String ix = arr[2];
                int ival;
                if (ix.endsWith("@") && Character.isDigit(ix.charAt(0))) ival = Integer.parseInt(ix.substring(0, ix.length() - 1));
                else if (ix.startsWith("$")) { ival = resolveGlobal(ix.substring(1), globalsByName, err); if (err.length() > 0) return; }
                else ival = (int) parseNum(ix, err);
                if (err.length() > 0) return;
                write16(out, ival);
                out.write(4); // 元素大小字节：int 默认 4（SB 数组元素缺省；待样本对拍确认）
                break;
            }
            case TypedDecoder.T_STRING8: {
                String s = tok;
                if (s.startsWith("'") && s.endsWith("'") && s.length() >= 2) {
                    s = s.substring(1, s.length() - 1);
                }
                for (int i = 0; i < 8; i++) {
                    if (i < s.length()) out.write(s.charAt(i) & 0xFF); else out.write(0);
                }
                break;
            }
            default:
                err.append("未支持参数编码");
        }
    }

    private static int encodeJump(OpcodeTable.Placeholder ph, String tok, int instrOffset,
                                  Map<String, Integer> labelOffsets, StringBuilder err) {
        String lbl = tok.substring(1);
        Integer tgt = labelOffsets.get(lbl);
        if (tgt == null) { err.append("未知标签: ").append(lbl); return 0; }
        return -(11 + tgt);
    }

    /** 数组参数 token 解析：0@(1@)、0@(5)、$X(0@)、$123(0@) → {V|L, base, idx} 或 null */
    private static String[] arrayOf(String tok) {
        if (tok == null) return null;
        int p = tok.indexOf('(');
        if (p <= 0 || !tok.endsWith(")")) return null;
        String base = tok.substring(0, p);
        String idx = tok.substring(p + 1, tok.length() - 1);
        if (idx.isEmpty() || idx.indexOf('(') >= 0 || idx.indexOf(')') >= 0 || idx.indexOf(' ') >= 0) return null;
        if (base.matches("\\d+@")) return new String[]{"L", base, idx};
        if (base.matches("\\$[A-Za-z0-9_]+")) return new String[]{"V", base, idx};
        return null;
    }

    private static int typeFor(OpcodeTable.Placeholder ph, String tok) {
        if (ph.type == 'p') return TypedDecoder.T_INT32;
        if (ph.type == 'f') return TypedDecoder.T_FLOAT32;
        if (ph.type == 'g' || ph.type == 's' || ph.type == 'k') return TypedDecoder.T_STRING8;
        String[] arrTok = arrayOf(tok);
        if (arrTok != null) return arrTok[0].equals("V") ? TypedDecoder.T_VAR_ARRAY : TypedDecoder.T_LVAR_ARRAY;
        if (tok.startsWith("$")) return TypedDecoder.T_VAR;
        if (tok.endsWith("@")) return TypedDecoder.T_LVAR;
        if (tok.contains(".") || tok.contains("e") || tok.contains("E")) return TypedDecoder.T_FLOAT32;
        long v = parseNum(tok, new StringBuilder());
        if (ph.type == 'm' || ph.type == 'o') return TypedDecoder.T_INT32;
        if (v >= -128 && v <= 127) return TypedDecoder.T_INT8;
        if (v >= -32768 && v <= 32767) return TypedDecoder.T_INT16;
        return TypedDecoder.T_INT32;
    }

    private static int resolveGlobal(String v, Map<String, Integer> globalsByName, StringBuilder err) {
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            Integer idx = globalsByName.get(v.toUpperCase(Locale.ROOT));
            if (idx == null) { err.append("未知全局变量: $").append(v); return 0; }
            return idx;
        }
    }

    /**
     * GTA 引擎只有 GREATER 系比较 opcode（模板字面量为 > / >= / ==）。
     * 用户写 A < B 或 A <= B 时按 SB 语义反转操作数：A < B → B > A。
     */
    private static List<String> normalizeCmp(List<String> toks) {
        for (int i = 0; i < toks.size(); i++) {
            String t = toks.get(i);
            if (t.equals("<") || t.equals("<=")) {
                if (i - 1 >= 0 && i + 1 < toks.size()) {
                    List<String> out = new ArrayList<>(toks);
                    out.set(i - 1, toks.get(i + 1));
                    out.set(i + 1, toks.get(i - 1));
                    out.set(i, t.equals("<") ? ">" : ">=");
                    return out;
                }
            }
        }
        return toks;
    }

    private static List<String> splitArgs(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQ = false;
        char last = 0;
        for (char c : s.toCharArray()) {
            if (c == '\'' && last != '\\') { inQ = !inQ; cur.append(c); }
            else if (c == ' ' || c == '\t') {
                if (!inQ) {
                    if (cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
                } else cur.append(c);
            } else cur.append(c);
            last = c;
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private static void write16(java.io.ByteArrayOutputStream o, int v) {
        o.write(v & 0xFF); o.write((v >> 8) & 0xFF);
    }
    private static void write32(java.io.ByteArrayOutputStream o, int v) {
        o.write(v & 0xFF); o.write((v >> 8) & 0xFF); o.write((v >> 16) & 0xFF); o.write((v >> 24) & 0xFF);
    }

    static String extractScriptName(String src) {
        java.util.regex.Matcher m = Pattern.compile("script_name\\s+[\"']([A-Za-z0-9_]{1,7})[\"']",
                Pattern.CASE_INSENSITIVE).matcher(src);
        return m.find() ? m.group(1) : "SCRIPT";
    }
}
