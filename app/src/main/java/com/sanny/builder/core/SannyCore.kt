package com.sanny.builder.core

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.PointerByReference

/**
 * JNA 映射 sanny_builder_core 的 C ABI（aarch64）.
 * Rust bool 为 1 字节，故返回类型用 Byte；usize 为 8 字节，用 Long。
 */
@Suppress("FunctionName", "unused")
interface SannyCore : Library {

    companion object {
        val INSTANCE: SannyCore by lazy {
            Native.load("core", SannyCore::class.java)
        }
    }

    // ---- language service ----
    fun language_service_set_data_dir(path: String)

    fun language_service_new(): Pointer

    fun language_service_free(server: Pointer)

    fun language_service_client_connect_in_memory(
        server: Pointer,
        handle: Int,
        static_constants_file: String,
        classes_file: String
    ): Byte

    fun language_service_client_connect_with_file(
        server: Pointer,
        file_name: String,
        handle: Int,
        static_constants_file: String,
        classes_file: String
    ): Byte

    fun language_service_client_notify_on_change(
        server: Pointer,
        handle: Int,
        text: String
    ): Byte

    fun language_service_client_disconnect(server: Pointer, handle: Int): Byte

    fun language_service_is_enabled(server: Pointer, handle: Int): Byte

    fun language_service_find(
        server: Pointer,
        symbol: String,
        handle: Int,
        line_number: Int,
        out_value: SymbolInfo
    ): Byte

    fun language_service_filter_constants_by_name(
        server: Pointer,
        handle: Int,
        needle: String,
        line_number: Int,
        dict: Pointer
    ): Byte

    fun language_service_format_function_signature(
        server: Pointer,
        value: String,
        out: PointerByReference
    ): Byte

    // ---- dictionary: StrByStr（补全结果容器）----
    fun dictionary_str_by_str_new(): Pointer

    fun dictionary_str_by_str_free(dict: Pointer)

    fun dictionary_str_by_str_get_count(dict: Pointer): Long

    fun dictionary_str_by_str_get_entry(
        dict: Pointer,
        index: Long,
        out_key: PointerByReference,
        out_value: PointerByReference
    ): Byte
}

/** 对应 core 的 SymbolInfoRaw (repr(C)) */
class SymbolInfo : Structure() {
    @JvmField
    var _type: Int = 0

    @JvmField
    var value: Pointer? = null

    @JvmField
    var nameNoFormat: Pointer? = null

    @JvmField
    var annotation: Pointer? = null

    override fun getFieldOrder(): List<String> =
        listOf("_type", "value", "nameNoFormat", "annotation")

    val typeName: String
        get() = when (_type) {
            0 -> "Number"
            1 -> "String"
            2 -> "Var"
            3 -> "Label"
            4 -> "ModelName"
            5 -> "Function"
            else -> "Unknown(${_type})"
        }

    val valueText: String get() = value?.getString(0) ?: ""
    val nameText: String get() = nameNoFormat?.getString(0) ?: ""
    val annotationText: String get() = annotation?.getString(0) ?: ""
}
