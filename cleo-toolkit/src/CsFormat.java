package com.sanny.builder.compiler;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * GTA SA CLEO .cs 文件格式（SCM main 段）：
 *   [0..2)  opcode 0004（段标志）
 *   [2..6)  u32 主代码长度
 *   [6..)   主代码（指令流）
 * CLEO 4 / CLEO-Redux 均按此格式加载自定义脚本。
 * 另兼容 SannyBuilder Android 旧版自定义头（version=3 + u32 size + code）的导入。
 */
public class CsFormat {

    /** 打包：输出 CLEO 兼容的 SCM main 段。 */
    public static byte[] pack(byte[] code) {
        ByteBuffer bb = ByteBuffer.allocate(6 + code.length).order(ByteOrder.LITTLE_ENDIAN);
        bb.putShort((short) 0x0004);
        bb.putInt(code.length);
        bb.put(code);
        return bb.array();
    }

    /** 解包：返回主代码。支持 SCM 段（04 00+len）与旧版自定义头（version=3+size）。非法返回 null。 */
    public static byte[] unpack(byte[] cs, StringBuilder err) {
        if (cs.length < 6) { err.append("文件太短，不是有效的 .cs"); return null; }
        ByteBuffer bb = ByteBuffer.wrap(cs).order(ByteOrder.LITTLE_ENDIAN);
        int magic = bb.getShort() & 0xFFFF;
        if (magic == 0x0004) {
            // SCM main 段：04 00 + u32 len + code
            int size = bb.getInt();
            if (size < 0 || 6 + size > cs.length) { err.append("main 段长度不合法"); return null; }
            byte[] code = new byte[size];
            bb.get(code);
            return code;
        }
        if (magic == 3) {
            // 旧自定义头：u32 version=3 + u32 size + code
            if (cs.length < 8) { err.append("文件太短，不是有效的 .cs"); return null; }
            ByteBuffer bb2 = ByteBuffer.wrap(cs).order(ByteOrder.LITTLE_ENDIAN);
            int version = bb2.getInt();
            int size = bb2.getInt();
            if (version != 3) { err.append("未知脚本版本 ").append(version).append("（当前支持 3 = GTA SA）"); return null; }
            if (size < 0 || 8 + size > cs.length) { err.append("main 段长度不合法"); return null; }
            byte[] code = new byte[size];
            bb2.get(code);
            return code;
        }
        err.append("不是有效的 .cs 文件（magic=0x").append(Integer.toHexString(magic)).append("）");
        return null;
    }
}
