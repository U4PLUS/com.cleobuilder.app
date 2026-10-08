# CLEO 脚本规格（GTA San Andreas / GTASA Mobile）

> 面向 AI 的完整速查资料。函数签名 **由 CLEO Builder 编译器数据直接生成**（与编译器解析结果 100% 一致）；语法/控制流写法**均经编译器实测验证**。写脚本时以本表为准。

---

## 1. 文件头与脚本类型

| 后缀 | 头 | 用途 | 放置 |
|---|---|---|---|
| `.cs` | `{$CLEO .cs}` | 自定义脚本（最常用） | PC：`CLEO\`；Mobile：`Android/data/com.rockstargames.gtasa/files/CLEO/` |
| `.csi` | `{$CLEO .csi}` | SA Mobile 容器（内嵌源码 FLAG/SRC 结构） | 同 CLEO 目录 |
| `.csa` | `{$CLEO .csa}` | 流脚本（0A92 加载） | PC：`CLEO\` |

脚本名 = 文件名（去扩展名），纯 ASCII。

---

## 2. 语法（词法）

| 元素 | 写法 | 说明 |
|---|---|---|
| 注释 | `//` `;` `{ }` `/* */` | 均支持 |
| 全局变量 | `$N`（纯数字，如 `$500`） | 编码 var(u16=N*4) |
| 内置全局 | `$PLAYER_CHAR`(=2) `$PLAYER_ACTOR`(=3) | 注册于 CustomVariables.ini，编译自动加载 |
| 局部变量 | `N@`（如 `0@`） | 编码 lvar(u16 N) |
| 类型后缀 | `_s`字符串 `_f`浮点 `_v`向量 `_h`句柄 | 变量名尾缀 |
| 整数 | `123` `0x1F` | 按范围编 int8/int16/int32 |
| 浮点 | `1.5` | float32 |
| 字符串 | `'TEXT'` | string8 |
| 标签 | `:NAME` 定义，`@NAME` 引用 | 跳转目标编码 int32 |
| 模型 | **数字 ID**（`411`） | `#MODEL` 名不支持 |
| 行格式 | `OPCODE: 参数...`（HEX 或模板名） | 一行一指令 |

---

## 3. 脚本结构（骨架，实测可编译）

```scm
{$CLEO .cs}
0000:
wait 1000                          ; 初始化段（仅一次）
while 001D: 1 > 0                  ; 主循环（= while true）
    wait 0                         ; 必写，释放帧
    if
        0AB0:   key_pressed 65     ; 条件行：完整 HEX 行
    then
        0ACD: show_text_highpriority 'CLEOOK' time 2000
    end
end
```

---

## 4. 控制流（实测支持的形态）

| 形态 | 写法 | 实测 |
|---|---|---|
| 分支 | `if` + 条件行 + `then` … `else` … `end` | ✅ 条件行必须完整 HEX 行 |
| 多条件 | 多个条件行依次排列（and）；`or` 前缀 | ✅ |
| 循环 | `while <条件行>` + `wait 0` + `end` | ✅ |
| 永真循环 | `while 001D: 1 > 0` | ✅（`while true` 不支持） |
| 取反 | opcode 号置 0x80 位：`8248:`=`0248` 取反、`8038:`=`0038` 取反 | ✅（`if not 00E1:` 不支持） |
| 旧式分支 | `00D6: if <条件行>` + `004D: jump_if_false @L` | ✅ 任意条件 opcode 可用（含触摸类） |
| 子程序 | `gosub @SUB` … `:SUB` … `0AA1: return_if_false` | ✅ |
| 跳转 | `0002: jump @L`；`004D: jump_if_false @L` | ✅ |

---

## 5. 参数类型

### 5.1 占位符（opcode 表内）

| 占位符 | 含义 |
|---|---|
| `%1d%` `%2d%` | int 值 / 变量或值 |
| `%1s%` | 字符串 |
| `%1p%` | 标签/偏移 |
| `%1o%` `%1m%` | 模型 |
| `%1g%` | GXT 键 |
| `%1k%` | 128 字节字符串（日志） |
| `%1b%` | userdir/rootdir 选择 |
| `%1h%` | half/int16 |
| `%1f%` | float |

### 5.2 编码规则（TypedCompiler）

- 整数：按值范围选 **int8 / int16 / int32**
- 浮点：**float32**
- `$N`：**var**(u16 = N×4)
- `N@`：**lvar**(u16 = N)
- `'xxx'`：**string8**
- `@LABEL`：**int32**（= -(11+偏移)）
- `not <名称>` 前缀：opcode 第二字节 **\| 0x80**

---

## 6. 函数表

### 6.0 基础常用（SASCM.INI，均已实测编译）

| opcode | 签名 | 示例 |
|---|---|---|
| 0001 | `wait %1d% ms` | `wait 0` |
| 0002 | `jump %1p%` | `0002: jump @L` |
| 004D | `jump_if_false %1p%` | 旧式条件 |
| 004E | `terminate_this_script` | 终结（SCM 内建） |
| 0004 | `%1d% = %2d%` (int) | `0004: $500 = 1` |
| 0005 | `%1d% = %2d%` (float) | `0005: $501 = 1.5` |
| 005A | `%1d% += %2d%` (int) | 自增 |
| 0062 | `%1d% -= %2d%` (int) | 自减 |
| 001D | `%1d% > %2d%` (int 条件) | `001D: 1 > 0` |
| 0038 | `%1d% == %2d%` (条件) | `0038: $500 == 0` |
| 00D6 | `if %1d%`（旧式条件头） | `00D6: if 0248: model 411 available` |
| 00E1 | `player %1d% pressed_key %2d%`（条件） | 键位枚举 |
| 0247 | `load_model %1o%` | `0247: load_model 411` |
| 0248 | `model %1o% available`（条件） | 加载完成检测 |
| 0249 | `release_model %1o%` | 释放 |
| 00A5 | `%5d% = create_car %1m% at %2d% %3d% %4d%` | 生成车 |
| 0175 | `set_car %1d% Z_angle_to %2d%` | 朝向 |
| 00A1 | `put_actor %1d% at %2d% %3d% %4d%` | 传送 |
| 04C4 | `store_coords_to %5d% %6d% %7d% from_actor %1d% with_offset %2d% %3d% %4d%` | 取坐标 |
| 00AA | `store_car %1d% position_to %2d% %3d% %4d%` | 存车坐标 |
| 0256 | `player %1d% defined`（条件） | 玩家存在 |
| 0118 | `actor %1d% dead`（条件） | actor 死亡 |
| 0109 | `player %1d% money += %2d%` | 加钱 |
| 010B | `%2d% = player %1d% money` | 读钱 |
| 0209 | `%3d% = random_int_in_ranges %1d% %2d%` | 随机 |
| 0208 | `%3d% = random_float_in_ranges %1d% %2d%` | 随机浮点 |
| 03E5 | `show_text_box %1g%` | 文本盒 |
| 0ACD | `show_text_highpriority %1s% time %2d%` | 高优先显示 |
| 0ACB | `show_styled_text %1s% time %2d% style %3d%` | 样式文本 |
| 01E3 | `show_text_1number_styled GXT %1g% number %2d% time %3d% style %4d%` | 带数字 |
| 04D4 | `display_text GXT %1g%` | 立即显示 |
| 02A3 | `enable_widescreen %1d%` | 黑边 |
| 01EB | `set_traffic_density_multiplier_to %1d%` | 清交通 |

文本格式码（字符串内）：`~G~`绿 `~B~`蓝 `~Y~`黄 `~R~`红 `~W~`白 `~n~`换行。

### 6.1 完整函数表（机器生成，与编译器数据一致）

以下四表**由编译器数据文件直接生成**：参数数 `-1` 表示变参（格式化/调用类，编译兼容有风险，优先用定参替代）。

## 附表 A：CLEO 特有函数（SASCM.CLEO.ini，PC+Mobile 通用）
| opcode | 参数数 | 签名 |
|---|---|---|
| 0A8C | 4 | `write_memory %1d% size %2d% value %3d% virtual_protect %4d%` |
| 0A8D | 4 | `%4d% = read_memory %1d% size %2d% virtual_protect %3d%` |
| 0A8E | 3 | `%3d% = %1d% + %2d% ; int` |
| 0A8F | 3 | `%3d% = %1d% - %2d% ; int` |
| 0A90 | 3 | `%3d% = %1d% * %2d% ; int` |
| 0A91 | 3 | `%3d% = %1d% / %2d% ; int` |
| 0A92 | -1 | `stream_custom_script %1s%` |
| 0A93 | 0 | `terminate_this_custom_script` |
| 0A94 | -1 | `load_and_launch_custom_mission %1s%` |
| 0A95 | 0 | `save_this_custom_script` |
| 0A96 | 2 | `%2d% = ped %1d% struct` |
| 0A97 | 2 | `%2d% = vehicle %1d% struct` |
| 0A98 | 2 | `%2d% = object %1d% struct` |
| 0A99 | 1 | `set_current_directory %1buserdir/rootdir%` |
| 0A9A | 3 | `%3d% = open_file %1s% mode %2d% ; IF and SET` |
| 0A9B | 1 | `close_file %1d%` |
| 0A9C | 2 | `%2d% = file %1d% size` |
| 0A9D | 3 | `read_file %1d% size %2d% to %3d%` |
| 0A9E | 3 | `write_file %1d% size %2d% from %3d%` |
| 0A9F | 1 | `%1d% = get_this_script_struct` |
| 0AA0 | 1 | `gosub_if_false %1p%` |
| 0AA1 | 0 | `return_if_false` |
| 0AA2 | 2 | `%2h% = load_dynamic_library %1s% ; IF and SET` |
| 0AA3 | 1 | `free_dynamic_library %1h%` |
| 0AA4 | 3 | `%3d% = get_dynamic_library_procedure %1s% library %2d% ; IF and SET` |
| 0AA5 | -1 | `call_function %1d% num_params %2h% pop %3h%` |
| 0AA6 | -1 | `call_method %1d% struct %2d% num_params %3h% pop %4h%` |
| 0AA7 | -1 | `call_function_return %1d% num_params %2h% pop %3h%` |
| 0AA8 | -1 | `call_method_return %1d% struct %2d% num_params %3h% pop %4h%` |
| 0AA9 | 0 | `is_game_version_original` |
| 0AAA | 2 | `%2d% = get_script_struct_named %1s%` |
| 0AAB | 1 | `does_file_exist %1s%` |
| 0AAC | 2 | `%2d% = load_audio_stream %1d%` |
| 0AAD | 2 | `set_audio_stream %1d% state %2d%` |
| 0AAE | 1 | `remove_audio_stream %1d%` |
| 0AAF | 2 | `%2d% = get_audio_stream_length %1d%` |
| 0AB0 | 1 | `is_key_pressed %1d%` |
| 0AB1 | -1 | `cleo_call %1p%` |
| 0AB2 | -1 | `cleo_return %1d%` |
| 0AB3 | 2 | `cleo_shared_var %1d% = %2d%` |
| 0AB4 | 2 | `%2d% = cleo_shared_var %1d%` |
| 0AB5 | 3 | `store_char %1d% closest_vehicle_to %2d% closest_ped_to %3d%` |
| 0AB6 | 3 | `get_target_blip_coords_to %1d% %2d% %3d% // IF and SET` |
| 0AB7 | 2 | `get_car %1d% number_of_gears_to %2d%` |
| 0AB8 | 2 | `get_car %1d% current_gear_to %2d%` |
| 0AB9 | 2 | `get_audio_stream %1d% state_to %2d%` |
| 0ABA | 1 | `terminate_all_custom_scripts_with_this_name %1s%` |
| 0ABB | 2 | `%2d% = audio_stream %1d% volume` |
| 0ABC | 2 | `set_audio_stream %1d% volume %2d%` |
| 0ABD | 1 | `car %1d% siren_on` |
| 0ABE | 1 | `car %1d% engine_on` |
| 0ABF | 2 | `set_car %1d% engine_state_to %2d%` |
| 0AC0 | 2 | `set_audio_stream %1d% looped %2d%` |
| 0AC1 | 2 | `%2d% = load_audio_stream_with_3d_support %1d% ; IF and SET` |
| 0AC2 | 4 | `link_3d_audio_stream %1d% at_coords %2d% %3d% %4d%` |
| 0AC3 | 2 | `link_3d_audio_stream %1d% to_object %2d%` |
| 0AC4 | 2 | `link_3d_audio_stream %1d% to_actor %2d%` |
| 0AC5 | 2 | `link_3d_audio_stream %1d% to_car %2d%` |
| 0AC6 | 2 | `%2d% = label %1p% pointer` |
| 0AC7 | 2 | `%2d% = var %1d% pointer` |
| 0AC8 | 2 | `%2d% = allocate_memory_size %1d%` |
| 0AC9 | 1 | `free_allocated_memory %1d%` |
| 0ACA | 1 | `show_text_box %1s%` |
| 0ACB | 3 | `show_styled_text %1s% time %2d% style %3d%` |
| 0ACC | 2 | `show_text_lowpriority %1s% time %2d%` |
| 0ACD | 2 | `show_text_highpriority %1s% time %2d%` |
| 0ACE | -1 | `show_formatted_text_box %1s%` |
| 0ACF | -1 | `show_formatted_styled_text %1s% time %2d% style %3d%` |
| 0AD0 | -1 | `show_formatted_text_lowpriority %1s% time %2s%` |
| 0AD1 | -1 | `show_formatted_text_highpriority %1s% time %2s%` |
| 0AD2 | 2 | `%2d% = player %1d% targeted_actor // IF and SET` |
| 0AD3 | -1 | `%1d% = string_format %2s%` |
| 0AD4 | -1 | `%3d% = scan_string %1d% format %2s%` |
| 0AD5 | 3 | `file %1d% seek %2d% from_origin %3d% // IF and SET` |
| 0AD6 | 1 | `is_end_of_file_reached %1d%` |
| 0AD7 | 3 | `read_string_from_file %1d% to %2d% size %3d% // IF and SET` |
| 0AD8 | 2 | `write_string_to_file %1d% from %2d% // IF and SET` |
| 0AD9 | -1 | `write_formatted_text %2d% to_file %1d%` |
| 0ADA | -1 | `%3d% = scan_file %1d% format %2d% // IF and SET` |
| 0ADB | 2 | `%2d% = vehicle_model %1o% name` |
| 0ADC | 1 | `test_cheat %1d%` |
| 0ADD | 1 | `spawn_car_with_model %1o% like_a_cheat` |
| 0ADE | 2 | `%2d% = text_label_string %1d%` |
| 0ADF | 2 | `add_text_label %1d% text %2d%` |
| 0AE0 | 1 | `remove_text_label %1d%` |
| 0AE1 | 7 | `%7d% = random_char_near_point %1d% %2d% %3d% in_radius %4d% find_next %5h% pass_deads %6h% // IF and SET` |
| 0AE2 | 7 | `%7d% = random_vehicle_near_point %1d% %2d% %3d% in_radius %4d% find_next %5h% pass_wrecked %6h% // IF and SET` |
| 0AE3 | 6 | `%6d% = random_object_near_point %1d% %2d% %3d% in_radius %4d% find_next %5h% // IF and SET` |
| 0AE4 | 1 | `does_directory_exist %1d%` |
| 0AE5 | 1 | `create_directory %1d% ; IF and SET` |
| 0AE6 | 3 | `%2d% = find_first_file %1d% get_filename_to %3d% ; IF and SET` |
| 0AE7 | 2 | `%2d% = find_next_file %1d% ; IF and SET` |
| 0AE8 | 1 | `find_close %1d%` |
| 0AE9 | 1 | `pop_float %1d%` |
| 0AEA | 2 | `%2d% = ped_struct %1d% handle` |
| 0AEB | 2 | `%2d% = vehicle_struct %1d% handle` |
| 0AEC | 2 | `%2d% = object_struct %1d% handle` |
| 0AED | 3 | `%3d% = float %1d% to_string_format %2d%` |
| 0AEE | 3 | `%3d% = pow %1d% base %2d% // all floats` |
| 0AEF | 3 | `%3d% = log %1d% base %2d% // all floats` |
| 0AF0 | 4 | `%4d% = read_int_from_ini_file %1s% section %2s% key %3s%` |
| 0AF1 | 4 | `write_int %1d% to_ini_file %2s% section %3s% key %4s%` |
| 0AF2 | 4 | `%4d% = read_float_from_ini_file %1s% section %2s% key %3s%` |
| 0AF3 | 4 | `write_float %1d% to_ini_file %2s% section %3s% key %4s%` |
| 0AF4 | 4 | `%4d% = read_string_from_ini_file %1s% section %2s% key %3s%` |
| 0AF5 | 4 | `write_string %1s% to_ini_file %2s% section %3s% key %4s%` |
| 0B00 | 1 | `delete_file %1d% ;; IF and SET` |
| 0B01 | 2 | `delete_directory %1d% with_all_files_and_subdirectories %2d% ;; IF and SET` |
| 0B02 | 2 | `move_file %1d% to %2d% ;; IF and SET` |
| 0B03 | 2 | `move_directory %1d% to %2d% ;; IF and SET` |
| 0B04 | 2 | `copy_file %1d% to %2d% ;; IF and SET` |
| 0B05 | 2 | `copy_directory %1d% to %2d% ;; IF and SET` |
| 0B10 | 3 | `%3d% = %1d% & %2d%` |
| 0B11 | 3 | `%3d% = %1d% | %2d%` |
| 0B12 | 3 | `%3d% = %1d% ^ %2d%` |
| 0B13 | 2 | `%2d% = ~%1d%` |
| 0B14 | 3 | `%3d% = %1d% % %2d%` |
| 0B15 | 3 | `%3d% = %1d% >> %2d%` |
| 0B16 | 3 | `%3d% = %1d% << %2d%` |
| 0B17 | 2 | `%1d% &= %2d%` |
| 0B18 | 2 | `%1d% |= %2d%` |
| 0B19 | 2 | `%1d% ^= %2d%` |
| 0B1A | 1 | `~%1d%` |
| 0B1B | 2 | `%1d% %= %2d%` |
| 0B1C | 2 | `%1d% >>= %2d%` |
| 0B1D | 2 | `%1d% <<= %2d%` |

