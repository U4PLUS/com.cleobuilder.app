package com.sanny.builder.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析 SASCM.INI 风格的 opcode 表。
 *
 * 格式：HEX=参数个数,名称与格式   （格式里 %1d% %2p% %3s% … 为参数占位符）
 * 例如：0001=1,wait %1d% ms
 */
public class OpcodeTable {

    /** 一个参数占位符：%Nx% */
    public static class Placeholder {
        public final int index;   // 1-based 参数号
        public final char type;   // d/p/o/m/g/x/k/s/i/f …
        public final int fmtStart; // 在 format 中的起点（含 %）
        public final int fmtEnd;   // 在 format 中的终点（不含尾部 %）

        Placeholder(int index, char type, int start, int end) {
            this.index = index;
            this.type = type;
            this.fmtStart = start;
            this.fmtEnd = end;
        }

        public int width(GameMode.Encoding enc) {
            if (enc == GameMode.Encoding.SA) return widthSa(type);
            return widthTyped(type);
        }

        static int widthSa(char t) {
            switch (t) {
                case 'k': return 128;
                case 's': return 8;
                case 'r': return 8;   // r% = 8-byte string (allowed in some tables)
                default:  return 4;   // d/p/o/m/g/x/i/f
            }
        }

        static int widthTyped(char t) {
            return -1; // 类型化格式由解码器按类型字节处理
        }
    }

    public static class OpcodeDef {
        public final int id;
        public final int argCount;
        public final String format;
        public final List<Placeholder> params = new ArrayList<>();
        /** 去掉注释（; 之后）与占位符的“可读名称”部分 */
        public final String name;

        OpcodeDef(int id, int argCount, String format) {
            this.id = id;
            this.argCount = argCount;
            this.format = format.trim();
            scanPlaceholders(this.format);
            // 名称 = 第一个占位符之前的文本（trim）；无占位符则整段为名
            String n;
            if (params.isEmpty()) {
                n = this.format.trim();
            } else {
                n = this.format.substring(0, params.get(0).fmtStart).trim();
            }
            this.name = n.replaceAll("\\s+", " ");
        }

        private void scanPlaceholders(String fmt) {
            int i = 0;
            while (i < fmt.length()) {
                int pct = fmt.indexOf('%', i);
                if (pct < 0) break;
                // %Nx%
                int j = pct + 1;
                int idx = 0;
                while (j < fmt.length() && Character.isDigit(fmt.charAt(j))) {
                    idx = idx * 10 + (fmt.charAt(j) - '0');
                    j++;
                }
                if (idx == 0 || j >= fmt.length() || fmt.charAt(j) == '%') {
                    i = pct + 1;
                    continue;
                }
                char t = fmt.charAt(j);
                int end = fmt.indexOf('%', j + 1);
                if (end < 0) break;
                params.add(new Placeholder(idx, t, pct, end + 1)); // 含尾部 %
                i = end + 1;
            }
        }
    }

    private final Map<Integer, OpcodeDef> byId = new HashMap<>();
    private final Map<String, Integer> byNameLower = new HashMap<>();

    public void clear() { byId.clear(); byNameLower.clear(); }

