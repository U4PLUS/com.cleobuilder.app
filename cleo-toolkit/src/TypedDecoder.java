package com.sanny.builder.compiler;

/**
 * GTA III/VC/SA Mobile 类型化参数格式的公共解码器。
 * 每个参数 = 1 字节类型 + 值。
 * 全局变量 var: u16 = index*4；局部变量 lvar: u16 = index；
 * negated opcode = 第二字节 | 0x80；istring8 = 类型字节>0x13 时为字符串首字符+7 字节。
 */
public final class TypedDecoder {

    public static final int T_INT32 = 0x01, T_VAR = 0x02, T_LVAR = 0x03, T_INT8 = 0x04,
            T_INT16 = 0x05, T_FLOAT32 = 0x06, T_VAR_ARRAY = 0x07, T_LVAR_ARRAY = 0x08,
            T_STRING8 = 0x09, T_VAR_STRING8 = 0x0A, T_LVAR_STRING8 = 0x0B,
            T_VAR_STRING8_ARRAY = 0x0C, T_LVAR_STRING8_ARRAY = 0x0D, T_VLSTRING = 0x0E,
            T_STRING16 = 0x0F, T_VAR_STRING16 = 0x10, T_LVAR_STRING16 = 0x11;

    public static final int MAX_TYPE = 0x13;

    /** 类型字节 -> 值宽度（不含类型字节本身）；vlstring 变长 */
    public static int width(int typeId) {
        switch (typeId) {
            case T_INT32: return 4;
            case T_VAR: case T_LVAR: return 2;
            case T_INT8: return 1;
            case T_INT16: return 2;
            case T_FLOAT32: return 4;
            case T_VAR_ARRAY: case T_LVAR_ARRAY: return 5; // 基址2+索引2+元素大小1
            case T_STRING8: return 8;
            case T_VAR_STRING8: case T_LVAR_STRING8: return 2;
            case T_VAR_STRING8_ARRAY: case T_LVAR_STRING8_ARRAY: return 6;
            case T_STRING16: return 16;
            case T_VAR_STRING16: case T_LVAR_STRING16: return 2;
            default: return 4;
        }
    }

    public static boolean isIstring8(int typeId) { return typeId > MAX_TYPE; }
    public static boolean isVlstring(int typeId) { return typeId == T_VLSTRING; }
}
