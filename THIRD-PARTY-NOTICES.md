# Third-Party Notices

本仓库（CLEO Builder for Android）包含以下第三方组件的代码、数据或二进制。各组件版权归其原作者所有，在此致谢并遵守对应许可。

## 1. 语言服务核心 `libcore.so`（app/src/main/jniLibs/）

- **来源**：基于 Sanny Builder 官方 core 项目（`https://github.com/sannybuilder/core`）编译的 Rust 语言服务原生库（arm64-v8a / armeabi-v7a）。
- **版权**：© Seemann（Sanny Builder 作者），保留所有权利。
- **说明**：以"原样二进制 + 署名致谢"方式随本项目发布，用于代码补全、符号查询等语言服务；Sanny Builder 本体许可见 `https://sannybuilder.com/EULA.txt`（Freeware License Agreement，禁止转售，分发需保留许可声明）。
- 本项目与 Sanny Builder 无隶属关系；如需商用或独立分发该库，请直接联系作者。

## 2. 游戏数据（app/src/main/assets/sanny/data/）

- **opcode 表**（SASCM.INI / SCM.INI / VCSCM.INI / LCSSCM.INI 等）：来源为 **GTA Modding Community Opcode Database**（`https://gtagmodding.com/opcode-database/`、`https://gtamods.com/wiki/List_of_opcodes`），社区整理的事实性数据。
- **关键字 / 模板 / 变量类型**：整理自社区文档与格式规范（CLEO 语法规范见 `https://cleo.li/`）。
- 编辑模式定义（mode.xml）为本项目整理的中性描述。

## 3. 编译器与编辑器实现

- **编译器逻辑**（`compiler/` 目录 Java 实现）：按 CLEO 脚本格式规范独立实现的字节码编译 / 反编译（格式参考社区文档，未复制 Sanny Builder 源码表达）。
- **编辑器**（`ui/CleoEditor.kt`）：基于 Android 系统 EditText 的自绘实现（行号、高亮、缩放、撤销为独立实现）。

## 4. 第三方库依赖（Gradle）

| 库 | 许可 | 用途 |
|---|---|---|
| `io.github.rosemoe:editor:0.23.7` | Apache License 2.0 | 早期编辑器组件（保留依赖） |
| `net.java.dev.jna:jna:5.14.0` | LGPL-2.1 + Classpath exception | JNA 原生调用绑定 |
| `androidx.core:core-ktx` | Apache License 2.0 | 基础库 |
| `androidx.appcompat:appcompat` | Apache License 2.0 | 兼容组件 |
| `com.google.android.material:material` | Apache License 2.0 | Material UI |
| `androidx.constraintlayout:constraintlayout` | Apache License 2.0 | 布局 |

Apache License 2.0 全文见本仓库 `LICENSE`（适用于本项目自研代码）；sora-editor 的 Apache-2.0 声明保留于其上游包中。

## 5. 参考致谢

- **Sanny Builder**（`https://sannybuilder.com`）：语法格式、编辑器理念与脚本生态的启发来源，特此致谢。
- **CLEO Library**（`https://cleo.li`，LGPL）：`.cs` / `.csi` 运行时与格式规范。
- **GTA Modding 社区**：opcode 数据库维护者。

*本文件描述如实反映了仓库组件的来源；任何遗漏或需更正之处，请提交 Issue 说明。*