## 附表 B：CLEO+ 扩展（SASCM.CLEO+.ini）
| opcode | 参数数 | 签名 |
|---|---|---|
| 0E00 | 2 | `get_car_alarm %1d% mode_to %2d%` |
| 0E01 | 7 | `create_object_no_save %1o% at %2d% %3d% %4d% offset %5d% ground %6d% to %7d%` |
| 0E02 | 1 | `set_car_generator_no_save %1d%` |
| 0E03 | 2 | `perlin_noise %1d% store_to %2d%` |
| 0E04 | 1 | `get_next_weather_to %1d%` |
| 0E05 | 1 | `set_next_weather_to %1d%` |
| 0E06 | 1 | `get_rain_intensity %1d%` |
| 0E07 | 1 | `set_rain_intensity %1d%` |
| 0E08 | 1 | `is_car_script_controlled %1d%` |
| 0E09 | 1 | `mark_car_as_needed %1d%` |
| 0E0A | 1 | `is_char_script_controlled %1d%` |
| 0E0B | 1 | `mark_char_as_needed %1d%` |
| 0E0C | 1 | `is_object_script_controlled %1d%` |
| 0E0D | 1 | `mark_object_as_needed %1d%` |
| 0E0E | 2 | `get_current_resolution_to %1d% %2d%` |
| 0E0F | 4 | `get_fixed_xy_aspect_ratio %1d% %2d% to %3d% %4d%` |
| 0E10 | 0 | `is_mouse_wheel_up` |
| 0E11 | 0 | `is_mouse_wheel_down` |
| 0E12 | 2 | `get_vehicle %1d% subclass_to %2d%` |
| 0E13 | 2 | `get_entity %1d% type_to %2d%` |
| 0E14 | 3 | `init_extended_char_vars %1d% id %2d% new_vars %3d%` |
| 0E15 | 4 | `set_extended_char_var %1d% id %2d% var %3d% value %4d%` |
| 0E16 | 4 | `get_extended_char_var %1d% id %2d% var %3d% to %4d%` |
| 0E17 | 3 | `init_extended_car_vars %1d% id %2d% new_vars %3d%` |
| 0E18 | 4 | `set_extended_car_var %1d% id %2d% var %3d% value %4d%` |
| 0E19 | 4 | `get_extended_car_var %1d% id %2d% var %3d% to %4d%` |
| 0E1A | 3 | `init_extended_object_vars %1d% id %2d% new_vars %3d%` |
| 0E1B | 4 | `set_extended_object_var %1d% id %2d% var %3d% value %4d%` |
| 0E1C | 4 | `get_extended_object_var %1d% id %2d% var %3d% to %4d%` |
| 0E1D | 0 | `is_on_mission` |
| 0E1E | 15 | `draw_texture_plus %1d% event %2d% pos %3d% %4d% size %5d% %6d% angle %7d% depth %8d% fix_aspect_ratio %9d% maskTrisCount %10d% maskTrisArray %11d% rgba %12d% %13d% %14d% %15d%` |
| 0E1F | 4 | `ease %1d% mode %2d% way %3d% to %4d%` |
| 0E20 | 0 | `is_on_samp` |
| 0E21 | 1 | `get_audio_sfx_volume %1d%` |
| 0E22 | 1 | `get_audio_radio_volume %1d%` |
| 0E23 | 1 | `get_mouse_sensibility_to %1d%` |
| 0E24 | 4 | `fix_char %1d% ground %2d% brightness %3d% and_fade_in %4d%` |
| 0E25 | 0 | `is_on_cutscene` |
| 0E26 | 2 | `is_weapon %1d% fire_type %2d%` |
| 0E27 | 5 | `get_angle_from_two_coords %1d% %2d% and %3d% %4d% to %5d%` |
| 0E28 | 4 | `write_struct %1d% offset %2d% size %3d% value %4d%` |
| 0E29 | 7 | `perlin_noise %1d% octaves %2d% frequency %3d% amplitude %4d% lacunarity %5d% persistence %6d% store_to %7d%` |
| 0E2A | 9 | `add_cleo_blip %1d% position %2d% %3d% is_short %4d% RGBA %5d% %6d% %7d% %8d% store_to %9d%` |
| 0E2B | 1 | `remove_cleo_blip %1d%` |
| 0E2C | 1 | `get_current_save_slot %1d%` |
| 0E2D | 0 | `is_game_first_start` |
| 0E2E | 10 | `create_render_object_to_char_bone %1d% model %2d% bone %3d% offset %4d% %5d% %6d% rotation %7d% %8d% %9d% store_to %10d%` |
| 0E2F | 1 | `delete_render_object %1d%` |
| 0E30 | 4 | `set_render_object_auto_hide %1d% dead %2d% weapon %3d% car %4d%` |
| 0E31 | 2 | `set_render_object_visible %1d% %2d%` |
| 0E32 | 4 | `set_char_coordinates_simple %1d% coord %2d% %3d% %4d%` |
| 0E33 | 5 | `get_pickup_this_coord %1d% %2d% %3d% only_available %4d% store_to %5d%` |
| 0E34 | 2 | `get_pickup_model %1d% %2d%` |
| 0E35 | 4 | `set_render_object_position %1d% %2d% %3d% %4d%` |
| 0E36 | 4 | `set_render_object_rotation %1d% %2d% %3d% %4d%` |
| 0E37 | 4 | `set_render_object_scale %1d% %2d% %3d% %4d%` |
| 0E38 | 2 | `get_pickup_pointer %1d% store_to %2d%` |
| 0E39 | 2 | `get_pickup_type %1d% store_to %2d%` |
| 0E3A | 5 | `set_render_object_distortion %1d% %2d% %3d% %4d% %5d%` |
| 0E3B | 2 | `get_audiostream_internal %1d% store_to %2d%` |
| 0E3C | 2 | `get_texture_from_sprite %1d% store_to %2d%` |
| 0E3D | 1 | `is_key_just_pressed %1d%` |
| 0E3E | 2 | `is_button_just_pressed %1d% button %2d%` |
| 0E3F | 9 | `convert_3d_to_screen_2d %1d% %2d% %3d% checkNearClip %4d% checkFarClip %5d% store_2d_to %6d% %7d% size_to %8d% %9d%` |
| 0E40 | 1 | `get_current_hour %1d%` |
| 0E41 | 1 | `get_current_minute %1d%` |
| 0E42 | 2 | `is_char_doing_task_id %1d% %2d%` |
| 0E43 | 3 | `get_char_task_pointer_by_id %1d% %2d% store_to %3d%` |
| 0E44 | 2 | `get_char_kill_target_char %1d% store_to %2d%` |
| 0E45 | 1 | `frame_mod %1d%` |
| 0E46 | 1 | `is_char_using_gun %1d%` |
| 0E47 | 1 | `is_char_fighting %1d%` |
| 0E48 | 1 | `is_char_fallen_on_ground %1d%` |
| 0E49 | 1 | `is_char_entering_any_car %1d%` |
| 0E4A | 1 | `is_char_exiting_any_car %1d%` |
| 0E4B | 2 | `is_char_playing_any_script_animation %1d% include_anims %2d%` |
| 0E4C | 2 | `is_char_doing_any_important_task %1d% include_anims %2d%` |
| 0E4D | 1 | `random_percent %1d%` |
| 0E4E | 2 | `display_onscreen_timer_local %1d% direction %2d%` |
| 0E4F | 3 | `display_onscreen_timer_with_string_local %1d% direction %2d% GXT %3d%` |
| 0E50 | 2 | `display_onscreen_counter_local %1d% direction %2d%` |
| 0E51 | 3 | `display_onscreen_counter_with_string_local %1d% direction %2d% GXT %3d%` |
| 0E52 | 2 | `display_two_onscreen_counters_local %1d% max_value %2d%` |
| 0E53 | 3 | `display_two_onscreen_counters_with_string_local %1d% max_value %2d% GXT %3d%` |
| 0E54 | 1 | `clear_onscreen_timer_local %1d%` |
| 0E55 | 1 | `clear_onscreen_counter_local %1d%` |
| 0E56 | 2 | `set_onscreen_counter_flash_when_first_displayed_local %1d% flash %2d%` |
| 0E57 | 2 | `set_timer_beep_countdown_time_local %1d% secs %2d%` |
| 0E58 | 2 | `set_onscreen_counter_colour_local %1d% color %2d%` |
| 0E59 | 2 | `get_trailer_from_car %1d% trailer %2d%` |
| 0E5A | 2 | `get_car_from_trailer %1d% store_to %2d%` |
| 0E5B | 7 | `get_car_dummy_coord %1d% dummy %2d% world_coords %3d% invert_x %4d% store_to %5d% %6d% %7d% // same as NewOpcodes but this is adapted to VehFuncs` |
| 0E5C | 2 | `get_player_health_percent %1d% store_to %2d%` |
| 0E5D | 1 | `is_cheat_active %1d%` |
| 0E5E | 3 | `change_player_money %1d% mode %2d% value %3d%` |
| 0E5F | 1 | `car_horn %1d%` |
| 0E60 | 1 | `set_camera_control %1d%` |
| 0E61 | 2 | `set_car_alarm %1d% mode %2d%` |
| 0E62 | 8 | `print %1s% event %2d% at %3d% %4d% scale %5d% %6d% fixAR %7d% style %8d%` |
| 0E63 | 27 | `print %1s% event %2d% at %3d% %4d% scale %5d% %6d% fixAR %7d% style %8d% prop %9d% align %10d% wrap %11d% justify %12d% color %13d% %14d% %15d% %16d% outline %17d% shadow %18d% dropColor %19d% %20d% %21d% %22d% background %23d% backColor %24d% %25d% %26d% %27d%` |
| 0E64 | 1 | `get_camera_mode %1d%` |
| 0E65 | 2 | `get_car_collision_intensity %1d% store_to %2d%` |
| 0E66 | 4 | `get_car_collision_coordinates %1d% store_to %2d% %3d% %4d%` |
| 0E67 | 1 | `is_aim_button_pressed %1d%` |
| 0E68 | 2 | `set_player_control_pad %1d% %2d%` |
| 0E69 | 2 | `set_player_control_pad_movement %1d% %2d%` |
| 0E6A | 2 | `make_nop %1d% size %2d%` |
| 0E6B | 3 | `get_colpoint_lighting %1d% from_night %2d% store_to %3d%` |
| 0E6C | 1 | `get_day_night_balance %1d%` |
| 0E6D | 1 | `get_underwaterness %1d%` |
| 0E6E | 0 | `is_select_menu_just_pressed` |
| 0E6F | -1 | `stream_custom_script_from_label %1p%` |
| 0E70 | 1 | `get_last_created_custom_script %1d%` |
| 0E71 | 2 | `get_object_centre_of_mass_to_base_of_model %1d% %2d%` |
| 0E72 | 2 | `create_list %1d% store_to %2d%` |
| 0E73 | 1 | `delete_list %1d%` |
| 0E74 | 2 | `list_add %1d% value %2d%` |
| 0E75 | 2 | `list_remove_value %1d% value %2d%` |
| 0E76 | 2 | `list_remove_index %1d% index %2d%` |
| 0E77 | 2 | `get_list_size %1d% size %2d%` |
| 0E78 | 3 | `get_list_value_by_index %1d% index %2d% store_to %3d%` |
| 0E79 | 1 | `reset_list %1d%` |
| 0E7A | 3 | `get_list_string_value_by_index %1d% index %2d% store_to %3d%` |
| 0E7B | 2 | `list_add_string %1d% value %2d%` |
| 0E7C | 2 | `list_remove_string_value %1d% value %2d%` |
| 0E7D | 3 | `list_remove_index %1d% start %2d% end %3d%` |
| 0E7E | 1 | `reverse_list %1d%` |
| 0E7F | 2 | `get_model_type %1d% store_to %2d%` |
| 0E80 | 5 | `is_string_equal %1s% %2s% max_size %3d% case_sensitive %4d% ignore_charactere %5s%` |
| 0E81 | 1 | `is_string_comment %1s%` |
| 0E82 | 2 | `does_car_have_part_node %1d% %2d%` |
| 0E83 | 2 | `get_current_char_weaponinfo %1d% store_to %2d%` |
| 0E84 | 3 | `get_weaponinfo %1d% skill %2d% store_to %3d%` |
| 0E85 | 3 | `get_weaponinfo_models %1d% store_to %2d% %3d%` |
| 0E86 | 2 | `get_weaponinfo_flags %1d% store_to %2d%` |
| 0E87 | 2 | `get_weaponinfo_animgroup %1d% store_to %2d%` |
| 0E88 | 2 | `get_weaponinfo_total_clip %1d% store_to %2d%` |
| 0E89 | 2 | `get_weaponinfo_fire_type %1d% store_to %2d%` |
| 0E8A | 2 | `get_weaponinfo_slot %1d% store_to %2d%` |
| 0E8B | 2 | `get_char_weapon_state %1d% store_to %2d%` |
| 0E8C | 2 | `get_char_weapon_clip %1d% store_to %2d%` |
| 0E8D | 1 | `is_any_fire_button_pressed %1d%` |
| 0E8E | 2 | `get_char_collision_surface %1d% store_to %2d%` |
| 0E8F | 2 | `get_char_collision_lighting %1d% store_to %2d%` |
| 0E90 | 2 | `get_car_collision_surface %1d% store_to %2d%` |
| 0E91 | 2 | `get_car_collision_lighting %1d% store_to %2d%` |
| 0E92 | 1 | `is_char_really_in_air %1d%` |
| 0E93 | 1 | `is_car_really_in_air %1d%` |
| 0E94 | 1 | `is_object_really_in_air %1d%` |
| 0E95 | 3 | `simulate_object_damage %1d% damage %2d% type %3d%` |
| 0E96 | 1 | `clear_char_primary_tasks %1d%` |
| 0E97 | 1 | `clear_char_secondary_tasks %1d%` |
| 0E98 | 1 | `request_priority_model %1d%` |
| 0E99 | 0 | `load_all_priority_models_now` |
| 0E9A | 2 | `load_special_character_for_id %1d% name %2d%` |
| 0E9B | 1 | `unload_special_character_from_id %1d%` |
| 0E9C | 2 | `get_model_by_name %1d% store_id %2d%` |
| 0E9D | 1 | `get_model_available_by_name %1d%` |
| 0E9E | 3 | `get_model_available_by_name %1d% to %2d% store_to %3d%` |
| 0E9F | 0 | `remove_all_unused_models` |
| 0EA0 | 3 | `set_actor_second_player %1d% enable_camera %2d% separate_cars %3d%` |
| 0EA1 | 1 | `disable_second_player_restore_camera %1d%` |
| 0EA2 | 1 | `fix_two_players_separated_cars %1d%` |
| 0EA3 | 1 | `remove_model_if_unused %1d%` |
| 0EA4 | 1 | `is_char_on_fire %1d%` |
| 0EA5 | 7 | `get_closest_cop_near_char %1d% radius %2d% alive %3d% in_car %4d% on_foot %5d% seen_in_front %6d% store_to %7d%` |
| 0EA6 | 8 | `get_closest_cop_near_char %1d% %2d% %3d% radius %4d% alive %5d% in_car %6d% on_foot %7d% store_to %8d%` |
| 0EA7 | 3 | `get_any_char_no_save_recursive %1d% progress_to %2d% char_to %3d%` |
| 0EA8 | 3 | `get_any_car_no_save_recursive %1d% progress_to %2d% car_to %3d%` |
| 0EA9 | 3 | `get_any_object_no_save_recursive %1d% progress_to %2d% object_to %3d%` |
| 0EAA | 1 | `set_char_arrested %1d%` |
| 0EAB | 2 | `get_char_pedstate %1d% store_to %2d%` |
| 0EAC | 6 | `get_char_proofs %1d% bullet %2d% fire %3d% explosion %4d% collision %5d% melee %6d%` |
| 0EAD | 6 | `get_car_proofs %1d% bullet %2d% fire %3d% explosion %4d% collision %5d% melee %6d%` |
| 0EAE | 6 | `get_object_proofs %1d% bullet %2d% fire %3d% explosion %4d% collision %5d% melee %6d%` |
| 0EAF | 1 | `is_char_weapon_visible_set %1d%` |
| 0EB0 | 1 | `get_forced_weather %1d%` |
| 0EB1 | 2 | `get_char_stat_id %1d% store_to %2d%` |
| 0EB2 | 6 | `get_offset_from_camera_in_world_coords %1d% %2d% %3d% store_to %4d% %5d% %6d%` |
| 0EB3 | 4 | `convert_direction_to_quat %1d% dir %2d% %3d% %4d%` |
| 0EB4 | 4 | `set_car_coordinates_simple %1d% position %2d% %3d% %4d%` |
| 0EB5 | 5 | `get_char_damage_last_frame %1d% damager %2d% type %3d% part %4d% intensity %5d%` |
| 0EB6 | 4 | `get_car_weapon_damage_last_frame %1d% char %2d% type %3d% intensity %4d%` |
| 0EB7 | 0 | `is_on_scripted_cutscene` |
| 0EB8 | 0 | `is_radar_visible` |
| 0EB9 | 0 | `is_hud_visible` |
| 0EBA | 3 | `get_model_ped_type_and_stat %1d% store_to %2d% %3d%` |
| 0EBB | 1 | `pass_time %1d%` |
| 0EBC | 4 | `generate_random_int_in_range_with_seed %1d% min %2d% max %3d% store_to %4d%` |
| 0EBD | 4 | `generate_random_float_in_range_with_seed %1d% min %2d% max %3d% store_to %4d%` |
| 0EBE | 4 | `locate_camera_distance_to_coordinates %1d% %2d% %3d% radius %4d%` |
| 0EBF | 2 | `get_fx_system_pointer %1d% store_to %2d%` |
| 0EC0 | 14 | `add_fx_system_particle %1d% coord %2d% %3d% %4d% vel %5d% %6d% %7d% size %8d% brightness %9d% rgba %10d% %11d% %12d% %13d% lastFactor %14d%` |
| 0EC1 | 1 | `is_fx_system_available_with_name %1s%` |
| 0EC2 | 1 | `set_string_upper %1s%` |
| 0EC3 | 1 | `set_string_lower %1s%` |
| 0EC4 | 4 | `string_find %1d% %2s% %3s% store_to %4d%` |
| 0EC5 | 2 | `cut_string_at %1d% %2d%` |
| 0EC6 | 3 | `is_string_character_at %1d% character %2d% index %3d%` |
| 0EC7 | 1 | `get_fade_alpha %1d%` |
| 0EC8 | 2 | `get_char_random_seed %1d% store_to %2d%` |
| 0EC9 | 2 | `get_car_random_seed %1d% store_to %2d%` |
| 0ECA | 2 | `get_object_random_seed %1d% store_to %2d%` |
| 0ECB | 2 | `get_char_move_state %1d% store_to %2d%` |
| 0ECC | 2 | `dont_delete_char_until_time %1d% %2d%` |
| 0ECD | 2 | `dont_delete_car_until_time %1d% %2d%` |
| 0ECE | 2 | `get_time_char_is_dead %1d% store_to %2d%` |
| 0ECF | 2 | `get_time_car_is_dead %1d% store_to %2d%` |
| 0ED0 | 0 | `return_script_event` |
| 0ED1 | 3 | `set_script_event_save_confirmation %1d% label %2p% var_slot %3d%` |
| 0ED2 | 3 | `set_script_event_char_delete %1d% label %2p% var_char %3d%` |
| 0ED3 | 3 | `set_script_event_char_create %1d% label %2p% var_char %3d%` |
| 0ED4 | 3 | `set_script_event_car_delete %1d% label %2p% var_car %3d%` |
| 0ED5 | 3 | `set_script_event_car_create %1d% label %2p% var_car %3d%` |
| 0ED6 | 3 | `set_script_event_object_delete %1d% label %2p% var_object %3d%` |
| 0ED7 | 3 | `set_script_event_object_create %1d% label %2p% var_object %3d%` |
| 0ED8 | 3 | `set_script_event_on_menu %1d% label %2p% var_just_paused %3d%` |
| 0ED9 | 2 | `set_char_ignore_damage_anims %1d% %2d%` |
| 0EDA | 3 | `set_script_event_char_process %1d% label %2p% var_char %3d%` |
| 0EDB | 3 | `set_script_event_car_process %1d% label %2p% var_car %3d%` |
| 0EDC | 3 | `set_script_event_object_process %1d% label %2p% var_object %3d%` |
| 0EDD | 3 | `set_script_event_building_process %1d% label %2p% var_building %3d%` |
| 0EDE | 3 | `set_script_event_char_damage %1d% label %2p% var_char %3d%` |
| 0EDF | 3 | `set_script_event_car_weapon_damage %1d% label %2p% var_car %3d%` |
| 0EE0 | 6 | `set_script_event_bullet_impact %1d% label %2p% var_owner %3d% var_victim %4d% var_weapon %5d% var_colpoint %6d%` |
| 0EE1 | 4 | `get_colpoint_coordinates %1d% store_to %2d% %3d% %4d%` |
| 0EE2 | -1 | `read_struct_offset_multi %1d% offset %2d% total %3d% size %4d%` |
| 0EE3 | -1 | `write_struct_offset_multi %1d% offset %2d% count %3d% size %4d%` |
| 0EE4 | 3 | `locate_char_distance_to_char %1d% char %2d% radius %3d%` |
| 0EE5 | 3 | `locate_char_distance_to_car %1d% car %2d% radius %3d%` |
| 0EE6 | 3 | `locate_char_distance_to_object %1d% object %2d% radius %3d%` |
| 0EE7 | 3 | `locate_car_distance_to_object %1d% object %2d% radius %3d%` |
| 0EE8 | 3 | `locate_car_distance_to_car %1d% car %2d% radius %3d%` |
| 0EE9 | 3 | `locate_object_distance_to_object %1d% object %2d% radius %3d%` |
| 0EEA | 5 | `locate_char_distance_to_coordinates %1d% pos %2d% %3d% %4d% radius %5d%` |
| 0EEB | 5 | `locate_car_distance_to_coordinates %1d% pos %2d% %3d% %4d% radius %5d%` |
| 0EEC | 5 | `locate_object_distance_to_coordinates %1d% pos %2d% %3d% %4d% radius %5d%` |
| 0EED | 3 | `locate_entity_distance_to_entity %1d% entityB %2d% radius %3d%` |
| 0EEE | 4 | `get_entity_coordinates %1d% store_to %2d% %3d% %4d%` |
| 0EEF | 2 | `get_entity_heading %1d% store_to %2d%` |
| 0EF0 | 6 | `get_coord_from_angled_distance %1d% %2d% angle %3d% dist %4d% store_to %5d% %6d%` |
| 0EF1 | 8 | `perlin_noise_fractal_2d x %1d% y %2d% octaves %3d% frequency %4d% amplitude %5d% lacunarity %6d% persistence %7d% store_to %8d%` |
| 0EF2 | 9 | `perlin_noise_fractal_3d x %1d% y %2d% z %3d% octaves %4d% frequency %5d% amplitude %6d% lacunarity %7d% persistence %8d% store_to %9d%` |
| 0EF3 | 4 | `lerp %1d% %2d% %3d% store_to %4d%` |
| 0EF4 | 4 | `clamp_float %1d% min %2d% max %3d% store_to %4d%` |
| 0EF5 | 1 | `is_car_owned_by_player %1d%` |
| 0EF6 | 2 | `set_car_owned_by_player %1d% %2d%` |
| 0EF7 | 4 | `clamp_int %1d% min %2d% max %3d% store_to %4d%` |
| 0EF8 | 2 | `get_model_info %1d% store_to %2d%` |
| 0EF9 | 2 | `get_car_animgroup %1d% store_to %2d%` |
| 0EFA | 2 | `get_char_fear %1d% store_to %2d%` |
| 0EFB | 1 | `is_car_convertible %1d%` |
| 0EFC | 2 | `get_car_value %1d% store_to %2d%` |
| 0EFD | 3 | `get_car_pedals %1d% gas_to %2d% break_to %3d%` |
| 0EFE | 2 | `get_loaded_library %1d% store_to %2d%` |
| 0EFF | 3 | `get_char_simplest_active_task %1d% id_to %2d% pointer_to %3d%` |
| 0F00 | 3 | `load_special_model_dff %1s% txd %2s% store_to %3d%` |
| 0F01 | 1 | `remove_special_model %1d%` |
| 0F02 | 10 | `create_render_object_to_char_bone_from_special %1d% special_model %2d% bone %3d% offset %4d% %5d% %6d% rotation %7d% %8d% %9d% scale %10d% %11d% %12d% store_to %13d%` |
| 0F03 | 9 | `create_render_object_to_object %1d% model %2d% offset %3d% %4d% %5d% rotation %6d% %7d% %8d% store_to %9d%` |
| 0F04 | 9 | `create_render_object_to_object_from_special %1d% special_model %2d% offset %3d% %4d% %5d% rotation %6d% %7d% %8d% store_to %9d%` |
| 0F05 | 4 | `get_special_model_data %1d% clump_to %2d% atomic_to %3d% txd_index_to %4d%` |
| 0F06 | 3 | `replace_list_value_by_index %1d% index %2d% value %3d%` |
| 0F07 | 3 | `replace_list_string_value_by_index %1d% index %2d% value %3d%` |
| 0F08 | 3 | `insert_list_value_by_index %1d% index %2d% value %3d%` |
| 0F09 | 3 | `insert_list_string_value_by_index %1d% index %2d% value %3d%` |
| 0F0A | 1 | `return_times %1d%` |
| 0F0B | 2 | `set_script_event_before_game_process %1d% label %2p%` |
| 0F0C | 2 | `set_script_event_after_game_process %1d% label %2p%` |
| 0F0D | 7 | `set_matrix_look_direction %1d% origin %2d% %3d% %4d% dir %5d% %6d% %7d%` |
| 0F0E | 10 | `get_third_person_camera_target %1d% from %2d% %3d% %4d% start_to %5d% %6d% %7d% end_to %8d% %9d% %10d%` |
| 0F0F | 2 | `get_distance_multiplier %1d% %2d%` |
| 0F10 | 3 | `get_active_camera_rotation %1d% %2d% %3d%` |
| 0F11 | 2 | `get_closest_water_distance %1d% %2d%` |
| 0F12 | 2 | `get_camera_struct %1d% %2d%` |
| 0F13 | 2 | `get_time_not_touching_pad %1d% store_to %2d%` |
| 0F14 | 2 | `get_camera_rotation_input_values %1d% %2d%` |
| 0F15 | 2 | `set_camera_rotation_input_values %1d% %2d%` |
| 0F16 | 1 | `set_on_mission %1d%` |
| 0F17 | 2 | `get_model_name_pointer %1d% to %2s%` |

