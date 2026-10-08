package com.sanny.builder.compiler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CLEO Builder 1.0 —— GTASA / GTASA Mobile 脚本编译器（单 jar 自包含）
 *
 * 用法（人也友好）:
 *   java -jar cleobuilder.jar <输入.cs> <输出> [模式]
 *   java -jar cleobuilder.jar -i <输入.cs> -o <输出> [-m gtasa|gtasa_mobile]
 *   java -jar cleobuilder.jar -h           帮助
 *   java -jar cleobuilder.jar --info       编译器信息
 *
 * 模式: gtasa（PC，默认） / gtasa_mobile（Android）
 * 依赖: JRE 17+（全部 CLEO 数据内嵌于 jar，无需其他文件）
 */
public class Main {

    static final String VERSION = "1.0.0";

    // 强制 UTF-8 输出：不依赖系统默认编码（保证中文帮助/错误在任何终端可读）
    static {
        try {
            System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
            System.setErr(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err), true, StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    public static void main(String[] args) throws Exception {
        String input = null, output = null, mode = "gtasa";
        List<String> pos = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "-h": case "--help": case "-help": printHelp(); return;
                case "--info": case "-V": case "--version": printInfo(); return;
                case "-i": case "--input": input = args[++i]; break;
                case "-o": case "--output": output = args[++i]; break;
                case "-m": case "--mode": mode = args[++i]; break;
                default:
                    if (a.startsWith("-") && a.length() > 1) { err("未知参数: " + a + "（用 -h 查看用法）"); System.exit(2); }
                    pos.add(a);
            }
        }
        if (pos.size() > 0 && input == null) input = pos.get(0);
        if (pos.size() > 1 && output == null) output = pos.get(1);
        if (pos.size() > 2) mode = pos.get(2);
        if (pos.size() > 3) { err("参数过多（用 -h 查看用法）"); System.exit(2); }

        if (input == null || output == null) { printHelp(); System.exit(2); }

        GameMode gm = resolveMode(mode);
        if (gm == null) { err("未知模式: " + mode + "（可用: gtasa, gtasa_mobile）"); System.exit(2); }

        String source = new String(Files.readAllBytes(Paths.get(input)), StandardCharsets.UTF_8);
        Result r = compile(gm, source);
        if (r.error != null) {
            System.err.println("编译失败:");
            System.err.println(r.error);
            System.exit(1);
        }
        try {
            Files.write(Paths.get(output), r.bytes);
        } catch (IOException e) {
            System.err.println("无法写入输出文件 " + output + ": " + e.getMessage());
            System.exit(1);
        }
        System.out.println("编译成功: " + output + " (" + r.bytes.length + " 字节)");
    }

    // ---------- 编译核心 ----------

    public static class Result {
        public byte[] bytes;
        public String error;
    }

    public static Result compile(GameMode gm, String source) {
        Result r = new Result();
        try {
            OpcodeTable table = new OpcodeTable();
            for (String ini : gm.iniFiles) {
                String c = loadRes("/data/" + gm.dataDir + "/" + ini);
                if (c != null) table.load(c);
            }
            String conds = loadRes("/data/opcode_conds.json");
            if (conds == null) conds = "{}";
            table.loadConds(conds);
            String names = loadRes("/data/opcode_names.json");
            if (names != null) table.loadNames(names);
            String types = loadRes("/data/opcode_types.json");
            if (types != null) table.loadTypes(types);

            Map<String, Integer> globals = loadGlobals("/data/" + gm.dataDir + "/CustomVariables.ini");
            if (globals == null) globals = new HashMap<>();

            StringBuilder err = new StringBuilder();
            byte[] out = TypedCompiler.compile(source, table, globals, err);
            if (out == null) { r.error = err.toString(); return r; }
            r.bytes = out;
            return r;
        } catch (Throwable t) {
            r.error = "内部错误: " + t;
            return r;
        }
    }

    /** 从 jar 内嵌资源读取文本；缺失返回 null */
    private static String loadRes(String path) {
        try (InputStream in = Main.class.getResourceAsStream(path)) {
            if (in == null) return null;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /** 解析 CustomVariables.ini（资源）：全局名 -> 编号 */
    private static Map<String, Integer> loadGlobals(String resPath) {
        String c = loadRes(resPath);
        if (c == null) return null;
        Map<String, Integer> map = new LinkedHashMap<>();
        for (String raw : c.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith(";")) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            try {
                map.put(line.substring(eq + 1).trim().toUpperCase(Locale.ROOT), Integer.parseInt(line.substring(0, eq).trim()));
            } catch (NumberFormatException ignored) {}
        }
        return map;
    }

    private static GameMode resolveMode(String m) {
        switch (m.toLowerCase(Locale.ROOT)) {
            case "gtasa": case "sa": return GameMode.GTASA;
            case "gtasa_mobile": case "sa_mobile": case "mobile": case "m": return GameMode.SA_MOBILE;
            default: return null;
        }
    }

    private static int countOpcodes(GameMode gm) {
        OpcodeTable t = new OpcodeTable();
        for (String ini : gm.iniFiles) {
            String c = loadRes("/data/" + gm.dataDir + "/" + ini);
            if (c != null) t.load(c);
        }
        return t.size();
    }

    private static void printInfo() {
        System.out.println("CLEO Builder " + VERSION + " (GTASA / GTASA Mobile 编译器)");
        System.out.println("依赖: JRE 17+（无其他依赖；全部数据内嵌于本 jar）");
        System.out.println("模式:");
        System.out.println("  gtasa         GTA San Andreas（PC）    " + countOpcodes(GameMode.GTASA) + " opcode");
        System.out.println("  gtasa_mobile  GTASA Android（含触摸/九宫格） " + countOpcodes(GameMode.SA_MOBILE) + " opcode");
    }

    private static void printHelp() {
        System.out.println();
        System.out.println("CLEO Builder " + VERSION + " —— GTA SA / GTASA Mobile 脚本编译器");
        System.out.println();
        System.out.println("把 CLEO 脚本（.cs/.csi 源码）编译为游戏可执行的字节码。");
        System.out.println();
        System.out.println("用法:");
        System.out.println("  java -jar cleobuilder.jar <输入.cs> <输出文件> [模式]");
        System.out.println("  java -jar cleobuilder.jar -i <输入.cs> -o <输出文件> [-m 模式]");
        System.out.println();
        System.out.println("参数:");
        System.out.println("  -i, --input   输入文件（脚本源码，UTF-8）");
        System.out.println("  -o, --output  输出文件（编译产物字节码）");
        System.out.println("  -m, --mode    游戏模式: gtasa（默认）/ gtasa_mobile");
        System.out.println("  -h, --help    显示本帮助");
        System.out.println("      --info    显示编译器信息");
        System.out.println();
        System.out.println("示例:");
        System.out.println("  java -jar cleobuilder.jar myscript.cs myscript.cs.out");
        System.out.println("  java -jar cleobuilder.jar -i a.cs -o a.bin -m gtasa_mobile");
        System.out.println();
        System.out.println("依赖: JRE 17+（数据已内嵌，无其他文件）");
        System.out.println("详细说明与 CLEO 资料见同目录 README.txt 与 CLEO-KNOWLEDGE-BASE.md");
        System.out.println();
    }

    private static void err(String s) { System.err.println(s); }
}
