import com.sun.jna.*;
import com.sun.jna.ptr.PointerByReference;
import java.util.*;

/**
 * Rust 核心语言服务端到端验证（desktop aarch64 .so + 官方 sa_sbl 数据）。
 * 用法：java -Djna.library.path=/path/to/libcore.so-dir -cp jna.jar:tools JnaLspCheck <dataRoot> <constants> <classes>
 * 期望：connect=1, enabled=1, filter(t)=3 (true/timerb/timera), find(timera)=Var/32@
 */
public class JnaLspCheck {
    interface Core extends Library {
        Core INSTANCE = Native.load("core", Core.class);
        void language_service_set_data_dir(String path);
        Pointer language_service_new();
        void language_service_free(Pointer s);
        byte language_service_client_connect_in_memory(Pointer s, int h, String c, String cl);
        byte language_service_client_notify_on_change(Pointer s, int h, String text);
        byte language_service_is_enabled(Pointer s, int h);
        byte language_service_find(Pointer s, String sym, int h, int line, SymbolInfo out);
        byte language_service_filter_constants_by_name(Pointer s, int h, String needle, int line, Pointer dict);
        byte language_service_client_disconnect(Pointer s, int h);
        Pointer dictionary_str_by_str_new();
        void dictionary_str_by_str_free(Pointer d);
        long dictionary_str_by_str_get_count(Pointer d);
        byte dictionary_str_by_str_get_entry(Pointer d, long i, PointerByReference k, PointerByReference v);
    }
    public static class SymbolInfo extends Structure {
        public int _type; public Pointer value; public Pointer nameNoFormat; public Pointer annotation;
        protected List<String> getFieldOrder() { return Arrays.asList("_type", "value", "nameNoFormat", "annotation"); }
        public String val(){ return value==null?"":value.getString(0); }
    }
    static List<String> filter(Core c, Pointer s, String needle, int line) {
        Pointer d = c.dictionary_str_by_str_new();
        c.language_service_filter_constants_by_name(s, 1, needle, line, d);
        long cnt = c.dictionary_str_by_str_get_count(d);
        List<String> r = new ArrayList<>();
        PointerByReference k = new PointerByReference(); PointerByReference v = new PointerByReference();
        for (long i = 0; i < cnt; i++) if (c.dictionary_str_by_str_get_entry(d, i, k, v) != 0)
            r.add(k.getValue().getString(0));
        c.dictionary_str_by_str_free(d);
        return r;
    }
    public static void main(String[] a) throws Exception {
        String root = a.length > 0 ? a[0] : "/home/dsh/main/sb_research";
        String constants = a.length > 1 ? a[1] : "/home/dsh/main/sb_research/data/sa_sbl/constants.txt";
        String classes = a.length > 2 ? a[2] : "/home/dsh/main/sb_research/data/sa_sbl/classes.db";
        Core c = Core.INSTANCE;
        c.language_service_set_data_dir(root);
        Pointer s = c.language_service_new();
        int fail = 0;
        if (c.language_service_client_connect_in_memory(s, 1, constants, classes) != 1) { System.out.println("FAIL connect"); fail++; }
        String src = "{$CLEO .cs}\n0@ = 0\nwhile 0@ >= TIMERA\n0@ -= 1\nend\nif false == 0\n    jump_if_false @L_0000000A\nend\n";
        c.language_service_client_notify_on_change(s, 1, src);
        Thread.sleep(1500);
        if (c.language_service_is_enabled(s, 1) != 1) { System.out.println("FAIL enabled"); fail++; }
        List<String> f = filter(c, s, "t", 2);
        System.out.println("filter(t)=" + f);
        if (!f.contains("timera") || !f.contains("timerb")) { System.out.println("FAIL filter(t)"); fail++; }
        f = filter(c, s, "timer", 2);
        if (f.size() != 2) { System.out.println("FAIL filter(timer)"); fail++; }
        SymbolInfo info = new SymbolInfo();
        if (c.language_service_find(s, "timera", 1, 2, info) != 1 || info._type != 2 || !"32@".equals(info.val())) {
            System.out.println("FAIL find(timera): type=" + info._type + " val=" + info.val()); fail++;
        } else System.out.println("find(timera)=Var/32@ OK");
        c.language_service_client_disconnect(s, 1);
        c.language_service_free(s);
        System.out.println(fail == 0 ? "ALL PASS" : (fail + " FAILURES"));
        System.exit(fail == 0 ? 0 : 1);
    }
}
