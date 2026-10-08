package com.sanny.builder.compiler;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * CLEO Builder 编译器 —— Linux CLI 入口（DSH 插件后端）
 *
 * 用法:
 *   java -jar cleo-compiler.jar --mode <gtasa|gtasa_mobile> [--data <dir>] <input.cs> <output>
 *   或: java -jar cleo-compiler.jar --mode <mode> --stdin --out <output>
 *   --list 列出支持的模式与 opcode 数
 *
 * 退出码: 0 成功（打印 OK <bytes> <path>）；1 编译失败（stderr 含行号）；2 参数错误
 */
public class Main {

    public static void main(String[] args) throws Exception {
        String mode = "gtasa";
        String dataDir = "data";
        String input = null;
        String output = null;
        boolean stdin = false;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--mode": mode = args[++i]; break;
                case "--data": dataDir = args[++i]; break;
                case "--stdin": stdin = true; break;
                case "--out": output = args[++i]; break;
                case "--list": listModes(dataDir); return;
                default:
                    if (input == null) input = a;
                    else if (output == null) output = a;
                    else { err("多余参数: " + a); System.exit(2); }
            }
        }
        if (output == null) { err("缺少输出路径（--out <file> 或 <input> <output>）"); System.exit(2); }

        GameMode gm = resolveMode(mode);
        if (gm == null) { err("未知模式: " + mode + "（可用: gtasa, gtasa_mobile）"); System.exit(2); }

        String source;
        if (stdin) {
            byte[] buf = System.in.readAllBytes();
            source = new String(buf, StandardCharsets.UTF_8);
        } else {
            if (input == null) { err("缺少输入文件"); System.exit(2); }
            source = new String(Files.readAllBytes(Paths.get(input)), StandardCharsets.UTF_8);
        }

        Result r = compile(gm, new File(dataDir), source);
        if (r.error != null) {
            System.err.println(r.error);
            System.exit(1);
        }
        Files.write(Paths.get(output), r.bytes);
        System.out.println("OK " + r.bytes.length + " " + output);
    }

    public static class Result {
        public byte[] bytes;
        public String error;
    }

    /** 编译入口（插件可复用）：mode + 数据目录 + 源码 -> 字节码或错误文本 */
    public static Result compile(GameMode gm, File dataRoot, String source) {
        Result r = new Result();
        try {
            OpcodeTable table = new OpcodeTable();
            for (String ini : gm.iniFiles) {
                File f = new File(dataRoot, gm.dataDir + "/" + ini);
                if (!f.exists()) continue;
                table.load(new String(Files.readAllBytes(f.toPath()), "UTF-8"));
            }
            File root = new File(dataRoot, gm.dataDir);
            table.loadConds(read(root, "opcode_conds.json", dataRoot));
            table.loadNames(read(root, "opcode_names.json", dataRoot));
            table.loadTypes(read(root, "opcode_types.json", dataRoot));

            Map<String, Integer> globals = loadGlobals(new File(root, "CustomVariables.ini"));
            if (globals == null) globals = new HashMap<>();

            StringBuilder err = new StringBuilder();
            byte[] out = TypedCompiler.compile(source, table, globals, err);
            if (out == null) {
                r.error = "编译失败\n" + err.toString();
                return r;
            }
            r.bytes = out;
            return r;
        } catch (Throwable t) {
            r.error = "内部错误: " + t;
            return r;
        }
    }

    private static String read(File modeDir, String name, File dataRoot) throws IOException {
        File inMode = new File(modeDir, name);
        if (inMode.exists()) return new String(Files.readAllBytes(inMode.toPath()), "UTF-8");
        File inRoot = new File(dataRoot, name);
        if (inRoot.exists()) return new String(Files.readAllBytes(inRoot.toPath()), "UTF-8");
        return "{}";
    }

    /** 解析 CustomVariables.ini：全局名 -> 编号 */
    public static Map<String, Integer> loadGlobals(File f) {
        if (f == null || !f.exists()) return null;
        try {
            Map<String, Integer> map = new LinkedHashMap<>();
            for (String raw : new String(Files.readAllBytes(f.toPath()), "UTF-8").split("\n")) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith(";")) continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                try {
                    map.put(line.substring(eq + 1).trim().toUpperCase(Locale.ROOT), Integer.parseInt(line.substring(0, eq).trim()));
                } catch (NumberFormatException ignored) {}
            }
            return map;
        } catch (IOException e) {
            return null;
        }
    }

    private static GameMode resolveMode(String m) {
        switch (m.toLowerCase(Locale.ROOT)) {
            case "gtasa": case "sa": return GameMode.GTASA;
            case "gtasa_mobile": case "sa_mobile": case "mobile": return GameMode.SA_MOBILE;
            default: return null;
        }
    }

    private static void listModes(String dataDir) throws Exception {
        for (GameMode gm : new GameMode[]{GameMode.GTASA, GameMode.SA_MOBILE}) {
            OpcodeTable t = new OpcodeTable();
            for (String ini : gm.iniFiles) {
                File f = new File(dataDir, gm.dataDir + "/" + ini);
                if (f.exists()) t.load(new String(Files.readAllBytes(f.toPath()), "UTF-8"));
            }
            System.out.println(gm.id + " (" + gm.label + "): " + t.size() + " opcodes");
        }
    }

    private static void err(String s) { System.err.println(s); }
}
