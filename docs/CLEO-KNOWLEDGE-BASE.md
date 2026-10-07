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

## 10. 参考数据文件（编译器同源，可自行查全）

- `app/src/main/assets/sanny/data/sa/SASCM.INI`（基础，数千条）
- `sa/SASCM.CLEO.ini`（CLEO 特有）· `sa/SASCM.CLEO+.ini`（CLEO+ 扩展）· `sa/SASCM.NewOpcodes.ini`
- `sa_mobile/SASCM.Mobile.ini`（Mobile 特有）
- `opcode_conds.json`（条件判定表）· `opcode_types.json` · `opcode_names.json` · `CustomVariables.ini`（全局注册）
- 在线：<https://gtagmodding.com/opcode-database/> · <https://cleo.li/> · <https://docs.sannybuilder.com/>
