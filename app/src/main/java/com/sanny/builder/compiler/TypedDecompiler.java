package com.sanny.builder.compiler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * GTA III/VC/SA Mobile 类型化参数反编译器。
 * 规则（从 tpmenu.csi 逆向确认）：
 *  - 主代码偏移 0 = 文件偏移 11（无 SA 式 .cs 头）
 *  - jump/jump_if_false 的 p 参数 = int32 = -(目标绝对偏移 = 11 + 主代码偏移)
 */
public final class TypedDecompiler {

    public static String decompile(byte[] code, OpcodeTable table,
                                   Map<Integer, String> globalsById, StringBuilder err) {
        // 第一遍：收集跳转目标（p 参数绝对值 = 目标绝对偏移）
        TreeSet<Integer> targets = new TreeSet<>();
        int pos = 0;
        while (pos + 2 <= code.length) {
            int op = (code[pos] & 0xFF) | ((code[pos + 1] & 0xFF) << 8);
            int base = op & 0x7FFF;
            OpcodeTable.OpcodeDef def = table.get(base);
            pos += 2;
            if (def == null) { continue; }
            java.util.List<OpcodeTable.Placeholder> ps1 = new java.util.ArrayList<>(def.params);
            ps1.sort(java.util.Comparator.comparingInt(a -> a.index));
            for (OpcodeTable.Placeholder ph : ps1) {
                if (ph.type == 'p') {
                    int v = readInt32(code, pos + 1); // 跳过类型字节
                    int tAbs = (int) -((long) v);
                    if (v < 0) targets.add(tAbs - 11); // 转主代码相对偏移
                }
                pos += typedWidth(code, pos);
            }
        }
        // 第二遍：输出
        StringBuilder out = new StringBuilder();
        pos = 0;
        while (pos + 2 <= code.length) {
            int ip = pos;
            if (targets.contains(ip)) {
                out.append(String.format(":L_%08X\n", ip));
            }
            int op = (code[pos] & 0xFF) | ((code[pos + 1] & 0xFF) << 8);
            boolean neg = (op & 0x8000) != 0;
            int base = op & 0x7FFF;
            OpcodeTable.OpcodeDef def = table.get(base);
            if (def == null) {
                out.append(String.format("%04X: ; unknown opcode\n", op));
                pos += 2;
                continue;
            }
            pos += 2;
            StringBuilder fmt = new StringBuilder();
            fmt.append(String.format("%04X: ", op));
            // 解码按字节流顺序（index 升序），再按模板文本顺序拼接
            java.util.List<OpcodeTable.Placeholder> psByIndex = new java.util.ArrayList<>(def.params);
            psByIndex.sort(java.util.Comparator.comparingInt(a -> a.index));
            String[] decVals = new String[def.params.size()];
            int argPos = pos;
            for (OpcodeTable.Placeholder ph : psByIndex) {
                decVals[ph.index - 1] = decodeParam(ph, code, argPos, globalsById);
                argPos += typedWidth(code, argPos);
            }
            pos = argPos;
            if (neg && def.params.size() == 1 && def.params.get(0).type == 'p') {
                // negated jump(): 目标仍按绝对值
            }
            int prev = 0;
            for (OpcodeTable.Placeholder ph : def.params) {
                if (ph.fmtStart >= prev) fmt.append(def.format, prev, ph.fmtStart);
                fmt.append(decVals[ph.index - 1]);
                prev = ph.fmtEnd;
            }
            if (prev < def.format.length()) fmt.append(def.format, prev, def.format.length());
            appendSys(neg, fmt);
            out.append(fmt).append('\n');
        }
        if (targets.contains(pos)) {
            out.append(String.format(":L_%08X\n", pos));
        }
        return out.toString();
    }

    private static void appendSys(boolean neg, StringBuilder fmt) {
        if (neg) {
            int idx = fmt.indexOf(": ");
            if (idx >= 0) fmt.insert(idx + 2, "not ");
        }
    }

