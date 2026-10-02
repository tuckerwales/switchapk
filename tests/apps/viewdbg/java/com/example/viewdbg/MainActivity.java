package com.example.viewdbg;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewDebug;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Checks ViewDebug annotations and dumpCapturedView. The yellow square
 * is only there so the screenshot shows the activity drew.
 */
public class MainActivity extends Activity {
    private static final String TAG = "ViewDbg";

    static void check(String name, boolean ok, Object detail) {
        if (ok) Log.i(TAG, "VD ok " + name);
        else Log.e(TAG, "VD FAIL " + name + " " + detail);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface TagAnn {
        String value();
    }

    @Retention(RetentionPolicy.CLASS)
    @interface Hidden {
        int v();
    }

    @Hidden(v = 1)
    static class Secret {}

    public static class Bits {
        @ViewDebug.CapturedViewProperty
        public int getN() {
            return 4;
        }
    }

    @TagAnn("probe")
    static class Probe extends View {
        @ViewDebug.CapturedViewProperty
        public String label = "beta";

        Probe(Context context) {
            super(context);
        }

        @ViewDebug.CapturedViewProperty
        public int getCode() {
            return 7;
        }

        @ViewDebug.CapturedViewProperty(retrieveReturn = true)
        public Bits getBits() {
            return new Bits();
        }

        @ViewDebug.ExportedProperty(category = "layout", mapping = {
                @ViewDebug.IntToString(from = 0, to = "VISIBLE"),
                @ViewDebug.IntToString(from = 4, to = "INVISIBLE"),
                @ViewDebug.IntToString(from = 8, to = "GONE")
        }, flagMapping = {
                @ViewDebug.FlagToString(mask = 1, equals = 1, name = "ENABLED")
        })
        public int getVis() {
            return 0;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Probe probe = new Probe(this);
        check("flags", !ViewDebug.TRACE_HIERARCHY && !ViewDebug.TRACE_RECYCLER, ViewDebug.TRACE_HIERARCHY);
        check("draw", ViewDebug.HierarchyTraceType.valueOf("DRAW") == ViewDebug.HierarchyTraceType.DRAW,
                ViewDebug.HierarchyTraceType.valueOf("DRAW"));
        check("recycler", ViewDebug.RecyclerTraceType.values().length == 6,
                ViewDebug.RecyclerTraceType.values().length);
        ViewDebug.ExportedProperty ep = null;
        ViewDebug.CapturedViewProperty code = null;
        ViewDebug.CapturedViewProperty bits = null;
        TagAnn tag = null;
        try {
            ep = Probe.class.getMethod("getVis").getAnnotation(ViewDebug.ExportedProperty.class);
            code = Probe.class.getMethod("getCode").getAnnotation(ViewDebug.CapturedViewProperty.class);
            bits = Probe.class.getMethod("getBits").getAnnotation(ViewDebug.CapturedViewProperty.class);
            tag = Probe.class.getAnnotation(TagAnn.class);
        } catch (Exception e) {
            check("lookup", false, e);
        }
        check("category", ep != null && "layout".equals(ep.category()), ep == null ? "null" : ep.category());
        boolean mapping = false;
        if (ep != null) {
            ViewDebug.IntToString[] map = ep.mapping();
            mapping = map.length == 3 && map[0].from() == 0 && "VISIBLE".equals(map[0].to())
                    && map[1].from() == 4 && "INVISIBLE".equals(map[1].to())
                    && map[2].from() == 8 && "GONE".equals(map[2].to());
        }
        check("mapping", mapping, ep == null ? "null" : ep.mapping().length);
        boolean flagmap = false;
        if (ep != null && ep.flagMapping().length == 1) {
            ViewDebug.FlagToString flag = ep.flagMapping()[0];
            flagmap = flag.mask() == 1 && flag.equals() == 1 && "ENABLED".equals(flag.name()) && flag.outputIf();
        }
        check("flagmap", flagmap, ep == null ? "null" : ep.flagMapping().length);
        boolean defaults = ep != null && !ep.resolveId() && "".equals(ep.prefix()) && !ep.formatToHexString()
                && !ep.hasAdjacentMapping() && !ep.deepExport() && ep.indexMapping().length == 0;
        check("defaults", defaults, ep == null ? "null" : ep.prefix());
        check("captured", code != null && !code.retrieveReturn() && bits != null && bits.retrieveReturn(), code);
        check("tag", tag != null && "probe".equals(tag.value()), tag);
        check("hidden", Secret.class.getAnnotation(Hidden.class) == null, Secret.class.getAnnotation(Hidden.class));
        boolean traced = true;
        Throwable traceError = null;
        try {
            ViewDebug.startHierarchyTracing("x", probe);
            ViewDebug.trace(probe, ViewDebug.HierarchyTraceType.DRAW);
            ViewDebug.stopHierarchyTracing();
            ViewDebug.startRecyclerTracing("x", probe);
            ViewDebug.trace(probe, ViewDebug.RecyclerTraceType.BIND_VIEW, 1);
            ViewDebug.stopRecyclerTracing();
            ViewDebug.dumpCapturedView(TAG, probe);
        } catch (Throwable t) {
            traced = false;
            traceError = t;
        }
        check("traced", traced, traceError);
    }
}