    public void load(String iniContent) {
        String section = "";
        for (String raw : iniContent.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith(";")) continue;
            if (line.startsWith("[")) {
                section = line.substring(1, line.indexOf(']')).trim();
                continue;
            }
            // HEX=count,name
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            String hexPart = line.substring(0, eq).trim();
            String rest = line.substring(eq + 1).trim();
            int comma = rest.indexOf(',');
            if (comma < 0) continue;
            int id;
            try {
                id = Integer.parseInt(hexPart, 16);
            } catch (NumberFormatException e) {
                continue; // 非十六进制键（DATE= 等）
            }
            int count;
            try {
                count = Integer.parseInt(rest.substring(0, comma).trim());
            } catch (NumberFormatException e) {
                continue;
            }
            String format = rest.substring(comma + 1).trim();
            // 去掉格式末尾的注释（; 后）
            int semi = format.indexOf(';');
            if (semi >= 0) format = format.substring(0, semi);
            OpcodeDef def = new OpcodeDef(id, count, format);
            byId.put(id, def);
            byNameLower.put(def.name.toLowerCase(), id);
        }
    }

    public int size() { return byId.size(); }

    public java.util.Collection<OpcodeDef> allDefs() { return byId.values(); }

    public OpcodeDef get(int id) { return byId.get(id); }
    public OpcodeDef getByHex(String hex) {
        try { return byId.get(Integer.parseInt(hex, 16)); } catch (NumberFormatException e) { return null; }
    }
    public Integer idByName(String name) {
        return byNameLower.get(name.toLowerCase(java.util.Locale.ROOT));
    }
    private int jumpIfFalseId = -1;

    /** 条件假跳转 opcode：SA 系 0x004D jump_if_false；VCS 系 0x0022 goto_if_false */
    public int jumpIfFalseId() {
        if (jumpIfFalseId < 0) {
            OpcodeDef d = getByName("jump_if_false");
            if (d == null) d = getByName("goto_if_false");
            jumpIfFalseId = d != null ? d.id : 0x4D;
        }
        return jumpIfFalseId;
    }

    public OpcodeDef getByName(String name) {
        Integer id = byNameLower.get(name.toLowerCase());
        return id == null ? null : byId.get(id);
    }

    /** 加载官方 opcode 名表（JSON {"HEX":"NAME"}），优先于 ini 模板名 */
    public void loadNames(String jsonContent) {
        // 单层 JSON 手写解析（无外部依赖）
        int i = jsonContent.indexOf('{');
        int j = jsonContent.lastIndexOf('}');
        if (i < 0 || j <= i) return;
        String inner = jsonContent.substring(i + 1, j);
        for (String part : inner.split(",")) {
            String kv = part.trim();
            int c = kv.indexOf(':');
            if (c <= 0) continue;
            String k = kv.substring(0, c).trim().replace("\"", "");
            String v = kv.substring(c + 1).trim().replace("\"", "");
            try {
                int id = Integer.parseInt(k, 16);
                if (byId.containsKey(id) && !v.isEmpty()) {
                    byNameLower.put(v.toLowerCase(java.util.Locale.ROOT), id);
                }
            } catch (NumberFormatException ignored) { }
        }
    }

    // 参数类型表：id -> [[type, source], ...]（来自官方 sa.json，用于无名称模板匹配）
    private final java.util.Map<Integer, String[][]> typesById = new java.util.HashMap<>();

    public void loadTypes(String jsonContent) {
        int i = jsonContent.indexOf('{');
        int j = jsonContent.lastIndexOf('}');
        if (i < 0 || j <= i) return;
        parseTypesManual(jsonContent.substring(i + 1, j));
    }

    private void parseTypesManual(String inner) {
        int pos = 0;
        while (pos < inner.length()) {
            int c1 = inner.indexOf('"', pos);
            if (c1 < 0) break;
            int c2 = inner.indexOf('"', c1 + 1);
            if (c2 < 0) break;
            String hex = inner.substring(c1 + 1, c2);
            int arr = inner.indexOf('[', c2);
            if (arr < 0) break;
            int depth = 0, end = arr;
            for (; end < inner.length(); end++) {
                char ch = inner.charAt(end);
                if (ch == '[') depth++;
                else if (ch == ']') { depth--; if (depth == 0) break; }
            }
            if (end >= inner.length()) break;
            String arrBody = inner.substring(arr + 1, end);
            java.util.List<String[]> items = new java.util.ArrayList<>();
            int ap = 0;
            while (ap < arrBody.length()) {
                int b1 = arrBody.indexOf('[', ap);
                if (b1 < 0) break;
                int b2 = arrBody.indexOf(']', b1);
                if (b2 < 0) break;
                String item = arrBody.substring(b1 + 1, b2);
                java.util.List<String> fs = new java.util.ArrayList<>();
                for (String f : item.split(",")) {
                    String v = f.trim();
                    if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
                        v = v.substring(1, v.length() - 1);
                    }
                    fs.add(v);
                }
                items.add(fs.toArray(new String[0]));
                ap = b2 + 1;
            }
            if (items.size() > 0) {
                typesById.put(Integer.parseInt(hex, 16), items.toArray(new String[0][]));
            }
            pos = end + 1;
        }
    }

    /** 返回 opcode 的 input 类型表；null 表示未知（宽松匹配） */
    public String[][] typesOf(int id) { return typesById.get(id); }

    // 条件型 opcode 标志（sa.json attrs.is_condition）
    private final java.util.Set<Integer> condIds = new java.util.HashSet<>();

    public void loadConds(String jsonContent) {
        int i = jsonContent.indexOf('{');
        int j = jsonContent.lastIndexOf('}');
        if (i < 0 || j <= i) return;
        String inner = jsonContent.substring(i + 1, j);
        for (String part : inner.split(",")) {
            String kv = part.trim();
            int c = kv.indexOf(':');
            if (c <= 0) continue;
            String k = kv.substring(0, c).trim().replace("\"", "");
            try { condIds.add(Integer.parseInt(k, 16)); } catch (NumberFormatException ignored) { }
        }
    }

    /** 该 opcode 是否条件型（if 块内吸收）；未知返回 false */
    public boolean isCondition(int id) { return condIds.contains(id); }
}
