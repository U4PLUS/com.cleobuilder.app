# CLEO Builder (Android)

在 Android 手机上编写、编译 **GTA 系列 CLEO 脚本** 的代码编辑器，竖屏布局。

仓库主页：<https://github.com/U4PLUS/com.cleobuilder.app/>

## 功能

- 📱 竖屏代码编辑器：语法高亮、行号、自动换行 / 水平滚动、双指缩放（画布式锚点）
- 🧩 代码补全联想（`wait` / `if` / `goto` …）、底部标点符号栏
- ▶️ 内置编译器：`.cs` / `.csi` / `.csa`（CLEO 4 / CLEO+ 语法、typed opcodes）
- 🎮 全游戏模式：GTA III / VC / San Andreas / LCS / VCS（含移动版、PS2、PSP 等）
- 🚨 编译错误多行标红 + 自动跳转首个错误行
- 💾 编译产物与源码同名同步保存到 `cleo/CLEO_Builder/`
- 📂 内建文件选择器（无需系统 SAF）
- ↩️ 撤销 / 重做（保留光标位置）、固定深色主题

## 构建

```bash
./gradlew :app:assembleDebug
```

- 产物：`app/build/outputs/apk/debug/app-debug.apk`
- 环境：JDK 17+、Android SDK（compileSdk 34 / minSdk 21 / targetSdk 29）
- 原生依赖：`jniLibs/*/libcore.so`（语言服务核心，来源见 THIRD-PARTY-NOTICES）

## 目录结构

```
app/src/main/java/com/sanny/builder/
  compiler/   TypedCompiler/TypedDecompiler/OpcodeTable/GameMode/CompilerService…
  core/       SannyCore（JNA 绑定）/ SannyService / AssetsHelper
  ui/         CleoEditor（自绘编辑区）/ CodeHighlighter / FilePicker
app/src/main/assets/sanny/data/   各模式数据（opcode 表、关键字、模板）
app/src/main/jniLibs/             libcore.so（arm64 / armeabi-v7a）
```

## 致谢与第三方组件

| 组件 | 许可 | 说明 |
|---|---|---|
| 语言服务核心 `libcore.so` | 见 NOTICES | 基于 Sanny Builder 官方 core 项目编译的原生库，版权归原作者 |
| opcode 数据 | 社区数据库 | GTA Modding Community Opcode Database |
| sora-editor | Apache-2.0 | 早期版本使用的编辑器组件 |
| JNA | LGPL-2.1+exception | Java 原生调用绑定 |
| AndroidX / Material Components | Apache-2.0 | UI 基础组件 |

完整清单见 [THIRD-PARTY-NOTICES.md](./THIRD-PARTY-NOTICES.md)。

## 许可证

本项目代码采用 **Apache License 2.0**（见 [LICENSE](./LICENSE)）。
原生库与数据文件的版权归属及许可详见 THIRD-PARTY-NOTICES.md。

> ⚠️ 本项目为第三方独立项目，与 Sanny Builder、Rockstar Games 无隶属关系。
> 使用与分发请遵守相关项目的许可证与游戏平台规则。