## 附表 C：SA 新 opcode（SASCM.NewOpcodes.ini）
| opcode | 参数数 | 签名 |
|---|---|---|
| 0D00 | 3 | `matrix %3d% = matrix %1d% * matrix %2d%` |
| 0D01 | 6 | `rotate_matrix %1d% on_axis %2d% %3d% %4d% angle %5d% combine_op %6d%` |
| 0D02 | 2 | `%2d% = matrix %1d% x_angle` |
| 0D03 | 2 | `%2d% = matrix %1d% y_angle` |
| 0D04 | 2 | `%2d% = matrix %1d% z_angle` |
| 0D05 | 4 | `set_matrix %1d% position %2d% %3d% %4d%` |
| 0D06 | 4 | `get_matrix %1d% position_to %2d% %3d% %4d%` |
| 0D07 | 7 | `get_coords %1d% %2d% %3d% offsets_relative_to_matrix %4d% store_to %5d% %6d% %7d%` |
| 0D08 | 4 | `set_matrix %1d% angles_XYZ %2d% %3d% %4d%` |
| 0D09 | 2 | `copy_matrix %1d% to %2d%` |
| 0D0A | 7 | `store_coords_to %5d% %6d% %7d% from_matrix %1d% with_offsets %2d% %3d% %4d%` |
| 0D0B | 3 | `get_actor %1d% bone %2d% matrix_to %3d% // IF and SET` |
| 0D0C | 3 | `get_car %1d% component %2s% matrix_to %3d% // IF and SET` |
| 0D0D | 3 | `%3d% = get_car %1d% component %2s% // IF and SET` |
| 0D0E | 3 | `set_car %1d% component %2s% state %3d% // IF and SET` |
| 0D0F | 2 | `set_car %1d% model_alpha %2d% // IF and SET` |
| 0D10 | 2 | `set_actor %1d% model_alpha %2d% // IF and SET` |
| 0D11 | 2 | `set_object %1d% model_alpha %2d% // IF and SET` |
| 0D12 | 3 | `set_car %1d% component %2s% alpha %3d% // IF and SET` |
| 0D13 | 2 | `set_matrix %1d% x_angle %2d%` |
| 0D14 | 2 | `set_matrix %1d% y_angle %2d%` |
| 0D15 | 2 | `set_matrix %1d% z_angle %2d%` |
| 0D16 | 2 | `set_matrix %1d% rotation_from_quat %2d%` |
| 0D17 | 2 | `convert_matrix %1d% to_quat %2d%` |
| 0D18 | 6 | `rotate_quat %1d% axis_vector %2d% %3d% %4d% angle %5d% flag %6d%` |
| 0D19 | 2 | `get_normalized_quat %1d% to_quat %2d%` |
| 0D1A | 3 | `quat %3d% = quat %1d% * quat %2d%` |
| 0D1B | 3 | `get_entity %1d% type_to %2d% class_to %3d%` |
| 0D1C | 1 | `normalize_vector %1d%` |
| 0D1D | 4 | `matrix_slerp %1d% %2d% %3d% %4d%` |
| 0D1E | 4 | `quat_slerp %1d% %2d% %3d% %4d%` |
| 0D1F | 2 | `%2d% = component %1d% child` |
| 0D20 | 2 | `%2d% = component %1d% next_component` |
| 0D21 | 2 | `%2s% = component %1d% name` |
| 0D22 | 2 | `%2d% = component %1d% ltm` |
| 0D23 | 2 | `%2d% = component %1d% modelling_matrix` |
| 0D24 | 5 | `set_quat %1d% elements %2d% %3d% %4d% %5d%` |
| 0D25 | 17 | `set_matrix %1d% elements %2d% %3d% %4d% %5d% %6d% %7d% %8d% %9d% %10d% %11d% %12d% %13d% %14d% %15d% %16d% %17d%` |
| 0D26 | 4 | `set_vector %1d% elements %2d% %3d% %4d%` |
| 0D27 | 3 | `copy_memory_from %1d% to %2d% size %3d%` |
| 0D28 | 4 | `get_vector %1d% elements_to %2d% %3d% %4d%` |
| 0D29 | 5 | `get_quat %1d% elements_to %2d% %3d% %4d% %5d%` |
| 0D2A | 2 | `%2d% = get_car %1d% number_of_collided_entites` |
| 0D2B | 2 | `%2d% = get_actor %1d% number_of_collided_entites` |
| 0D2C | 2 | `%2d% = get_object %1d% number_of_collided_entites` |
| 0D2D | 8 | `get_local_time_year_to %1d% month_to %2d% day_of_week_to %3d% day_to %4d% hour_to %5d% minute_to %6d% second_to %7d% milliseconds_to %8d%` |
| 0D2E | 3 | `set_script %1d% var %2d% to %3d%` |
| 0D2F | 3 | `%3d% = get_script %1d% var %2d%` |
| 0D30 | 3 | `%3d% = actor %1d% bone %2d% // IF and SET` |
| 0D31 | 2 | `%2d% = bone %1d% offset_vector` |
| 0D32 | 2 | `%2d% = bone %1d% quat` |
| 0D33 | 3 | `set_car %1d% door %2d% window_state %3d%` |
| 0D34 | 7 | `store_car %1d% collided_entities_to %2d% %3d% %4d% %5d% %6d% %7d%` |
| 0D35 | 7 | `store_actor %1d% collided_entities_to %2d% %3d% %4d% %5d% %6d% %7d%` |
| 0D36 | 7 | `store_object %1d% collided_entities_to %2d% %3d% %4d% %5d% %6d% %7d%` |
| 0D37 | 3 | `struct %1d% param %2d% = %3d%` |
| 0D38 | 3 | `%3d% = struct %1d% param %2d%` |
| 0D39 | 2 | `%2d% = actor %1d% max_health` |
| 0D3A | 20 | `get_collision_between_points %1d% %2d% %3d% and %4d% %5d% %6d% flags %7d% %8d% %9d% %10d% %11d% %12d% %13d% %14d% ignore_entity %15d% store_point_to %17d% %18d% %19d% entity_to %20d% colpoint_data_to %16d% // IF and SET` |
| 0D3B | 4 | `get_colpoint_data %1d% normal_XYZ_to %2d% %3d% %4d%` |
| 0D3C | 2 | `get_colpoint_data %1d% surface_to %2d%` |
| 0D3D | 2 | `get_colpoint_data %1d% lighting_to %2d%` |
| 0D3E | 2 | `get_colpoint_data %1d% depth_to %2d%` |
| 0D3F | 10 | `find_intersrction_between_circles %1d% %2d% %3d% and %4d% %5d% %6d% store_point1_to %7d% %8d% point2_to %9d% %10d% // IF and SET` |
| 0D40 | 8 | `draw_2d_shape_type %3d% texture %4d% numVerts %2d% pVerts %1d% vertexAlpha %5d% srcBlend %6d% dstBlend %7d% priority %8d% // IF and SET` |
| 0D41 | 14 | `set_vertices %1d% vertex %2d% xyz %5d% %6d% %7d% rhw %8d% RGBA %9d% %10d% %11d% %12d% uv %13d% %14d% invertX %3d% invertY %4d%` |
| 0D42 | 2 | `load_txd %1s% from %2s% // IF and SET` |
| 0D43 | 2 | `%2d% = txd %1s% id` |
| 0D44 | 3 | `%3d% = find_texture %1s% in_dictionary_named %2s% // IF and SET` |
| 0D45 | 5 | `rotate_2d_vertices_shape %1d% num_verts %2d% aroundXY %3d% %4d% angle %5d%` |
| 0D46 | 3 | `%3d% = find_texture %1s% in_dictionary_with_id %2d% // IF and SET` |
| 0D47 | 2 | `%2d% = model %1d% txd_id // IF and SET` |
| 0D48 | 2 | `%2d% = model %1d% crc32_key // IF and SET` |
| 0D49 | 3 | `%3d% = compare_strings %1s% %2s% // IF and SET` |
| 0D4A | 2 | `concatenate_strings %1d% %2s%` |
| 0D4B | 3 | `%3d% = locate_substring %1d% %2s% // IF and SET` |
| 0D4C | 2 | `%2d% = string %1s% length` |
| 0D4D | 2 | `copy_string %1s% to %2s%` |
| 0D4E | 4 | `%4d% = struct %1d% offset %2d% size %3d%` |
| 0D4F | 4 | `struct %1d% offset %2d% size %3d% = %4d%` |
| 0D50 | 14 | `draw_shadow_type %1d% position %2d% %3d% %4d% width %5d% height %6d% rotation %7d% distance %8d% texture %9d% intensity %10d% RGB %11d% %12d% %13d% shadow_data %14d%` |
| 0D51 | 14 | `draw_permanent_shadow_type %1d% position %2d% %3d% %4d% width %5d% height %6d% rotation %7d% distance %8d% texture %9d% intensity %10d% RGB %11d% %12d% %13d% time %14d%` |
| 0D52 | 12 | `draw_light_type %1d% position %2d% %3d% %4d% direction %5d% %6d% %7d% radius %8d% RGBA %9d% %10d% %11d% affect_entity %12d%` |
| 0D53 | 10 | `draw_corona_with_texture %1d% color %2d% %3d% %4d% %5d% on_entity %6d% at %7d% %8d% %9d% size %10d%` |
| 0D54 | 18 | `draw_corona_with_extra_params_texture %1d% color %2d% %3d% %4d% %5d% on_entity %6d% at %7d% %8d% %9d% size %10d% far_clip %11d% near_clip %12d% flare %13d% enable_reflection %14d% check_obstacles %15d% flash_while_fading %16d% fade_speed %17d% only_from_below %18d%` |
| 0D55 | 6 | `get_sun_colors_core_to %1d% %2d% %3d% glow_to %4d% %5d% %6d%` |
| 0D56 | 2 | `get_sun_screen_coords_XY_to %1d% %2d%` |
| 0D57 | 3 | `get_sun_position_to %1d% %2d% %3d% // IF and SET` |
| 0D58 | 2 | `get_sun_size_core_to %1d% glow_to %2d%` |
| 0D59 | 1 | `%1d% = current_weather` |
| 0D5A | 2 | `get_trafficlights_type_NS_current_color_to %1d% type_WE_current_color_to %2d%` |
| 0D5B | 12 | `draw_spotlight_from %1d% %2d% %3d% to %4d% %5d% %6d% base_radius %7d% target_radius %8d% enable_shadow %9d% shadow_intensity %10d% flag1 %11d% flag2 %12d%` |
| 0D5C | 3 | `%3d% = get_car %1d% light %2d% damage_state` |
| 0D5D | 3 | `set_car %1d% light %2d% damage_state %3d%` |
| 0D5E | 3 | `get_vehicle %1d% class_to %2d% subclass_to %3d%` |
| 0D5F | 7 | `get_vehicle %1d% dummy_element %2d% position %3d% to %5d% %6d% %7d% invert_x %4d% // IF and SET` |
| 0D60 | 10 | `create_projectile_type %1d% launched_from_entity %2d% origin %3d% %4d% %5d% target %6d% %7d% %8d% target_entity %9d% force %10d%` |
| 0D61 | 3 | `%3d% = load_texture_bmp_from %1s% with_mask %2s% // IF and SET` |
| 0D64 | 2 | `%2d% = load_texture_png_from %1s% // IF and SET` |
| 0D65 | 6 | `print %1s% at %2d% %3d% scale %4d% %5d% style %6d%` |
| 0D66 | 25 | `print %1s% at %2d% %3d% scale %4d% %5d% style %6d% prop %7d% align %8d% wrap %9d% justify %10d% color %11d% %12d% %13d% %14d% outline %15d% shadow %16d% dropColor %17d% %18d% %19d% %20d% background %21d% backColor %22d% %23d% %24d% %25d%` |
| 0D72 | 3 | `get_sfx_volume_to %2d% radio_volume_to %3d% type %1d%` |
| 0D73 | 3 | `get_screen_width_to %2d% height_to %3d% type %1d%` |
| 0D74 | 2 | `%2d% = component %1d% parent_component` |
| 0D75 | 2 | `%2d% = component %1d% num_objects` |
| 0D76 | 3 | `%3d% = component %1d% object %2d%` |
| 0D77 | 2 | `object_atomic %1d% hide %2d%` |
| 0D78 | 3 | `%3d% = get_object %1d% atomic_flag %2d%` |
| 0D79 | 3 | `set_object %1d% atomic_flag %2d% state %3d%` |
| 0D7A | 2 | `%2d% = get_object %1d% num_materials` |
| 0D7B | 3 | `%3d% = get_object %1d% material %2d% texture` |
| 0D7C | 2 | `%2d% = load_texture_dds_from %1s% // IF and SET` |
| 0D7D | 1 | `clean_loaded_texture %1d%` |
| 0D7E | 10 | `draw_sprite_with_texture %1d% at_cornerA %2d% %3d% cornerB %4d% %5d% color %6d% %7d% %8d% %9d% angle %10d%` |
| 0D7F | 22 | `draw_gradient_sprite_with_texture %1d% at_cornerA %2d% %3d% cornerB %4d% %5d% colors %6d% %7d% %8d% %9d%  %10d% %11d% %12d% %13d%  %14d% %15d% %16d% %17d%  %18d% %19d% %20d% %21d% angle %22d%` |

