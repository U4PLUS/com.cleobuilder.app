package com.sanny.builder.compiler;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 游戏编辑模式：数据目录 + opcode 表文件 + 字节码编码风格。
 */
public enum GameMode {
    GTA3("gta3", "GTA III", "gta3", Arrays.asList("SCM.INI"), Encoding.TYPED),
    GTAVC("vc", "GTA VC", "vc", Arrays.asList("VCSCM.INI", "VCSCM.CLEO.ini"), Encoding.TYPED),
    VC_MOBILE("vc_mobile", "GTA VC Mobile", "vc_mobile",
            Arrays.asList("VCSCM.INI", "VCSCM.Mobile.ini", "VCSCM.CLEO.ini"), Encoding.TYPED),
    GTASA("sa", "GTA SA", "sa", Arrays.asList("SASCM.INI", "SASCM.CLEO.ini", "SASCM.CLEO+.ini", "SASCM.NewOpcodes.ini"), Encoding.SA),
    SA_MOBILE("sa_mobile", "GTA SA Mobile", "sa_mobile",
            Arrays.asList("SASCM.ini", "SASCM.Mobile.ini", "SASCM.CLEO.ini"), Encoding.SA),
    SA_PS2("sa_ps2", "GTA SA PS2", "sa_ps2",
            Arrays.asList("SASCM.INI", "SASCM.CLEO.ini", "SASCM.PS2.INI"), Encoding.SA),
    SA_V2("sa_v2", "GTA SA v2", "sa",
            Arrays.asList("SASCM.INI", "SASCM.CLEO.ini", "SASCM.CLEO+.ini"), Encoding.SA),
    LCS("lcs", "GTA LCS", "lcs", Arrays.asList("LCSSCM.INI"), Encoding.TYPED),
    LCS_MOBILE("lcs_mobile", "GTA LCS Mobile", "lcs", Arrays.asList("LCSSCM.INI"), Encoding.TYPED),
    VCS_PSP("vcs_psp", "GTA VCS PSP", "vcs_psp", Arrays.asList("VCSSCM.INI"), Encoding.TYPED),
    VCS_PS2("vcs_ps2", "GTA VCS PS2", "vcs_psp", Arrays.asList("VCSSCM.INI"), Encoding.TYPED);

    public enum Encoding { SA, TYPED }

    public final String id;
    public final String label;
    public final String dataDir;
    public final List<String> iniFiles;
    public final Encoding encoding;

    GameMode(String id, String label, String dataDir, List<String> iniFiles, Encoding enc) {
        this.id = id;
        this.label = label;
        this.dataDir = dataDir;
        this.iniFiles = Collections.unmodifiableList(iniFiles);
        this.encoding = enc;
    }

    /** 从 data 目录名反查模式 */
    public static GameMode fromDataDir(String dataDir) {
        for (GameMode m : values()) {
            if (m.dataDir.equals(dataDir) || m.id.equals(dataDir)) return m;
        }
        return GTASA;
    }
}
