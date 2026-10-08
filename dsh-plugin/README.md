# CLEO Builder DSH 插件（GTASA / GTASA Mobile）

在 DeepSeek Harness（DSH）内直接制作并编译 CLEO 脚本的插件：**cleo_compile**（编译为 .cs/.csi 字节码）+ **cleo_kb**（内置完整知识库，无需联网）。

```
dsh-plugin/
├── cleo-compiler/          Linux 版编译器工程（纯 Java，从 Android 版移植，零依赖）
│   ├── src/                GameMode / OpcodeTable / TypedCompiler / TypedDecoder / CsFormat / Main
│   ├── data/               GTASA(sa) + GTASA Mobile(sa_mobile) 全量 opcode 表 + 条件/类型/全局数据
│   ├── build.sh            javac 打包 cleo-compiler.jar（主类 Main）
│   └── build/cleo-compiler.jar
└── dsh-cleobuilder/        安装到 DSH 的插件包（自包含：jar + data + 知识库）
    ├── index.js            两个工具 + 系统提示引导
    ├── CLEO-KNOWLEDGE-BASE.md
    └── compiler/           cleo-compiler.jar + data（随插件部署）
```

## 安装

```bash
# 1. 把插件包放进 profile 的 node_modules
cp -r dsh-plugin/dsh-cleobuilder ~/.dsh/profiles/web/node_modules/

# 2. 在 profile 的 cordis.patch.yml 追加（id 需唯一）
# - insert:
#     - id: cleobuilder
#       name: 'dsh-cleobuilder'
#       config:
#         outDir: '/home/dsh/main/cleo_builds'   # 编译产物目录，可改

# 3. 新会话生效（patchReload: live 的 profile 热重载立即生效）
```

要求：本机可执行 `java`（JRE 17+，编译器为纯 Java）。

## 工具

### cleo_compile
把 CLEO 脚本源码编译为字节码文件。

- 参数：`source`（必填，完整源码）、`game`（gtasa 默认 / gtasa_mobile）、`name`（输出文件名）
- 返回：产物路径 / 字节数 / sha256；失败返回带行号的错误
- 产物写入 `config.outDir`（默认 `~/.dsh/cleo_builds`）

```scm
{$CLEO .cs}
0000:
0ACD: show_text_highpriority 'CLEOOK' time 2000
0A93: terminate_this_custom_script
```

Mobile（九宫格触摸）示例：

```scm
{$CLEO .csi}
0000:
:L
wait 0
00D6: if 0DE0: 5 = get_touch_point_state 5 mintime 100
004D: jump_if_false @L
0ACD: show_text_highpriority 'CLEOOK' time 800
0002: jump @L
```

### cleo_kb
内置知识库查询（语法/结构/函数全表/参数/写法/注意事项/游戏数据：车辆 212、人物、武器、按键、天气、坐标）。

- 参数：`section` 可选章节关键词（如 函数/车辆/武器/触摸/注意事项）；缺省返回章节目录；`全部` 返回全文节选

## 编译器能力（与 Android 版一致）

| 模式 | 数据 |
|---|---|
| gtasa | SASCM.INI + CLEO.ini + CLEO+.ini + NewOpcodes.ini（2475 opcode） |
| gtasa_mobile | SASCM.ini + Mobile.ini(0A51 触摸) + C/A(0DD0-0DE3) + CLEO.STD(0A8C-0B1D 全表) + CA(0DF2-0DF6/1000)（2169 opcode） |

结构化语法：if/then/else/end、while、gosub；旧式 00D6+004D；0x80 位取反。踩坑与写法见知识库（cleo_kb）。

## 重新构建编译器

```bash
cd dsh-plugin/cleo-compiler && ./build.sh
cp build/cleo-compiler.jar ../dsh-cleobuilder/compiler/
```