## 附表 D：GTASA Mobile 特有（SASCM.Mobile.ini）
| opcode | 参数数 | 签名 |
|---|---|---|
| 0242 | 2 | `arm_car_with_bomb %1d% %2d%` |
| 0A4F | 0 | `NOP_false` |
| 0A50 | 0 | `increment_useless_flag` |
| 0A51 | 1 | `is_widget_pressed %1d%` |
| 0A52 | 1 | `is_widget_released %1d%` |
| 0A53 | 1 | `is_widget_doubletapped %1d%` |
| 0A54 | 1 | `is_widget_swiped %1d%` |
| 0A55 | 1 | `is_widget_swiped_left %1d%` |
| 0A56 | 1 | `is_widget_swiped_right %1d%` |
| 0A57 | 2 | `do_mission_skip_get_current_mission_page %1d% get_current_skip_to_mission_number %2d%` |
| 0A58 | 1 | `skip_to_mission_num %1d%` |
| 0A59 | 1 | `skip_to_mission_page %1d%` |
| 0A5A | 2 | `get_widget %1d% value_to %2d% ; float` |
| 0A5B | 3 | `get_widget %1d% value2_to %2d% %3d% ; float` |
| 0A5C | 3 | `display_missions_text_position %1d% %2d% GXT %3g%` |
| 0A5D | 5 | `get_widget %1d% position_to %2d% %3d% scale_to %4d% %5d%` |
| 0A5E | 2 | `set_widget %1d% value %2d% ; float` |
| 0A5F | 3 | `set_widget %1d% slider_range %2d% %3d% ; float` |
| 0A60 | 2 | `widget %1d% add_flag %2d%` |
| 0A61 | 2 | `widget %1d% remove_flag %2d%` |
| 0A62 | 2 | `widget %1d% add_button_flag %2d%` |
| 0A63 | 2 | `widget %1d% remove_button_flag %2d%` |
| 0A64 | 0 | `is_touch_enabled` |
| 0A65 | 0 | `skip_intro_cutscene` |
| 0A66 | 1 | `write_log %1k% ; 128-byte null-terminated string` |
| 0A67 | 4 | `write_log_int %1d% %2d% %3d% %4k% ; 128-byte null-terminated string` |
| 0A68 | 4 | `write_log_float %1d% %2d% %3d% %4k% ; 128-byte null-terminated string` |
| 0A69 | 1 | `create_shop_widget_menu %1g%` |
| 0A6A | 2 | `add_shop_widget_menu_item %1g% price %2d%` |
| 0A6B | 1 | `delete_widget %1d%` |
| 0A6C | 2 | `set_widget %1d% equipped_item %2d%` |
| 0A6D | 2 | `print_help_forever_conditional %1g% hid_type %2d%` |
| 0A6E | 2 | `set_widget %1d% texture %2k%  ; 128-byte null-terminated string` |
| 0A6F | 1 | `checkpoint_save %1d%` |
| 0A70 | 4 | `display_text_clamped_position %1d% %2d% GXT %3g% scale %4d% ;text_draw` |
| 0A71 | 5 | `display_text_clamped_position %1d% %2d% GXT %3g% with_number %4d% scale %5d% ;text_draw` |
| 0A72 | 0 | `auto_save_checkpoint` |
| 0A73 | 4 | `set_widget %1d% value3 %2d% %3d% %4d% ; float` |
| 0A74 | 1 | `is_checkpoint_resuming %1d% ; IsGameResuming //bool` |
| 0A75 | 2 | `set_active_square_color_panel %1d% find_car_colour %2d%` |
| 0A76 | 1 | `hid_implements %1d%` |
| 0A77 | 1 | `checkpoint_save_oddjob %1d%` |
| 0A78 | 2 | `print_help_forever_conditional_touch %1g% hid_type %2d%` |
| 0A79 | 2 | `print_help_forever_conditional_hid %1g% hid_type %2d%` |
| 0A7A | 2 | `print_help_forever_conditional_touch_classic %1g% hid_type %2d%` |
| 0A7B | 2 | `print_help_forever_conditional_touch_adapted %1g% hid_type %2d%` |
| 0A7C | 2 | `print_help_forever_conditional_hid_joypad %1g% hid_type %2d%` |
| 0A7D | 2 | `print_help_forever_conditional_hid_keyboard %1g% hid_type %2d%` |
| 0A7E | 2 | `print_help_forever_conditional_touch_analog %1g% hid_type %2d%` |
| 0A7F | 2 | `print_help_forever_conditional_touch_digital %1g% hid_type %2d%` |
| 0A80 | 2 | `print_help_forever_conditional_touch_flick %1g% hid_type %2d%` |
| 0A81 | 2 | `print_help_conditional %1g% hid_type %2d%` |
| 0A82 | 2 | `print_help_conditional_touch %1g% hid_type %2d%` |
| 0A83 | 2 | `print_help_conditional_hid %1g% hid_type %2d%` |
| 0A84 | 2 | `print_help_conditional_touch_classic %1g% hid_type %2d%` |
| 0A85 | 2 | `print_help_conditional_touch_adapted %1g% hid_type %2d%` |
| 0A86 | 2 | `print_help_conditional_hid_joypad %1g% hid_type %2d%` |
| 0A87 | 2 | `print_help_conditional_hid_keyboard %1g% hid_type %2d%` |
| 0A88 | 2 | `print_help_conditional_touch_analog %1g% hid_type %2d%` |
| 0A89 | 3 | `set_widget %1d% value2 %2d% %3d% ; float` |
| 0A8A | 8 | `set_widget %1d% info %2d% %3d% %4d% %5d% %6d% %7d% %8g%` |
| 0A8B | 7 | `set_widget %1d% info2 %2d% %3d% %4d% %5d% %6g% %7g%` |
| 0A8C | 0 | `load_all_requested_models ; No time to pause. load faster than 038B` |
| 0A8D | 1 | `is_hid_released %1d%` |
| 0A8E | 1 | `NOP %1d%` |
| 0A8F | 0 | `set_player_weapon_lock_on_target` |
| 0A90 | 1 | `is_hid_pressed %1d%` |

