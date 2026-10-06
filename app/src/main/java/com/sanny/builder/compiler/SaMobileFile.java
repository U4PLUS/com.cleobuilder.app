package com.sanny.builder.compiler;

/**
 * GTA SA Mobile (.csi) CLEO 脚本容器：
 * [0..4)   u16 未知字段A(0x03A4), u16 未知字段B(0x070E)
 * [4..11)  script_name 7 字节
 * [11..)   主代码（类型化参数指令流）
 * 尾部: "FLAG\0" + u32 + "SRC\0" + u32源码长度 + 源码文本 + u32(FLAG偏移) + "__SBFTR\0"
 */
public final class SaMobileFile {

    public static final int HEADER = 11;
    public static final byte[] SBFTR = "__SBFTR\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    public byte[] mainCode;      // 主代码（不含头与尾部）
    public String embeddedSource = null; // 若含源码段
    public String scriptName = "";
    public int flagOffset;       // 文件内 "FLAG\0" 的位置

    /** 解析 .csi；非 SA Mobile 结构返回 null */
    public static SaMobileFile parse(byte[] d) {
        if (d.length < HEADER) return null;
        SaMobileFile f = new SaMobileFile();
        StringBuilder nm = new StringBuilder();
        for (int i = 4; i < 11; i++) {
            if (d[i] == 0) break;
            nm.append((char) (d[i] & 0xFF));
        }
        f.scriptName = nm.toString();
        int flag = indexOf(d, "FLAG\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII), HEADER);
        if (flag < 0) return null;
        f.flagOffset = flag;
        f.mainCode = new byte[flag - HEADER];
        System.arraycopy(d, HEADER, f.mainCode, 0, f.mainCode.length);
        // SRC 段
        int src = indexOf(d, "SRC\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII), flag + 5);
        if (src >= 0 && src + 8 <= d.length) {
            int len = (int) (long) readU32(d, src + 4);
            if (src + 8 + len <= d.length && len > 0 && len < d.length) {
                f.embeddedSource = new String(d, src + 8, len, java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return f;
    }

    public static long readU32(byte[] d, int off) {
        return (d[off] & 0xFFL) | ((d[off + 1] & 0xFFL) << 8) | ((d[off + 2] & 0xFFL) << 16) | ((d[off + 3] & 0xFFL) << 24);
    }

    private static int indexOf(byte[] hay, byte[] needle, int from) {
        outer:
        for (int i = from; i + needle.length <= hay.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    /** 生成 .csi（头 + 主代码 + 源码段 + 尾部签名） */
    public static byte[] pack(byte[] mainCode, String scriptName, String source) {
        byte[] name = new byte[7];
        byte[] nm = scriptName.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        System.arraycopy(nm, 0, name, 0, Math.min(7, nm.length));
        byte[] src = source == null ? new byte[0] : source.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] flagTag = "FLAG\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] srcTag = "SRC\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        int flag = HEADER + mainCode.length;
        int srcPos = flag + flagTag.length + 4;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        writeU16(out, 0x03A4); // 未知字段 A（SB 编译产物参考值）
        writeU16(out, 0x070E); // 未知字段 B
        out.write(name, 0, 7);
        out.write(mainCode, 0, mainCode.length);
        out.write(flagTag, 0, 5);
        writeU32(out, srcPos);   // u32（SB 写 FLAG 段后偏移，含义待考证）
        out.write(srcTag, 0, 4);
        writeU32(out, src.length);
        out.write(src, 0, src.length);
        writeU32(out, flag);
        out.write(SBFTR, 0, 8);
        return out.toByteArray();
    }

    private static void writeU16(java.io.ByteArrayOutputStream o, int v) {
        o.write(v & 0xFF); o.write((v >> 8) & 0xFF);
    }
    private static void writeU32(java.io.ByteArrayOutputStream o, long v) {
        o.write((int) (v & 0xFF)); o.write((int) ((v >> 8) & 0xFF));
        o.write((int) ((v >> 16) & 0xFF)); o.write((int) ((v >> 24) & 0xFF));
    }
}