    /** 参数解码（argPos 指向类型字节） */
    private static String decodeParam(OpcodeTable.Placeholder ph, byte[] code, int argPos,
                                      Map<Integer, String> globalsById) {
        if (argPos >= code.length) return "0";
        int t = code[argPos] & 0xFF;
        if (t == 0) return "0"; // 变长参数结束
        if (TypedDecoder.isIstring8(t)) {
            StringBuilder sb = new StringBuilder();
            sb.append((char) t);
            for (int i = 1; i < 8 && argPos + i < code.length; i++) {
                char c = (char) (code[argPos + i] & 0xFF);
                if (c == 0) break;
                sb.append(c);
            }
            return "'" + sb + "'";
        }
        if (TypedDecoder.isVlstring(t)) {
            int n = code[argPos + 1] & 0xFF;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n && argPos + 2 + i < code.length; i++) {
                sb.append((char) (code[argPos + 2 + i] & 0xFF));
            }
            return "'" + sb + "'";
        }
        switch (t) {
            case TypedDecoder.T_INT32: {
                int v = readInt32(code, argPos + 1);
                if (ph.type == 'p') return "@L_" + String.format("%08X", -((long) v) - 11); // 相对主代码偏移（与 :L_ 标签一致）
                return Integer.toString(v);
            }
            case TypedDecoder.T_VAR: {
                int v = (code[argPos + 1] & 0xFF) | ((code[argPos + 2] & 0xFF) << 8);
                int idx = v / 4;
                String name = globalsById == null ? null : globalsById.get(idx);
                return name != null ? ("$" + name) : ("$" + idx);
            }
            case TypedDecoder.T_LVAR:
                return (code[argPos + 1] & 0xFF | ((code[argPos + 2] & 0xFF) << 8)) + "@";
            case TypedDecoder.T_INT8:
                return Integer.toString((byte) code[argPos + 1]);
            case TypedDecoder.T_INT16:
                return Integer.toString((short) ((code[argPos + 1] & 0xFF) | ((code[argPos + 2] & 0xFF) << 8)));
            case TypedDecoder.T_FLOAT32: {
                int bits = (code[argPos + 1] & 0xFF) | ((code[argPos + 2] & 0xFF) << 8)
                        | ((code[argPos + 3] & 0xFF) << 16) | ((code[argPos + 4] & 0xFF) << 24);
                float f = Float.intBitsToFloat(bits);
                return Float.toString(f);
            }
            case TypedDecoder.T_STRING8: {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < 9 && argPos + i < code.length; i++) {
                    char c = (char) (code[argPos + i] & 0xFF);
                    if (c == 0) break;
                    sb.append(c);
                }
                return "'" + sb + "'";
            }
            case TypedDecoder.T_VAR_ARRAY: case TypedDecoder.T_LVAR_ARRAY: {
                int tArr = t == TypedDecoder.T_VAR_ARRAY ? 0 : 1;
                int off = (code[argPos + 1] & 0xFF) | ((code[argPos + 2] & 0xFF) << 8);
                int idx = (code[argPos + 3] & 0xFF) | ((code[argPos + 4] & 0xFF) << 8);
                int sz = code[argPos + 5] & 0xFF;
                String base = tArr == 0 ? ("$" + (off / 4)) : (off + "@");
                return base + "(" + idx + ")"; // size 忽略（0-255 极少出现）
            }
            default:
                return Integer.toString(readInt32(code, argPos + 1));
        }
    }

    /** 从类型字节读取参数总宽（含类型字节） */
    private static int typedWidth(byte[] code, int pos) {
        if (pos >= code.length) return 1;
        int t = code[pos] & 0xFF;
        if (t == 0) return 1;
        if (TypedDecoder.isIstring8(t)) return 8;
        if (TypedDecoder.isVlstring(t)) {
            int n = pos + 1 < code.length ? (code[pos + 1] & 0xFF) : 0;
            return 2 + n;
        }
        return 1 + TypedDecoder.width(t);
    }

    private static int readInt32(byte[] d, int off) {
        return (d[off] & 0xFF) | ((d[off + 1] & 0xFF) << 8) | ((d[off + 2] & 0xFF) << 16) | (d[off + 3] << 24);
    }
}