---

## 6.2 CLEO ANDROID（C/A）触摸系统——屏幕九宫格 touch points

> 来源：CLEO 官方 Android 加载器 README（<https://github.com/cleolibrary/CLEO-ANDROID>）。
> **编译器已收录**：SA_MOBILE 模式数据 = SA 基础 + Mobile 触摸（0A51 系列）+ C/A opcode（SASCM.CLEO.ini / SASCM.CA.ini）+ 标准 CLEO 4 全表（SASCM.CLEO.STD.ini，0A8C-0B1D），**全部实测编译通过**。
> 触摸类条件（0DE0/0DE1/0A51 等）不在条件判定表 → if 块内请用旧式 `00D6+004D`（已验证）。

### 9 格触摸区（touch points，ID 1-9）

C/A 把触摸屏均分为 9 个区域，检测"点按"与"滑动"：

```
┌─────────┬─────────┬─────────┐
│   1     │   4     │   7     │   上排
│ LEFT-TOP│ CENTER  │ RIGHT-  │
│         │ -TOP    │ TOP     │
├─────────┼─────────┼─────────┤
│   2     │   5     │   8     │   中排
│ LEFT-   │ CENTER  │ RIGHT-  │
│ CENTER  │         │ CENTER  │
├─────────┼─────────┼─────────┤
│   3     │   6     │   9     │   下排
│ LEFT-   │ CENTER  │ RIGHT-  │
│ BOTTOM  │ -BOTTOM │ BOTTOM  │
└─────────┴─────────┴─────────┘
  左列      中列      右列
```

### 触摸 opcode（C/A）

| opcode | 签名 | 说明 |
|---|---|---|
| 0DE0 | `%1d% = get_touch_point_state %2d% mintime %3d%` | **检测 1-9 号触摸区按住**（id, 最小按住 ms）；返回 0/1 |
| 0DE1 | `%1d% = get_touch_slide_state from %2d% to %3d% mintime %4d% maxtime %5d%` | 检测**滑动**（起点格→终点格，限时窗口） |
| 0DE2 | `%1d% = get_menu_button_state` | 安卓系统菜单键状态 |
| 0DE3 | `%1d% = get_menu_button_pressed mintime %2d%` | 菜单键按下 |

示例（九宫格点按，旧式写法）：
```scm
{$CLEO .csa}
0000:
:L
wait 0
00D6: if 0DE0: 5 = get_touch_point_state 5 mintime 100   ; 正中央格按住 100ms
004D: jump_if_false @L
0ACD: show_text_highpriority 'CLEOOK' time 800
0002: jump @L
```

### C/A 触摸菜单（0DF2-0DF6）

| opcode | 签名 | 说明 |
|---|---|---|
| 0DF2 | `create_menu %1d% items %2d%` | 建菜单（Android 触控 / PSP 按键） |
| 0DF3 | `delete_menu` | 删菜单 |
| 0DF4 | `%1d% = get_menu_touched_item_index maxtime %2d%` | 触摸项索引（0 基；-1 无、-2 关闭菜单） |
| 0DF5 | `set_menu_active_item_index %1d%` | 设活动项 |
| 0DF6 | `%1d% = get_menu_active_item_index` | 读活动项 |

### C/A 平台/内存（0DD0-0DDE）

