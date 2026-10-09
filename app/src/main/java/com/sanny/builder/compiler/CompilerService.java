package com.sanny.builder.compiler;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 编译/反编译服务：加载当前模式的 opcode 表与全局变量名表，提供
 * 文本 <-> .cs 二进制 转换。
 */
public class CompilerService {

    private final OpcodeTable table = new OpcodeTable();
    private final Map<String, Integer> globalsByName = new HashMap<>();
    private final Map<Integer, String> globalsById = new HashMap<>();

    /** 读取文件全部字节（兼容 API 21，不用 java.nio.file） */
    private static byte[] readAll(File f) throws java.io.IOException {
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(Math.max(64, (int) f.length()));
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    private GameMode mode = GameMode.GTASA;
    private String dataDir; // filesDir/sanny/data
    private String lastDataWarn = ""; // 最近一次表加载校验摘要
    private final java.util.List<String> lastLoaded = new java.util.ArrayList<>(); // 最近实际加载的表
    private File lastIniDir; // 最近表来源目录（供 UI 显示路径）

    /** 最近一次 init 实际加载的表文件名（按加载顺序） */
    public java.util.List<String> lastLoadedTables() { return new java.util.ArrayList<>(lastLoaded); }
    public String lastIniDirPath() { return lastIniDir == null ? "" : lastIniDir.getAbsolutePath(); }

    /** 兼容旧调用：只从内置数据目录加载 */
    public void init(GameMode m, String dataRoot) {
        init(m, dataRoot, null, null, null);
    }

    /**
     * 加载当前模式的 opcode 表。
     * @param dataRoot   内置数据目录（filesDir/sanny/data，兜底）
     * @param opcodeRoot 用户表目录（cleo/CLEO_Builder/opcode/；存在 .ini 时优先）
     * @param enabled    启用的表文件名列表（null/空 = 全部内置表）；自定义表最后加载（覆盖优先级最高）
     * @param warn       非空时写入合并校验报告
     */
    public void init(GameMode m, String dataRoot, File opcodeRoot, java.util.List<String> enabled, StringBuilder warn) {
        this.mode = m;
        this.dataDir = dataRoot;
        table.clear();
        globalsByName.clear();
        globalsById.clear();
        lastDataWarn = "";
        StringBuilder w = new StringBuilder();

        // 表来源目录：用户表目录优先（存在 .ini 时），否则内置
        File dir = null;
        if (opcodeRoot != null) {
            File d = new File(opcodeRoot, m.dataDir);
            File[] ini = d == null ? null : d.listFiles((x, s) -> s.endsWith(".ini"));
            if (ini != null && ini.length > 0) dir = d;
        }
        if (dir == null) dir = new File(dataRoot, m.dataDir);
        lastIniDir = dir;
        lastLoaded.clear();

        // 加载队列：内置表按 iniFiles 顺序（被启用者），自定义表（非内置名）按字母序最后加载
        java.util.List<String> loadOrder = new java.util.ArrayList<>();
        if (enabled != null && !enabled.isEmpty()) {
            for (String ini : m.iniFiles) {
                if (enabled.contains(ini) && new File(dir, ini).isFile()) loadOrder.add(ini);
            }
            java.util.List<String> custom = new java.util.ArrayList<>();
            for (String ini : enabled) {
                if (!m.iniFiles.contains(ini) && new File(dir, ini).isFile()) custom.add(ini);
            }
            java.util.Collections.sort(custom);
            loadOrder.addAll(custom);
        } else {
            for (String ini : m.iniFiles) {
                if (new File(dir, ini).isFile()) loadOrder.add(ini);
            }
        }
        for (String ini : loadOrder) {
            File f = new File(dir, ini);
            if (f.isFile()) {
                try {
                    int bad = table.load(new String(readAll(f), StandardCharsets.UTF_8), w);
                    lastLoaded.add(ini);
                    if (bad > 0) w.append("「").append(ini).append("」跳过 ").append(bad).append(" 行非法定义; ");
                } catch (Exception ignored) { }
            }
        }
        // 官方 opcode 名表（SA 系；其它模式名从 ini 模板）
        File types = new File(dataRoot, "opcode_types.json");
        if (types.isFile()) {
            try {
                table.loadTypes(new String(readAll(types), StandardCharsets.UTF_8));
            } catch (Exception ignored) { }
        }
        File conds = new File(dataRoot, "opcode_conds.json");
        if (conds.isFile()) {
            try { table.loadConds(new String(readAll(conds), StandardCharsets.UTF_8)); } catch (Exception ignored) { }
        }
        File names = new File(dataRoot, "opcode_names.json");
        if (names.isFile()) {
            try {
                table.loadNames(new String(readAll(names), StandardCharsets.UTF_8));
            } catch (Exception ignored) { }
        }
        // 全局变量命名表 CustomVariables.ini：index=NAME
        File cv = new File(dir, "CustomVariables.ini");
        if (cv.isFile()) {
            try {
                for (String raw : new String(readAll(cv), StandardCharsets.UTF_8).split("\n")) {
                    String line = raw.trim();
                    if (line.isEmpty() || line.startsWith(";") || line.startsWith("//")) continue;
                    int eq = line.indexOf('=');
                    if (eq <= 0) continue;
                    try {
                        int idx = Integer.parseInt(line.substring(0, eq).trim());
                        String name = line.substring(eq + 1).trim();
                        if (!name.isEmpty()) {
                            globalsByName.put(name.toUpperCase(java.util.Locale.ROOT), idx);
                            globalsById.put(idx, name);
                        }
                    } catch (NumberFormatException ignored) { }
                }
            } catch (Exception ignored) { }
        }
        if (warn != null && w.length() > 0) warn.append(w);
        lastDataWarn = w.toString();
    }

    public String lastDataWarn() { return lastDataWarn; }

    public GameMode mode() { return mode; }
    public int opcodeCount() { return table.size(); }

    /** 已知指令名集合（小写）——语法高亮用 */
    private java.util.Set<String> opcodeNamesLower;

    public synchronized java.util.Set<String> opcodeNames() {
        if (opcodeNamesLower == null) {
            java.util.Set<String> s = new java.util.HashSet<>();
            for (OpcodeTable.OpcodeDef d : table.allDefs()) {
                if (d.name != null && !d.name.isEmpty()) s.add(d.name.toLowerCase(java.util.Locale.ROOT));
            }
            opcodeNamesLower = s;
        }
        return opcodeNamesLower;
    }

    /**
     * 编译脚本文本，返回 .cs 文件字节；失败返回 null 并在 err 中给出原因。
     * 使用 TypedCompiler（基于官方 opcode 模板 + 类型/条件表，字节级兼容）。
     */
    public byte[] compileToCs(String text, StringBuilder err) {
        if (text != null && text.startsWith("\uFEFF")) text = text.substring(1); // 容错 UTF-8 BOM
        if (text == null || text.trim().isEmpty()) { err.append("输入为空：没有可编译的内容"); return null; }
        byte[] code = TypedCompiler.compile(text, table, globalsByName, err);
        if (code == null) return null;
        return CsFormat.pack(code);
    }

    /** 只编译主代码段（不带任何文件头），供 .csi 容器打包 */
    public byte[] compileMain(String text, StringBuilder err) {
        if (text != null && text.startsWith("\uFEFF")) text = text.substring(1);
        if (text == null || text.trim().isEmpty()) { err.append("输入为空：没有可编译的内容"); return null; }
        return TypedCompiler.compile(text, table, globalsByName, err);
    }

    /** 按源码头部 {$CLEO .csi} 判断是否应输出 SA Mobile .csi 容器（而非 .cs） */
    public static boolean wantsCsi(String text) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{\\$CLEO[^}]*\\}").matcher(text);
        if (m.find()) return m.group().contains(".csi");
        return false;
    }

    /** 按源码头部 {$CLEO .csa} 判断是否输出 .csa 扩展名（内容同 .cs） */
    public static boolean wantsCsa(String text) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{\\$CLEO[^}]*\\}").matcher(text);
        if (m.find()) return m.group().contains(".csa");
        return false;
    }

    /** 从 script_name "XXX" 提取脚本名（.csi 容器需要） */
    public static String scriptNameOf(String text) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)script_name\\s+\"([^\"]+)\"")
                .matcher(text);
        if (m.find()) return m.group(1);
        java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("(?i)script_name\\s+([A-Za-z0-9_]+)").matcher(text);
        return m2.find() ? m2.group(1) : "SCRIPT";
    }

    /** 反编译 .cs/.csi 字节，返回 CLEO 源码文本 */
    public String decompileCs(byte[] cs, StringBuilder err) {
        // 1) SA Mobile .csi 容器：内嵌源码直接用；否则反编译主代码
        SaMobileFile f = SaMobileFile.parse(cs);
        if (f != null) {
            if (f.embeddedSource != null) return f.embeddedSource;
            return TypedDecompiler.decompile(f.mainCode, table, globalsById, err);
        }
        // 2) CLEO .cs（SCM main 段）或旧自定义头
        byte[] code = CsFormat.unpack(cs, err);
        if (code == null) return null;
        return TypedDecompiler.decompile(code, table, globalsById, err);
    }

    /** 打包为 SA Mobile .csi 容器（含源码段，可再导入） */
    public byte[] packCsi(byte[] mainCode, String scriptName, String source) {
        return SaMobileFile.pack(mainCode, scriptName, source);
    }

    /** 反编译任意 main 代码段（无 .cs 头） */
    public String decompileMain(byte[] code, StringBuilder err) {
        return TypedDecompiler.decompile(code, table, globalsById, err);
    }
}
