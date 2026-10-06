import com.sanny.builder.compiler.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
public class RegressTpmenu {
    public static void main(String[] args) throws Exception {
        OpcodeTable table = new OpcodeTable();
        String base = "/home/dsh/main/sanny-builder/app/src/main/assets/sanny/data/";
        for (String ini : new String[]{"sa_mobile/SASCM.ini", "sa_mobile/SASCM.Mobile.ini", "sa_mobile/SASCM.CLEO.ini"})
            table.load(new String(Files.readAllBytes(Paths.get(base+ini)), java.nio.charset.StandardCharsets.UTF_8));
        table.loadNames(new String(Files.readAllBytes(Paths.get(base+"opcode_names.json")), java.nio.charset.StandardCharsets.UTF_8));
        table.loadTypes(new String(Files.readAllBytes(Paths.get(base+"opcode_types.json")), java.nio.charset.StandardCharsets.UTF_8));
        table.loadConds(new String(Files.readAllBytes(Paths.get(base+"opcode_conds.json")), java.nio.charset.StandardCharsets.UTF_8));
        byte[] csi = Files.readAllBytes(Paths.get(args.length > 0 ? args[0] : "/home/dsh/main/sanny-builder/app/src/main/assets/sanny/samples/tpmenu.csi"));
        SaMobileFile f = SaMobileFile.parse(csi);
        String src = f.embeddedSource != null ? f.embeddedSource : new String(Files.readAllBytes(Paths.get("/tmp/tpmenu.txt")), java.nio.charset.StandardCharsets.UTF_8);
        HashMap<String,Integer> g = new HashMap<>();
        java.util.Map<Integer,String> gem = new HashMap<>();
        for (String l : Files.readAllLines(Paths.get(base+"sa_mobile/CustomVariables.ini"))) {
            String t = l.trim(); if (t.isEmpty() || t.startsWith(";")) continue;
            int e = t.indexOf('='); if (e <= 0) continue;
            try { int ix = Integer.parseInt(t.substring(0,e).trim()); String nm2 = t.substring(e+1).trim();
                g.put(nm2.toUpperCase(java.util.Locale.ROOT), ix); gem.put(ix, nm2);
            } catch (NumberFormatException x) {}
        }
        StringBuilder err = new StringBuilder();
        byte[] code = TypedCompiler.compile(src, table, g, err);
        if (code == null) {
            System.out.println("COMPILE FAIL err: " + err);
            return;
        }
        System.out.println("COMPILE OK len=" + code.length + " orig=" + f.mainCode.length);
        int same=0, n=Math.min(code.length, f.mainCode.length);
        for (int i = 0; i < n; i++) if (code[i]==f.mainCode[i]) same++;
        System.out.println("byte match: " + same + "/" + n);
        // 打包比较
        byte[] packed = SaMobileFile.pack(code, "TP_MENU", src);
        System.out.println("packed len=" + packed.length + " orig file=" + csi.length);
        // 反编译验证
        String dec = TypedDecompiler.decompile(code, table, gem, new StringBuilder());
        System.out.println("decompile lines=" + dec.split("\n").length);
        String[] dl = dec.split("\n");
        for (int i = 0; i < Math.min(8, dl.length); i++) System.out.println("DEC" + i + ": " + dl[i]);
        // round-trip：反编译产物重编译，字节必须一致
        StringBuilder e3 = new StringBuilder();
        byte[] code2 = TypedCompiler.compile(dec, table, g, e3);
        if (code2 == null) {
            System.out.println("RE-COMPILE FAIL: " + e3);
        } else {
            int same2 = 0, n2 = Math.min(code.length, code2.length);
            for (int i = 0; i < n2; i++) if (code[i] == code2[i]) same2++;
            System.out.println("decompile recompile: len=" + code2.length + " match=" + same2 + "/" + n2 + " equal=" + java.util.Arrays.equals(code, code2));
            if (!java.util.Arrays.equals(code, code2)) {
                int printed = 0;
                for (int i = 0; i < n2 && printed < 10; i++) {
                    if (code[i] != code2[i]) {
                        System.out.println("BYTEDIFF@" + i + " c1=" + String.format("%02x", code[i] & 0xFF) + " c2=" + String.format("%02x", code2[i] & 0xFF));
                        printed++;
                    }
                }
            }
        }
    }
}