| opcode | 签名 | 说明 |
|---|---|---|
| 0DD0 | `%1d% = get_label_addr %2p%` | 标签真实地址 |
| 0DD1 | `%1d% = get_func_addr_by_cstr_name %2d%` | 导出函数地址（PSP 恒 0） |
| 0DD5 | `%1d% = get_platform` | Android=1、PSP=2 |
| 0DD6 | `%1d% = get_game_version` | 游戏内部版本 |
| 0DD7 | `%1d% = get_image_base` | 主库镜像基址 |
| 0DD8 | `%1d% = read_mem_addr %2d% size %3d% add_ib %4d%` | 读内存（add_ib=1 加镜像基址） |
| 0DD9 | `write_mem_addr %1d% value %2d% size %3d% add_ib %4d% protect %5d%` | 写内存 |
| 0DDC/0DDD | `set_mutex_var %1d% to %2d%` / `%1d% = get_mutex_var %2d%` | 跨脚本共享变量（id 任意整数） |
| 0DDE | `call_func %1d% add_ib %2d% ...` | 调用游戏函数（'i'/'f'/'ref'/'resi'/'resf'） |
| 1000 | `opcode_func ...` | 插件函数（name 参数） |

### C/A 脚本加载机制（与 PC CLEO 不同）

- 放置：`%sdcard%/cleo/sa/`（iii/vc/sa/lcs 按游戏）
- **`.csa`**：游戏加载即自动启动；**`.csi`**：通过游戏内菜单手动调用（下拉手势呼出菜单）
- 需 root（SA Mobile v1.00-2.00 等；Android 4.1-11）
- PC 脚本常需改写（PC 定向 opcode/控制不适用）

## 7. 全局变量注册（CustomVariables.ini 节选）

```
2=PLAYER_CHAR
3=PLAYER_ACTOR
409=ONMISSION
```
- 字母名全局**必须已注册**，否则报「未知全局变量」。
- `$TIMERA`/`$TIMERB` 等 Sanny 内置名**未注册 → 不可用**；计时器用数字全局自实现：
```scm
0006: $500 = 5000
if 0038: $500 == 0 then ... end   ; 到 0 触发
```

---

## 8. 写法注意事项（实测踩坑）

| 直觉写法 | 结果 | 正确写法 |
|---|---|---|
| `while true` | ❌ | `while 001D: 1 > 0` |
| `#INFERNUS` 模型名 | ❌ | 数字 ID `411` |
| `$TIMERA` 计时器 | ❌ 未知全局 | 数字全局 `$500` |
| `player 0 pressed_key 2`（多词模板名） | ❌ | 完整 HEX 行 `00E1: player 0 pressed_key 2` |
| `if not 00E1: ...`（not+HEX） | ❌ | 0x80 位 `8048:` / 旧式 |
| `0A51` 触点作 if 条件 | ❌ 不在条件判定表 | 旧式 `00D6: if 0A51: ...` + `004D: jump_if_false @L` |
| `0ADC: test_cheat 'STR'` | ❌ 参数是数字 | `0ADC: test_cheat $id` |
| 变参 `0ACE` 等（参数数 -1） | ⚠️ 兼容风险 | 用定参 0ACD/01E3 |
| `terminate_this_custom_script` | ✅ | `0A93:`（标准） |
| if/while 嵌套、then/else/end | ✅ | 条件行写全 HEX |

---

## 9. 完整示例（全部实测编译通过）

### 示例 1：按 A 键显示文本（PC）
```scm
{$CLEO .cs}
0000:
wait 1000
while 001D: 1 > 0
    wait 0
    if
        0AB0:   key_pressed 65
    then
        0ACD: show_text_highpriority 'CLEOOK' time 2000
    end
end
```

### 示例 2：每 5 秒自动加钱（计时器用数字全局）
```scm
{$CLEO .cs}
0000:
0006: $500 = 5000
while 001D: 1 > 0
    wait 0
    if
        0038:   $500 == 0
    then
        0006: $500 = 5000
        0109: player 0 money += 500
    end
end
```

### 示例 3：模型加载 → 玩家面前生成跑车（旧式条件）
```scm
{$CLEO .cs}
0000:
0247: load_model 411
:LOOP
wait 0
00D6: if 0248:   model 411 available
004D: jump_if_false @LOOP
04C4: store_coords_to $501 $502 $503 from_actor $PLAYER_ACTOR with_offset 0.0 3.0 0.0
00A5: $504 = create_car 411 at $501 $502 $503
0249: release_model 411
0175: set_car $504 Z_angle_to 90.0
0A93: terminate_this_custom_script
```

### 示例 4：SA Mobile 触摸 0 号控件 → 提示（触摸类用旧式）
```scm
{$CLEO .csi}
0000:
wait 1000
:LOOP
wait 0
00D6: if 0A51:   is_widget_pressed 0
004D: jump_if_false @LOOP
0ACD: show_text_highpriority 'CLEOOK' time 800
0002: jump @LOOP
```

### 示例 5：INI 读写（实测 0AF1 可编译）
```scm
{$CLEO .cs}
0000:
0AF1: write_int 100 to_ini_file 'CLEO\cfg.ini' section 'main' key 'money'
0AF4: $510 = read_string_from_ini_file 'CLEO\cfg.ini' section 'main' key 'tag'
0A93: terminate_this_custom_script
```

---


---

## 10. 游戏数据速查（GTASA 官方 IDE 数据，ID 供脚本直接使用）

> 车辆/人物/物体数据提取自项目内置游戏 IDE 文件（ID↔名称事实数据）。模型 ID 是脚本里 `0247: load_model 411` 这类写法的数字。

### 10.1 车辆模型 ID（212 辆，ID 400-611）

| ID | 名称 | 类型 |
|---|---|---|
| 400 | LANDSTAL | car |
| 401 | BRAVURA | car |
| 402 | BUFFALO | car |
| 403 | LINERUN | car |
| 404 | PEREN | car |
| 405 | SENTINEL | car |
| 406 | DUMPER | mtruck |
| 407 | FIRETRUK | car |
| 408 | TRASH | car |
| 409 | STRETCH | car |
| 410 | MANANA | car |
| 411 | INFERNUS | car |
| 412 | VOODOO | car |
| 413 | PONY | car |
| 414 | MULE | car |
| 415 | CHEETAH | car |
| 416 | AMBULAN | car |
| 417 | LEVIATHN | heli |
| 418 | MOONBEAM | car |
| 419 | ESPERANT | car |
| 420 | TAXI | car |
| 421 | WASHING | car |
| 422 | BOBCAT | car |
| 423 | MRWHOOP | car |
| 424 | BFINJECT | car |
| 425 | HUNTER | heli |
| 426 | PREMIER | car |
| 427 | ENFORCER | car |
| 428 | SECURICA | car |
| 429 | BANSHEE | car |
| 430 | PREDATOR | boat |
| 431 | BUS | car |
| 432 | RHINO | car |
| 433 | BARRACKS | car |
| 434 | HOTKNIFE | car |
| 435 | ARTICT1 | trailer |
| 436 | PREVION | car |
| 437 | COACH | car |
| 438 | CABBIE | car |
| 439 | STALLION | car |
| 440 | RUMPO | car |
| 441 | RCBANDIT | car |
| 442 | ROMERO | car |
| 443 | PACKER | car |
| 444 | MONSTER | mtruck |
| 445 | ADMIRAL | car |
| 446 | SQUALO | boat |
| 447 | SEASPAR | heli |
| 448 | PIZZABOY | bike |
| 449 | TRAM | train |
| 450 | ARTICT2 | trailer |
| 451 | TURISMO | car |
| 452 | SPEEDER | boat |
| 453 | REEFER | boat |
| 454 | TROPIC | boat |
| 455 | FLATBED | car |
| 456 | YANKEE | car |
| 457 | CADDY | car |
| 458 | SOLAIR | car |
| 459 | TOPFUN | car |
| 460 | SKIMMER | plane |
| 461 | PCJ600 | bike |
| 462 | FAGGIO | bike |
| 463 | FREEWAY | bike |
| 464 | RCBARON | plane |
| 465 | RCRAIDER | heli |
| 466 | GLENDALE | car |
| 467 | OCEANIC | car |
| 468 | SANCHEZ | bike |
| 469 | SPARROW | heli |
| 470 | PATRIOT | car |
| 471 | QUAD | quad |
| 472 | COASTG | boat |
| 473 | DINGHY | boat |
| 474 | HERMES | car |
| 475 | SABRE | car |
| 476 | RUSTLER | plane |
| 477 | ZR350 | car |
| 478 | WALTON | car |
| 479 | REGINA | car |
| 480 | COMET | car |
| 481 | BMX | bmx |
| 482 | BURRITO | car |
| 483 | CAMPER | car |
| 484 | MARQUIS | boat |
| 485 | BAGGAGE | car |
| 486 | DOZER | car |
| 487 | MAVERICK | heli |
| 488 | VCNMAV | heli |
| 489 | RANCHER | car |
| 490 | FBIRANCH | car |
| 491 | VIRGO | car |
| 492 | GREENWOO | car |
| 493 | JETMAX | boat |
| 494 | HOTRING | car |
| 495 | SANDKING | car |
| 496 | BLISTAC | car |
| 497 | POLMAV | heli |
| 498 | BOXVILLE | car |
| 499 | BENSON | car |
| 500 | MESA | car |
| 501 | RCGOBLIN | heli |
| 502 | HOTRINA | car |
| 503 | HOTRINB | car |
| 504 | BLOODRA | car |
| 505 | RNCHLURE | car |
| 506 | SUPERGT | car |
| 507 | ELEGANT | car |
| 508 | JOURNEY | car |
| 509 | BIKE | bmx |
| 510 | MTBIKE | bmx |
| 511 | BEAGLE | plane |
| 512 | CROPDUST | plane |
| 513 | STUNT | plane |
| 514 | PETRO | car |
| 515 | RDTRAIN | car |
| 516 | NEBULA | car |
| 517 | MAJESTIC | car |
| 518 | BUCCANEE | car |
| 519 | SHAMAL | plane |
| 520 | HYDRA | plane |
| 521 | FCR900 | bike |
| 522 | NRG500 | bike |
| 523 | COPBIKE | bike |
| 524 | CEMENT | car |
| 525 | TOWTRUCK | car |
| 526 | FORTUNE | car |
| 527 | CADRONA | car |
| 528 | FBITRUCK | car |
| 529 | WILLARD | car |
| 530 | FORKLIFT | car |
| 531 | TRACTOR | car |
| 532 | COMBINE | car |
| 533 | FELTZER | car |
| 534 | REMINGTN | car |
| 535 | SLAMVAN | car |
| 536 | BLADE | car |
| 537 | FREIGHT | train |
| 538 | STREAK | train |
| 539 | VORTEX | plane |
| 540 | VINCENT | car |
| 541 | BULLET | car |
| 542 | CLOVER | car |
| 543 | SADLER | car |
| 544 | FIRELA | car |
| 545 | HUSTLER | car |
| 546 | INTRUDER | car |
| 547 | PRIMO | car |
| 548 | CARGOBOB | heli |
| 549 | TAMPA | car |
| 550 | SUNRISE | car |
| 551 | MERIT | car |
| 552 | UTILITY | car |
| 553 | NEVADA | plane |
| 554 | YOSEMITE | car |
| 555 | WINDSOR | car |
| 556 | MONSTERA | mtruck |
| 557 | MONSTERB | mtruck |
| 558 | URANUS | car |
| 559 | JESTER | car |
| 560 | SULTAN | car |
| 561 | STRATUM | car |
| 562 | ELEGY | car |
| 563 | RAINDANC | heli |
| 564 | RCTIGER | car |
| 565 | FLASH | car |
| 566 | TAHOMA | car |
| 567 | SAVANNA | car |
| 568 | BANDITO | car |
| 569 | FREIFLAT | train |
| 570 | STREAKC | train |
| 571 | KART | car |
| 572 | MOWER | car |
| 573 | DUNERIDE | mtruck |
| 574 | SWEEPER | car |
| 575 | BROADWAY | car |
| 576 | TORNADO | car |
| 577 | AT400 | plane |
| 578 | DFT30 | car |
| 579 | HUNTLEY | car |
| 580 | STAFFORD | car |
| 581 | BF400 | bike |
| 582 | NEWSVAN | car |
| 583 | TUG | car |
| 584 | PETROTR | trailer |
| 585 | EMPEROR | emperor |
| 586 | WAYFARER | wayfarer |
| 587 | EUROS | car |
| 588 | HOTDOG | car |
| 589 | CLUB | car |
| 590 | FREIBOX | train |
| 591 | ARTICT3 | trailer |
| 592 | ANDROM | plane |
| 593 | DODO | dodo |
| 594 | RCCAM | car |
| 595 | LAUNCH | boat |
| 596 | COPCARLA | car |
| 597 | COPCARSF | car |
| 598 | COPCARVG | car |
| 599 | COPCARRU | car |
| 600 | PICADOR | car |
| 601 | SWATVAN | car |
| 602 | ALPHA | car |
| 603 | PHOENIX | car |
| 604 | GLENSHIT | car |
| 605 | SADLSHIT | car |
| 606 | BAGBOXA | trailer |
| 607 | BAGBOXB | trailer |
| 608 | TUGSTAIR | trailer |
| 609 | BOXBURG | car |
| 610 | FARMTR1 | trailer |
| 611 | UTILTR1 | trailer |

### 10.2 人物模型 ID（276 个，peds.ide）

