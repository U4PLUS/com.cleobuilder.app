package com.sanny.builder.compiler;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * GTA SA CLEO 脚本反编译器：字节码 -> 文本。
 */
public class SaDecompiler {

    /**
     * 反编译 main 代码段。
     * @param globalNames 全局变量索引 -> 名称（$2 -> PLAYER_CHAR），可为 null
     */
    public static String decompile(byte[] code, OpcodeTable table,
                                   Map<Integer,String> globalNames, StringBuilder err) {
        // 收集跳转目标
        TreeSet<Integer> jumpTargets = new TreeSet<>();
        List<int[]> paramValues = new ArrayList<>();   // {offset, intValue}
        // 扫描指令定位标签目标（p 参数）
        int pos = 0;
        while (pos + 2 <= code.length) {
            int op = (code[pos] & 0xFF) | ((code[pos + 1] & 0xFF) << 8);
            if (op == 0 && pos + 4 <= code.length && code[pos+2] == 0 && code[pos+3] == 0) break; // 段尾
            OpcodeTable.OpcodeDef def = table.get(op);
            if (def == null) {
                jumpTargets.add(pos);
                pos += 2;
                continue;
            }
            int start = pos;
            pos += 2;
            for (OpcodeTable.Placeholder ph : def.params) {
                if (ph.type == 'p' && pos + 4 <= code.length) {
                    int v = readInt(code, pos);
                    paramValues.add(new int[]{start, v});
                    jumpTargets.add(v);
                }
                pos += ph.width(GameMode.Encoding.SA);
            }
        }

        // 重新解码输出
        StringBuilder out = new StringBuilder();
        pos = 0;
        int line = 0;
        int guard = 0;
        while (pos + 2 <= code.length && guard++ < 100000) {
            int op = (code[pos] & 0xFF) | ((code[pos + 1] & 0xFF) << 8);
            if (op == 0 && pos + 4 <= code.length && code[pos+2] == 0 && code[pos+3] == 0) break;
            Integer nextTarget = jumpTargets.ceiling(pos);
            if (nextTarget != null && nextTarget == pos) {
                out.append(String.format(":%s\n", labelName(pos)));
            }
            OpcodeTable.OpcodeDef def = table.get(op);
            if (def == null) {
                out.append(String.format("%04X: ; unknown opcode\n", op));
                pos += 2;
                continue;
            }
            pos += 2; // opcode 自身
            StringBuilder fmt = new StringBuilder();
            fmt.append(String.format("%04X: ", op));
            // 用 format 填充参数
            int prev = 0;
            int pi = 0;
            for (OpcodeTable.Placeholder ph : def.params) {
                fmt.append(def.format, prev, ph.fmtStart);
                int v;
                if (ph.type == 's' || ph.type == 'k') {
                    v = -1;
                } else {
                    v = pos + 4 <= code.length ? readInt(code, pos) : 0;
                }
                String txt = decodeParam(ph.type, v, code, pos, globalNames);
                fmt.append(txt);
                prev = ph.fmtEnd;
                pos += ph.width(GameMode.Encoding.SA);
                pi++;
            }
            if (prev < def.format.length()) fmt.append(def.format, prev, def.format.length());
            if (guard == 100000) System.err.println("[GUARD] pos=" + pos + " op=" + String.format("%04X", op) + " line=" + line);
            // 整理多余空白
            out.append(fmt.toString().trim()).append('\n');
            line++;
        }
        return out.toString();
    }

    private static String labelName(int offset) {
        return String.format("L_%08X", offset);
    }

    private static int readInt(byte[] code, int pos) {
        return (code[pos] & 0xFF) | ((code[pos+1] & 0xFF) << 8) | ((code[pos+2] & 0xFF) << 16) | ((code[pos+3] & 0xFF) << 24);
    }

    private static String decodeParam(char type, int v, byte[] code, int pos, Map<Integer,String> globalNames) {
        switch (type) {
            case 'p':
                return "@" + labelName(v);
            case 's': {
                int len = Math.min(8, code.length - pos);
                byte[] b = Arrays.copyOfRange(code, pos, pos + len);
                return "\"" + new String(b, StandardCharsets.UTF_8).trim() + "\"";
            }
            case 'k': {
                int len = Math.min(128, code.length - pos);
                byte[] b = Arrays.copyOfRange(code, pos, pos + len);
                String s = new String(b, StandardCharsets.UTF_8);
                int z = s.indexOf('\0');
                return "\"" + (z >= 0 ? s.substring(0, z) : s.trim()) + "\"";
            }
            case 'f':
                return Float.toString(Float.intBitsToFloat(v));
            case 'i':
            case 'd':
            case 'o':
            case 'm':
            case 'x':
            case 'g':
            default:
                if (v >= 0x8000) {
                    int idx = v - 0x8000;
                    return idx + "@";
                }
                if (v >= 0) {
                    if (globalNames != null) {
                        String name = globalNames.get(v);
                        if (name != null) return "$" + name;
                    }
                    return "$" + v;
                }
                return Integer.toString(v);
        }
    }
}
