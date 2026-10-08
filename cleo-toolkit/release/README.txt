==============================================================
 CLEO Builder 1.0.0 —— GTA SA / GTASA Mobile 脚本编译器
==============================================================

【这是什么】
  把 CLEO 脚本（.cs/.csi 源码）编译成游戏可执行的字节码文件。
  支持 GTA San Andreas（PC）和 GTASA Mobile（Android）两个模式。
  单 jar 自包含：全部 opcode 数据已内嵌，无需任何其他文件。

【文件清单】
  cleobuilder.jar           编译器本体（依赖：JRE 17+）
  CLEO-KNOWLEDGE-BASE.md    CLEO 脚本知识库（完整资料）
  README.txt                本说明

--------------------------------------------------------------
【依赖】
  Java 运行时 JRE 17 或更高（java -version 可查看版本）。
  除 Java 外无任何依赖，无需联网，数据全部内置。

--------------------------------------------------------------
【使用方法】

  最简单的用法 —— 三个位置参数：

      java -jar cleobuilder.jar <输入.cs> <输出文件> [模式]

  例：把 myscript.txt 编译为 myscript.cs（默认 PC 模式）：
      java -jar cleobuilder.jar myscript.txt myscript.cs

  例：编译 Android 版脚本（模式 gtasa_mobile）：
      java -jar cleobuilder.jar myscript.txt myscript.csi gtasa_mobile

  也可以用命名参数：

      java -jar cleobuilder.jar -i myscript.txt -o myscript.csi -m gtasa_mobile

--------------------------------------------------------------
【参数说明】
  -i, --input    输入文件（脚本源码，UTF-8 文本）
  -o, --output   输出文件（编译后的字节码，.cs 或任意名字）
  -m, --mode     游戏模式：
                   gtasa          GTA San Andreas（PC），默认
                   gtasa_mobile   GTASA Android（含触摸控件、九宫格触摸）
  -h, --help     显示帮助
      --info     显示编译器信息（版本 / 模式 / opcode 数量）
 也可以直接位置参数：java -jar cleobuilder.jar 输入 输出 [模式]

--------------------------------------------------------------
【输入脚本格式】
  脚本是 UTF-8 文本，第一行写文件头，正文每行一条指令：

      {$CLEO .cs}                     ; PC 脚本头
      0000:                           ; 主标签
      0ACD: show_text_highpriority 'CLEOOK' time 2000
      0A93: terminate_this_custom_script

  Android 脚本用 {$CLEO .csi} 头。用法与全部函数见资料文档。

--------------------------------------------------------------
【编译结果】
  成功：输出文件生成，终端打印 "编译成功: 路径 (N 字节)"。
  失败：打印 "编译失败" 和带行号的错误（如 第 2 行: 参数不足），退出码 1。

  产物放置：
    PC：  放在游戏 CLEO\ 文件夹
    Mobile：放在 Android/data/com.rockstargames.gtasa/files/CLEO/

--------------------------------------------------------------
【给 AI 的说明 —— 请务必阅读】
  同目录的 CLEO-KNOWLEDGE-BASE.md 是完整 CLEO 知识库，包含：
    语法 / 脚本结构 / 支持的函数（opcode 全表）/ 参数类型 /
    写法示例 / 注意事项（踩坑表）/ 游戏数据（车辆 ID、武器 ID、按键码等）。
  制作脚本前先查阅该文档，可避免绝大多数编译错误。
  文档按章节组织，可以通过章节标题快速定位所需内容。

【版本历史】
  1.0.0  首个独立工具包版本：单 jar 自包含（数据内嵌）、人机友好 CLI、
         内置知识库。支持 gtasa / gtasa_mobile。
==============================================================