| ID | 名称 |
|---|---|
| 0 | NULL |
| 7 | MALE01 |
| 9 | BFORI |
| 10 | BFOST |
| 11 | VBFYCRP |
| 12 | BFYRI |
| 13 | BFYST |
| 14 | BMORI |
| 15 | BMOST |
| 16 | BMYAP |
| 17 | BMYBU |
| 18 | BMYBE |
| 19 | BMYDJ |
| 20 | BMYRI |
| 21 | BMYCR |
| 22 | BMYST |
| 23 | WMYBMX |
| 24 | WBDYG1 |
| 25 | WBDYG2 |
| 26 | WMYBP |
| 27 | WMYCON |
| 28 | BMYDRUG |
| 29 | WMYDRUG |
| 30 | HMYDRUG |
| 31 | DWFOLC |
| 32 | DWMOLC1 |
| 33 | DWMOLC2 |
| 34 | DWMYLC1 |
| 35 | HMOGAR |
| 36 | WMYGOL1 |
| 37 | WMYGOL2 |
| 38 | HFORI |
| 39 | HFOST |
| 40 | HFYRI |
| 41 | HFYST |
| 43 | HMORI |
| 44 | HMOST |
| 45 | HMYBE |
| 46 | HMYRI |
| 47 | HMYCR |
| 48 | HMYST |
| 49 | OMOKUNG |
| 50 | WMYMECH |
| 51 | BMYMOUN |
| 52 | WMYMOUN |
| 53 | OFORI |
| 54 | OFOST |
| 55 | OFYRI |
| 56 | OFYST |
| 57 | OMORI |
| 58 | OMOST |
| 59 | OMYRI |
| 60 | OMYST |
| 61 | WMYPLT |
| 62 | WMOPJ |
| 63 | BFYPRO |
| 64 | HFYPRO |
| 66 | BMYPOL1 |
| 67 | BMYPOL2 |
| 68 | WMOPREA |
| 69 | SBFYST |
| 70 | WMOSCI |
| 71 | WMYSGRD |
| 72 | SWMYHP1 |
| 73 | SWMYHP2 |
| 75 | SWFOPRO |
| 76 | WFYSTEW |
| 77 | SWMOTR1 |
| 78 | WMOTR1 |
| 79 | BMOTR1 |
| 80 | VBMYBOX |
| 81 | VWMYBOX |
| 82 | VHMYELV |
| 83 | VBMYELV |
| 84 | VIMYELV |
| 85 | VWFYPRO |
| 87 | VWFYST1 |
| 88 | WFORI |
| 89 | WFOST |
| 90 | WFYJG |
| 91 | WFYRI |
| 92 | WFYRO |
| 93 | WFYST |
| 94 | WMORI |
| 95 | WMOST |
| 96 | WMYJG |
| 97 | WMYLG |
| 98 | WMYRI |
| 99 | WMYRO |
| 100 | WMYCR |
| 101 | WMYST |
| 102 | BALLAS1 |
| 103 | BALLAS2 |
| 104 | BALLAS3 |
| 105 | FAM1 |
| 106 | FAM2 |
| 107 | FAM3 |
| 108 | LSV1 |
| 109 | LSV2 |
| 110 | LSV3 |
| 111 | MAFFA |
| 112 | MAFFB |
| 113 | MAFBOSS |
| 114 | VLA1 |
| 115 | VLA2 |
| 116 | VLA3 |
| 117 | TRIADA |
| 118 | TRIADB |
| 120 | TRIBOSS |
| 121 | DNB1 |
| 122 | DNB2 |
| 123 | DNB3 |
| 124 | VMAFF1 |
| 125 | VMAFF2 |
| 126 | VMAFF3 |
| 127 | VMAFF4 |
| 128 | DNMYLC |
| 129 | DNFOLC1 |
| 130 | DNFOLC2 |
| 131 | DNFYLC |
| 132 | DNMOLC1 |
| 133 | DNMOLC2 |
| 134 | SBMOTR2 |
| 135 | SWMOTR2 |
| 136 | SBMYTR3 |
| 137 | SWMOTR3 |
| 138 | WFYBE |
| 139 | BFYBE |
| 140 | HFYBE |
| 141 | SOFYBU |
| 142 | SBMYST |
| 143 | SBMYCR |
| 144 | BMYCG |
| 145 | WFYCRK |
| 146 | HMYCM |
| 147 | WMYBU |
| 148 | BFYBU |
| 150 | WFYBU |
| 151 | DWFYLC1 |
| 152 | WFYPRO |
| 153 | WMYCONB |
| 154 | WMYBE |
| 155 | WMYPIZZ |
| 156 | BMOBAR |
| 157 | CWFYHB |
| 158 | CWMOFR |
| 159 | CWMOHB1 |
| 160 | CWMOHB2 |
| 161 | CWMYFR |
| 162 | CWMYHB1 |
| 163 | BMYBOUN |
| 164 | WMYBOUN |
| 165 | WMOMIB |
| 166 | BMYMIB |
| 167 | WMYBELL |
| 168 | BMOCHIL |
| 169 | SOFYRI |
| 170 | SOMYST |
| 171 | VWMYBJD |
| 172 | VWFYCRP |
| 173 | SFR1 |
| 174 | SFR2 |
| 175 | SFR3 |
| 176 | BMYBAR |
| 177 | WMYBAR |
| 178 | WFYSEX |
| 179 | WMYAMMO |
| 180 | BMYTATT |
| 181 | VWMYCR |
| 182 | VBMOCD |
| 183 | VBMYCR |
| 184 | VHMYCR |
| 185 | SBMYRI |
| 186 | SOMYRI |
| 187 | SOMYBU |
| 188 | SWMYST |
| 189 | WMYVA |
| 190 | COPGRL3 |
| 191 | GUNGRL3 |
| 192 | MECGRL3 |
| 193 | NURGRL3 |
| 194 | CROGRL3 |
| 195 | GANGRL3 |
| 196 | CWFOFR |
| 197 | CWFOHB |
| 198 | CWFYFR1 |
| 199 | CWFYFR2 |
| 200 | CWMYHB2 |
| 201 | DWFYLC2 |
| 202 | DWMYLC2 |
| 203 | OMYKARA |
| 204 | WMYKARA |
| 205 | WFYBURG |
| 206 | VWMYCD |
| 207 | VHFYPRO |
| 209 | OMONOOD |
| 210 | OMOBOAT |
| 211 | WFYCLOT |
| 212 | VWMOTR1 |
| 213 | VWMOTR2 |
| 214 | VWFYWAI |
| 215 | SBFORI |
| 216 | SWFYRI |
| 217 | WMYCLOT |
| 218 | SBFOST |
| 219 | SBFYRI |
| 220 | SBMOCD |
| 221 | SBMORI |
| 222 | SBMOST |
| 223 | SHMYCR |
| 224 | SOFORI |
| 225 | SOFOST |
| 226 | SOFYST |
| 227 | SOMOBU |
| 228 | SOMORI |
| 229 | SOMOST |
| 230 | SWMOTR5 |
| 231 | SWFORI |
| 232 | SWFOST |
| 233 | SWFYST |
| 234 | SWMOCD |
| 235 | SWMORI |
| 236 | SWMOST |
| 237 | SHFYPRO |
| 238 | SBFYPRO |
| 239 | SWMOTR4 |
| 240 | SWMYRI |
| 241 | SMYST |
| 242 | SMYST2 |
| 243 | SFYPRO |
| 244 | VBFYST2 |
| 245 | VBFYPRO |
| 246 | VHFYST3 |
| 247 | BIKERA |
| 248 | BIKERB |
| 249 | BMYPIMP |
| 250 | SWMYCR |
| 251 | WFYLG |
| 252 | WMYVA2 |
| 253 | BMOSEC |
| 254 | BIKDRUG |
| 255 | WMYCH |
| 256 | SBFYSTR |
| 257 | SWFYSTR |
| 258 | HECK1 |
| 259 | HECK2 |
| 260 | BMYCON |
| 261 | WMYCD1 |
| 262 | BMOCD |
| 263 | VWFYWA2 |
| 264 | WMOICE |
| 274 | LAEMT1 |
| 275 | LVEMT1 |
| 276 | SFEMT1 |
| 277 | LAFD1 |
| 278 | LVFD1 |
| 279 | SFFD1 |
| 280 | LAPD1 |
| 281 | SFPD1 |
| 282 | LVPD1 |
| 283 | CSHER |
| 284 | LAPDM1 |
| 285 | SWAT |
| 286 | FBI |
| 287 | ARMY |
| 288 | DSHER |
| 290 | SPECIAL01 |
| 291 | SPECIAL02 |
| 292 | SPECIAL03 |
| 293 | SPECIAL04 |
| 294 | SPECIAL05 |
| 295 | SPECIAL06 |
| 296 | SPECIAL07 |
| 297 | SPECIAL08 |
| 298 | SPECIAL09 |
| 299 | SPECIAL10 |

### 10.3 物体模型 ID（default.ide objs/hier 段，34 个）

| ID | 名称 |
|---|---|
| 300 | CUTOBJ01 |
| 301 | CUTOBJ02 |
| 302 | CUTOBJ03 |
| 303 | CUTOBJ04 |
| 304 | CUTOBJ05 |
| 305 | CUTOBJ06 |
| 306 | CUTOBJ07 |
| 307 | CUTOBJ08 |
| 308 | CUTOBJ09 |
| 309 | CUTOBJ10 |
| 310 | CUTOBJ11 |
| 311 | CUTOBJ12 |
| 312 | CUTOBJ13 |
| 313 | CUTOBJ14 |
| 314 | CUTOBJ15 |
| 315 | CUTOBJ16 |
| 316 | CUTOBJ17 |
| 317 | CUTOBJ18 |
| 318 | CUTOBJ19 |
| 319 | CUTOBJ20 |
| 384 | CLOTHES01 |
| 385 | CLOTHES01 |
| 386 | CLOTHES01 |
| 387 | CLOTHES01 |
| 388 | CLOTHES01 |
| 389 | CLOTHES01 |
| 390 | CLOTHES01 |
| 391 | CLOTHES01 |
| 392 | CLOTHES01 |
| 393 | CLOTHES01 |
| 394 | SHANDL |
| 395 | SHANDR |
| 396 | FHANDL |
| 397 | FHANDR |

### 10.4 武器物件模型 ID（default.ide weap 段，51 个；用于生成武器物件——注意与武器*类型* ID 0-46 不同）

| ID | 名称 |
|---|---|
| 320 | AIRTRAIN_VLO |
| 321 | GUN_DILDO1 |
| 322 | GUN_DILDO2 |
| 323 | GUN_VIBE1 |
| 324 | GUN_VIBE2 |
| 325 | FLOWERA |
| 326 | GUN_CANE |
| 327 | GUN_BOXWEE |
| 328 | GUN_BOXBIG |
| 330 | CELLPHONE |
| 331 | BRASSKNUCKLE |
| 333 | GOLFCLUB |
| 334 | NITESTICK |
| 335 | KNIFECUR |
| 336 | BAT |
| 337 | SHOVEL |
| 338 | POOLCUE |
| 339 | KATANA |
| 341 | CHNSAW |
| 342 | GRENADE |
| 343 | TEARGAS |
| 344 | MOLOTOV |
| 345 | MISSILE |
| 346 | COLT45 |
| 347 | SILENCED |
| 348 | DESERT_EAGLE |
| 349 | CHROMEGUN |
| 350 | SAWNOFF |
| 351 | SHOTGSPA |
| 352 | MICRO_UZI |
| 353 | MP5LNG |
| 354 | FLARE |
| 355 | AK47 |
| 356 | M4 |
| 357 | CUNTGUN |
| 358 | SNIPER |
| 359 | ROCKETLA |
| 360 | HEATSEEK |
| 361 | FLAME |
| 362 | MINIGUN |
| 363 | SATCHEL |
| 364 | BOMB |
| 365 | SPRAYCAN |
| 366 | FIRE_EX |
| 367 | CAMERA |
| 368 | NVGOGGLES |
| 369 | IRGOGGLES |
| 370 | JETPACK |
| 371 | GUN_PARA |
| 372 | TEC9 |
| 373 | ARMOUR |

### 10.5 车辆改装件 ID（veh_mods.ide，194 个）

| ID | 名称 |
|---|---|
| 1000 | SPL_B_MAR_M |
| 1001 | SPL_B_BAB_M |
| 1002 | SPL_B_BAR_M |
| 1003 | SPL_B_MAB_M |
| 1004 | BNT_B_SC_M |
| 1005 | BNT_B_SC_L |
| 1006 | RF_B_SC_R |
| 1007 | WG_L_B_SSK |
| 1008 | NTO_B_L |
| 1009 | NTO_B_S |
| 1010 | NTO_B_TW |
| 1011 | BNT_B_SC_P_M |
| 1012 | BNT_B_SC_P_L |
| 1013 | LGT_B_RSPT |
| 1014 | SPL_B_BAR_L |
| 1015 | SPL_B_BBR_L |
| 1016 | SPL_B_BBR_M |
| 1017 | WG_R_B_SSK |
| 1018 | EXH_B_TS |
| 1019 | EXH_B_T |
| 1020 | EXH_B_L |
| 1021 | EXH_B_M |
| 1022 | EXH_B_S |
| 1023 | SPL_B_BBB_M |
| 1024 | LGT_B_SSPT |
| 1025 | WHEEL_OR1 |
| 1026 | WG_L_A_S |
| 1027 | WG_R_A_S |
| 1028 | EXH_A_S |
| 1029 | EXH_C_S |
| 1030 | WG_R_C_S |
| 1031 | WG_L_C_S |
| 1032 | RF_A_S |
| 1033 | RF_C_S |
| 1034 | EXH_A_L |
| 1035 | RF_C_L |
| 1036 | WG_L_A_L |
| 1037 | EXH_C_L |
| 1038 | RF_A_L |
| 1039 | WG_L_C_L |
| 1040 | WG_R_A_L |
| 1041 | WG_R_C_L |
| 1042 | WG_L_LR_BR1 |
| 1043 | EXH_LR_BR2 |
| 1044 | EXH_LR_BR1 |
| 1045 | EXH_C_F |
| 1046 | EXH_A_F |
| 1047 | WG_L_A_F |
| 1048 | WG_L_C_F |
| 1049 | SPL_A_F_R |
| 1050 | SPL_C_F_R |
| 1051 | WG_R_A_F |
| 1052 | WG_R_C_F |
| 1053 | RF_C_F |
| 1054 | RF_A_F |
| 1055 | RF_A_ST |
| 1056 | WG_L_A_ST |
| 1057 | WG_L_C_ST |
| 1058 | SPL_A_ST_R |
| 1059 | EXH_C_ST |
| 1060 | SPL_C_ST_R |
| 1061 | RF_C_ST |
| 1062 | WG_R_A_ST |
| 1063 | WG_R_C_ST |
| 1064 | EXH_A_ST |
| 1065 | EXH_A_J |
| 1066 | EXH_C_J |
| 1067 | RF_A_J |
| 1068 | RF_C_J |
| 1069 | WG_L_A_J |
| 1070 | WG_L_C_J |
| 1071 | WG_R_A_J |
| 1072 | WG_R_C_J |
| 1073 | WHEEL_SR6 |
| 1074 | WHEEL_SR3 |
| 1075 | WHEEL_SR2 |
| 1076 | WHEEL_LR4 |
| 1077 | WHEEL_LR1 |
| 1078 | WHEEL_LR3 |
| 1079 | WHEEL_SR1 |
| 1080 | WHEEL_SR5 |
| 1081 | WHEEL_SR4 |
| 1082 | WHEEL_GN1 |
| 1083 | WHEEL_LR2 |
| 1084 | WHEEL_LR5 |
| 1085 | WHEEL_GN2 |
| 1086 | STEREO |
| 1087 | HYDRALICS |
| 1088 | RF_A_U |
| 1089 | EXH_C_U |
| 1090 | WG_L_A_U |
| 1091 | RF_C_U |
| 1092 | EXH_A_U |
| 1093 | WG_L_C_U |
| 1094 | WG_R_A_U |
| 1095 | WG_R_C_U |
| 1096 | WHEEL_GN3 |
| 1097 | WHEEL_GN4 |
| 1098 | WHEEL_GN5 |
| 1099 | WG_R_LR_BR1 |
| 1100 | MISC_C_LR_REM1 |
| 1101 | WG_R_LR_REM1 |
| 1102 | WG_R_LR_SV |
| 1103 | RF_LR_BL2 |
| 1104 | EXH_LR_BL1 |
| 1105 | EXH_LR_BL2 |
| 1106 | WG_L_LR_REM2 |
| 1107 | WG_R_LR_BL1 |
| 1108 | WG_L_LR_BL1 |
| 1109 | BBB_LR_SLV1 |
| 1110 | BBB_LR_SLV2 |
| 1111 | BNT_LR_SLV1 |
| 1112 | BNT_LR_SLV2 |
| 1113 | EXH_LR_SLV1 |
| 1114 | EXH_LR_SLV2 |
| 1115 | FBB_LR_SLV1 |
| 1116 | FBB_LR_SLV2 |
| 1117 | FBMP_LR_SLV1 |
| 1118 | WG_L_LR_SLV1 |
| 1119 | WG_L_LR_SLV2 |
| 1120 | WG_R_LR_SLV1 |
| 1121 | WG_R_LR_SLV2 |
| 1122 | WG_L_LR_REM1 |
| 1123 | MISC_C_LR_REM2 |
| 1124 | WG_R_LR_REM2 |
| 1125 | MISC_C_LR_REM3 |
| 1126 | EXH_LR_REM1 |
| 1127 | EXH_LR_REM2 |
| 1128 | RF_LR_BL1 |
| 1129 | EXH_LR_SV1 |
| 1130 | RF_LR_SV1 |
| 1131 | RF_LR_SV2 |
| 1132 | EXH_LR_SV2 |
| 1133 | WG_L_LR_SV |
| 1134 | WG_L_LR_T1 |
| 1135 | EXH_LR_T2 |
| 1136 | EXH_LR_T1 |
| 1137 | WG_R_LR_T1 |
| 1138 | SPL_A_S_B |
| 1139 | SPL_C_S_B |
| 1140 | RBMP_C_S |
| 1141 | RBMP_A_S |
| 1142 | BNTR_B_OV |
| 1143 | BNTL_B_OV |
| 1144 | BNTR_B_SQ |
| 1145 | BNTL_B_SQ |
| 1146 | SPL_C_L_B |
| 1147 | SPL_A_L_B |
| 1148 | RBMP_C_L |
| 1149 | RBMP_A_L |
| 1150 | RBMP_A_F |
| 1151 | RBMP_C_F |
| 1152 | FBMP_C_F |
| 1153 | FBMP_A_F |
| 1154 | RBMP_A_ST |
| 1155 | FBMP_A_ST |
| 1156 | RBMP_C_ST |
| 1157 | FBMP_C_ST |
| 1158 | SPL_C_J_B |
| 1159 | RBMP_A_J |
| 1160 | FBMP_A_J |
| 1161 | RBMP_C_J |
| 1162 | SPL_A_J_B |
| 1163 | SPL_C_U_B |
| 1164 | SPL_A_U_B |
| 1165 | FBMP_C_U |
| 1166 | FBMP_A_U |
| 1167 | RBMP_C_U |
| 1168 | RBMP_A_U |
| 1169 | FBMP_A_S |
| 1170 | FBMP_C_S |
| 1171 | FBMP_A_L |
| 1172 | FBMP_C_L |
| 1173 | FBMP_C_J |
| 1174 | FBMP_LR_BR1 |
| 1175 | FBMP_LR_BR2 |
| 1176 | RBMP_LR_BR1 |
| 1177 | RBMP_LR_BR2 |
| 1178 | RBMP_LR_REM2 |
| 1179 | FBMP_LR_REM1 |
| 1180 | RBMP_LR_REM1 |
| 1181 | FBMP_LR_BL2 |
| 1182 | FBMP_LR_BL1 |
| 1183 | RBMP_LR_BL2 |
| 1184 | RBMP_LR_BL1 |
| 1185 | FBMP_LR_REM2 |
| 1186 | RBMP_LR_SV2 |
| 1187 | RBMP_LR_SV1 |
| 1188 | FBMP_LR_SV2 |
| 1189 | FBMP_LR_SV1 |
| 1190 | FBMP_LR_T2 |
| 1191 | FBMP_LR_T1 |
| 1192 | RBMP_LR_T1 |
| 1193 | RBMP_LR_T2 |

### 10.6 武器类型 ID（GTASA 官方 WeapType 枚举，0-46）

| ID | 武器 | ID | 武器 | ID | 武器 |
|---|---|---|---|---|---|
| 0 | Fist (UNARMED) | 16 | Grenade | 32 | Tec9 |
| 1 | Brass Knuckles | 17 | Tear Gas | 33 | Country Rifle |
| 2 | Golf Club | 18 | Molotov | 34 | Sniper Rifle |
| 3 | Nightstick | 19 | Rocket (投掷) | 35 | Rocket Launcher |
| 4 | Knife | 20 | Rocket HS (热追踪) | 36 | Heat Seeker |
| 5 | Baseball Bat | 21 | Freefall Bomb | 37 | Flamethrower |
| 6 | Shovel | 22 | Colt 45 (Pistol) | 38 | Minigun |
| 7 | Pool Cue | 23 | Silenced Pistol | 39 | Satchel Charge |
| 8 | Katana | 24 | Desert Eagle | 40 | Detonator |
| 9 | Chainsaw | 25 | Shotgun | 41 | Spraycan |
| 10 | Dildo 1 | 26 | Sawnoff Shotgun | 42 | Fire Extinguisher |
| 11 | Dildo 2 | 27 | Combat Shotgun | 43 | Camera |
| 12 | Vibrator 1 | 28 | Uzi | 44 | Night Vision |
| 13 | Vibrator 2 | 29 | MP5 | 45 | Infrared (Thermal) |
| 14 | Flowers | 30 | AK47 | 46 | Parachute |
| 15 | Cane | 31 | M4 | | |

装备示例（先 `0247: load_model` 加载武器模型，`01B2` 官方注释要求）：
```scm
0247: load_model 348      ; desert_eagle 模型
while 8248:   not model 348 available
    wait 0
end
01B2: give_actor $PLAYER_ACTOR weapon 24 ammo 100
0249: release_model 348
```

### 10.6b 武器→模型 ID（官方 weapon.dat，`0247: load_model <ID>` 用）

| 武器 | 模型 ID | 武器 | 模型 ID | 武器 | 模型 ID |
|---|---|---|---|---|---|
| BRASSKNUCKLE | 331 | GOLFCLUB | 333 | NIGHTSTICK | 334 |
| KNIFE | 335 | BASEBALLBAT | 336 | SHOVEL | 337 |
| POOLCUE | 338 | KATANA | 339 | CHAINSAW | 341 |
| DILDO1 | 321 | DILDO2 | 322 | VIBE1 | 323 |
| VIBE2 | 324 | FLOWERS | 325 | CANE | 326 |
| PARACHUTE | 371 | GRENADE | 342 | TEARGAS | 343 |
| MOLOTOV | 344 | ROCKET/ROCKET_HS/FREEFALL_BOMB | 345 | PISTOL | 346 |
| PISTOL_SILENCED | 347 | DESERT_EAGLE | 348 | SHOTGUN | 349 |
| SAWNOFF | 350 | SPAS12 | 351 | MICRO_UZI | 352 |
| TEC9 | 372 | MP5 | 353 | AK47 | 355 |
| M4 | 356 | COUNTRYRIFLE | 357 | SNIPERRIFLE | 358 |
| RLAUNCHER | 359 | RLAUNCHER_HS | 360 | FTHROWER | 361 |
| MINIGUN | 362 | SATCHEL_CHARGE | 363 | DETONATOR | 364 |
| SPRAYCAN | 365 | EXTINGUISHER | 366 | CAMERA | 367 |
| NIGHTVISION | 368 | INFRARED | 369 | | |

### 10.7 按键码

**0AB0 key_pressed（CLEO，Windows 虚拟键码 VK）**
| 键 | 码 | 键 | 码 | 键 | 码 |
|---|---|---|---|---|---|
| 空格 | 32 | A-Z | 65-90 | 0-9 | 48-57 |
| 回车 | 13 | 退格 | 8 | Esc | 27 |
| Shift | 16 | Ctrl | 17 | Alt | 18 |
| Tab | 9 | F1-F12 | 112-123 | 小键盘0-9 | 96-105 |
| 上下左右 | 38/40/37/39 | 鼠标左/右 | 1/2 | 鼠标中 | 4 |

**00E1 player pressed_key（游戏按键绑定枚举）**：数字对应游戏内"set key"绑定索引（非 VK 码），用编辑器的按键绑定界面查看对应数字；脚本常见用 `00E1: player 0 pressed_key 2`（动作键）。

### 10.8 天气 ID（GTA SA 天气表常用）
| ID | 天气 | ID | 天气 |
|---|---|---|---|
| 0 | EXTRASUNNY_LA | 8 | SUNNY_SMOG_LA |
| 1 | SUNNY_LA | 14 | CLOUDY_LA |
| 2 | EXTRASUNNY_SMOG_LA | 16 | RAINY_COUNTRYSIDE |
| 3 | SUNNY_SMOG_LA | 17 | EXTRASUNNY_DESERT |
| 4 | CLOUDY_LA | 20 | SANDY_DESERT |
| 5 | RAINY_SF | 41 | FOGGY_SF (未用) |
| 6 | FOGGY_SF | 42 | SUNNY_SMOG_SF (未用) |

### 10.9 常用坐标（SA 世界 -3000..3000）
| 地点 | X | Y | Z |
|---|---|---|---|
| Grove Street（CJ 家） | 2494.0 | -1668.0 | 13.3 |
| 圣安地列斯市中心 | 1000.0 | -1000.0 | 20.0 |
| 洛圣都沙滩 | 330.0 | -1800.0 | -5.0 |
| 沙漠机场 | 315.0 | 2400.0 | 18.0 |
| SF 金门 | -2750.0 | 250.0 | 7.0 |
| LV 赌场区 | 2240.0 | 1300.0 | 25.0 |

### 10.10 常用内置全局（CustomVariables.ini）
| 全局 | ID | 说明 |
|---|---|---|
| $PLAYER_CHAR | 2 | 玩家 handle |
| $PLAYER_ACTOR | 3 | 玩家 actor handle |
| $ONMISSION | 409 | 1=任务中 |
| 其余 | - | 注册表见 `sa/CustomVariables.ini`（编译自动加载） |

## 11. 参考数据文件（编译器同源，可自行查全）

- `app/src/main/assets/sanny/data/sa/SASCM.INI`（基础，数千条）
- `sa/SASCM.CLEO.ini`（CLEO 特有）· `sa/SASCM.CLEO+.ini`（CLEO+ 扩展）· `sa/SASCM.NewOpcodes.ini`
- `sa_mobile/SASCM.Mobile.ini`（Mobile 触摸 0A51 系列）· `sa_mobile/SASCM.CLEO.ini`（C/A Android opcode）· `sa_mobile/SASCM.CLEO.STD.ini`（标准 CLEO 4 副本，Mobile 模式已挂载）· `sa_mobile/SASCM.CA.ini`（C/A 补充 0DDA/0DDB/0DDE/0DF2-0DF6/1000）
- `opcode_conds.json`（条件判定表）· `opcode_types.json` · `opcode_names.json` · `CustomVariables.ini`（全局注册）
- 在线：<https://gtagmodding.com/opcode-database/> · <https://cleo.li/> · <https://docs.sannybuilder.com/>
