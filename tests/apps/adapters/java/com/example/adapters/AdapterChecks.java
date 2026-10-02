package com.example.adapters;

import android.app.Activity;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.database.MatrixCursor;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.CheckBox;
import android.widget.ExpandableListView;
import android.widget.MultiAutoCompleteTextView;
import android.widget.SimpleAdapter;
import android.widget.SimpleCursorAdapter;
import android.widget.SimpleCursorTreeAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Logic checks for the adapters. Each logs "ADCHECK ok name" or a failure. */
final class AdapterChecks {
    static final String TAG = "AdapterTest";
    static int failures;

    static void check(String name, boolean ok, Object detail) {
        if (ok) Log.i(TAG, "ADCHECK ok " + name);
        else {
            failures++;
            Log.e(TAG, "ADCHECK FAIL " + name + " " + detail);
        }
    }

    static void run(Activity activity) {
        simpleAdapter(activity);
        cursorAdapter(activity);
        expandable(activity);
        cursorTree(activity);
        tokenizer();
    }

    private static Map<String, Object> row(String name, boolean on) {
        Map<String, Object> m = new HashMap<String, Object>();
        m.put("name", name);
        m.put("on", on);
        return m;
    }

    private static void simpleAdapter(Activity activity) {
        List<Map<String, Object>> data = new ArrayList<Map<String, Object>>();
        data.add(row("north star", true));
        data.add(row("south pole", false));
        data.add(row("northern lights", false));
        final SimpleAdapter adapter = new SimpleAdapter(activity, data, R.layout.row, new String[] {"name", "on"},
                new int[] {R.id.name, R.id.check});
        View v = adapter.getView(0, null, null);
        TextView name = (TextView) v.findViewById(R.id.name);
        CheckBox box = (CheckBox) v.findViewById(R.id.check);
        check("simple bind", "north star".equals(name.getText().toString()) && box.isChecked(),
                name.getText() + " " + box.isChecked());
        v = adapter.getView(1, v, null);
        check("simple rebind", "south pole".equals(name.getText().toString()) && !box.isChecked(), name.getText());
        // Matches a word prefix: "star" is the second word of "north star".
        adapter.getFilter().filter("sta", count -> check("simple filter",
                count == 1 && adapter.getCount() == 1, count + " " + adapter.getCount()));
    }

    private static void cursorAdapter(Activity activity) {
        MatrixCursor c = new MatrixCursor(new String[] {"_id", "name"});
        c.addRow(new Object[] {7, "seven"});
        c.addRow(new Object[] {9, "nine"});
        final SimpleCursorAdapter adapter = new SimpleCursorAdapter(activity, R.layout.row, c,
                new String[] {"name"}, new int[] {R.id.name}, 0);
        check("cursor count", adapter.getCount() == 2 && adapter.hasStableIds(), adapter.getCount());
        check("cursor ids", adapter.getItemId(1) == 9, adapter.getItemId(1));
        View v = adapter.getView(1, null, null);
        check("cursor bind", "nine".equals(((TextView) v.findViewById(R.id.name)).getText().toString()), v);
        adapter.setStringConversionColumn(1);
        check("cursor string", "nine".contentEquals(adapter.convertToString((Cursor) adapter.getItem(1))), "");
        final int[] changes = {0};
        adapter.registerDataSetObserver(new DataSetObserver() {
            public void onChanged() { changes[0]++; }
        });
        MatrixCursor c2 = new MatrixCursor(new String[] {"_id", "name"});
        c2.addRow(new Object[] {1, "one"});
        Cursor old = adapter.swapCursor(c2);
        check("cursor swap", old == c && changes[0] == 1 && adapter.getCount() == 1 && !old.isClosed(),
                changes[0] + " " + adapter.getCount());
        adapter.setFilterQueryProvider(constraint -> {
            MatrixCursor r = new MatrixCursor(new String[] {"_id", "name"});
            r.addRow(new Object[] {42, "q:" + constraint});
            r.addRow(new Object[] {43, "q2"});
            r.addRow(new Object[] {44, "q3"});
            return r;
        });
        adapter.getFilter().filter("x", count -> check("cursor filter",
                count == 3 && adapter.getCount() == 3 && adapter.getItemId(0) == 42 && c2.isClosed(),
                count + " " + adapter.getCount()));
    }

    /** Groups with stable ids from a mutable list, to check expansion survives data changes. */
    static final class Groups extends BaseExpandableListAdapter {
        final List<String> names = new ArrayList<String>();
        final int[] sizes;
        final Activity activity;

        Groups(Activity activity, int[] sizes) {
            this.activity = activity;
            this.sizes = sizes;
            for (int i = 0; i < sizes.length; i++) names.add("g" + i);
        }

        int size(int g) { return sizes[Integer.parseInt(names.get(g).substring(1))]; }

        public int getGroupCount() { return names.size(); }

        public int getChildrenCount(int g) { return size(g); }

        public Object getGroup(int g) { return names.get(g); }

        public Object getChild(int g, int c) { return names.get(g) + "/" + c; }

        public long getGroupId(int g) { return Integer.parseInt(names.get(g).substring(1)); }

        public long getChildId(int g, int c) { return c; }

        public boolean hasStableIds() { return true; }

        public View getGroupView(int g, boolean expanded, View convertView, ViewGroup parent) {
            TextView tv = convertView != null ? (TextView) convertView : new TextView(activity);
            tv.setText(names.get(g));
            return tv;
        }

        public View getChildView(int g, int c, boolean last, View convertView, ViewGroup parent) {
            TextView tv = convertView != null ? (TextView) convertView : new TextView(activity);
            tv.setText(getChild(g, c) + (last ? " last" : ""));
            return tv;
        }

        public boolean isChildSelectable(int g, int c) { return c != 0; }
    }

    private static void expandable(Activity activity) {
        long p = ExpandableListView.getPackedPositionForChild(3, 5);
        check("packed child", ExpandableListView.getPackedPositionType(p) == ExpandableListView.PACKED_POSITION_TYPE_CHILD
                && ExpandableListView.getPackedPositionGroup(p) == 3 && ExpandableListView.getPackedPositionChild(p) == 5,
                Long.toHexString(p));
        long pg = ExpandableListView.getPackedPositionForGroup(4);
        check("packed group", ExpandableListView.getPackedPositionType(pg) == ExpandableListView.PACKED_POSITION_TYPE_GROUP
                && ExpandableListView.getPackedPositionGroup(pg) == 4 && ExpandableListView.getPackedPositionChild(pg) == -1
                && ExpandableListView.getPackedPositionType(ExpandableListView.PACKED_POSITION_VALUE_NULL)
                        == ExpandableListView.PACKED_POSITION_TYPE_NULL, Long.toHexString(pg));

        Groups groups = new Groups(activity, new int[] {2, 3, 0, 4});
        ExpandableListView list = new ExpandableListView(activity);
        list.setAdapter(groups);
        check("collapsed count", list.getAdapter().getCount() == 4, list.getAdapter().getCount());
        list.expandGroup(1);
        list.expandGroup(3);
        // Flat: g0, g1, g1/0, g1/1, g1/2, g2, g3, g3/0..3
        check("expanded count", list.getAdapter().getCount() == 11 && list.isGroupExpanded(1)
                && !list.isGroupExpanded(0), list.getAdapter().getCount());
        long at3 = list.getExpandableListPosition(3);
        check("flat to child", ExpandableListView.getPackedPositionGroup(at3) == 1
                && ExpandableListView.getPackedPositionChild(at3) == 1, Long.toHexString(at3));
        long at5 = list.getExpandableListPosition(5);
        check("flat to group", at5 == ExpandableListView.getPackedPositionForGroup(2), Long.toHexString(at5));
        check("child to flat", list.getFlatListPosition(ExpandableListView.getPackedPositionForChild(3, 2)) == 9,
                list.getFlatListPosition(ExpandableListView.getPackedPositionForChild(3, 2)));
        check("hidden child", list.getFlatListPosition(ExpandableListView.getPackedPositionForChild(0, 1)) < 0, "");
        View last = list.getAdapter().getView(10, null, list);
        check("last child", "g3/3 last".equals(((TextView) last).getText().toString()), ((TextView) last).getText());
        check("child selectable", !list.getAdapter().isEnabled(2) && list.getAdapter().isEnabled(3)
                && list.getAdapter().isEnabled(5), "");
        check("view types", list.getAdapter().getViewTypeCount() == 2 && list.getAdapter().getItemViewType(0) == 0
                && list.getAdapter().getItemViewType(2) == 1, list.getAdapter().getViewTypeCount());
        // Remove g0: the expanded groups follow their ids to positions 0 and 2.
        groups.names.remove(0);
        groups.notifyDataSetChanged();
        check("sync expanded", list.isGroupExpanded(0) && !list.isGroupExpanded(1) && list.isGroupExpanded(2)
                && list.getAdapter().getCount() == 10, list.getAdapter().getCount());
        list.collapseGroup(0);
        check("collapse", !list.isGroupExpanded(0) && list.getAdapter().getCount() == 7,
                list.getAdapter().getCount());
        // Groups are now g1 (3 children), g2 (none), g3 (4).
        list.setSelectedChild(0, 2, true);
        check("select child", list.isGroupExpanded(0) && list.getSelectedItemPosition() == 3
                && list.getSelectedPosition() == ExpandableListView.getPackedPositionForChild(0, 2),
                Long.toHexString(list.getSelectedPosition()));
        boolean threw = false;
        try {
            list.setAdapter((android.widget.ListAdapter) null);
        } catch (RuntimeException e) {
            threw = true;
        }
        check("list adapter rejected", threw, "");
    }

    /** Groups (_id, name) whose children (_id, name) come from a per-group cursor, counted. */
    static final class Tree extends SimpleCursorTreeAdapter {
        int fetches;
        final List<MatrixCursor> made = new ArrayList<MatrixCursor>();

        Tree(Activity activity, Cursor groups) {
            super(activity, groups, R.layout.row, android.R.layout.simple_expandable_list_item_1,
                    new String[] {"name", "name"}, new int[] {R.id.name, android.R.id.text1},
                    android.R.layout.simple_list_item_1, R.layout.row,
                    new String[] {"name", "name"}, new int[] {android.R.id.text1, R.id.name});
        }

        @Override
        protected Cursor getChildrenCursor(Cursor group) {
            fetches++;
            long id = group.getLong(group.getColumnIndexOrThrow("_id"));
            // Group 30 loads its children later through setChildrenCursor.
            if (id == 30) return null;
            MatrixCursor c = new MatrixCursor(new String[] {"_id", "name"});
            for (int i = 0; i < id / 10 + 1; i++) c.addRow(new Object[] {id + i, group.getString(1) + i});
            made.add(c);
            return c;
        }
    }

    private static MatrixCursor treeGroups(String... names) {
        MatrixCursor c = new MatrixCursor(new String[] {"_id", "name"});
        for (int i = 0; i < names.length; i++) c.addRow(new Object[] {10 * (i + 1), names[i]});
        return c;
    }

    private static String text(View v, int id) {
        View t = v.findViewById(id);
        return t instanceof TextView ? ((TextView) t).getText().toString() : null;
    }

    private static void cursorTree(Activity activity) {
        MatrixCursor groups = treeGroups("a", "b", "c");
        final Tree tree = new Tree(activity, groups);
        check("tree groups", tree.getGroupCount() == 3 && tree.getGroupId(1) == 20 && tree.hasStableIds()
                && tree.fetches == 0 && tree.getCursor() == groups, tree.getGroupCount() + " " + tree.fetches);
        // Group 10 has 2 children, group 20 has 3; each children cursor is fetched once.
        check("tree children", tree.getChildrenCount(0) == 2 && tree.getChildrenCount(1) == 3
                && tree.getChildrenCount(0) == 2 && tree.fetches == 2 && tree.getChildId(1, 2) == 22
                && "b2".equals(tree.getChild(1, 2).getString(1)), tree.fetches);
        check("tree async", tree.getChildrenCount(2) == 0 && tree.fetches == 3, tree.getChildrenCount(2));
        MatrixCursor late = new MatrixCursor(new String[] {"_id", "name"});
        late.addRow(new Object[] {77, "late"});
        tree.setChildrenCursor(2, late);
        check("tree set children", tree.getChildrenCount(2) == 1 && tree.getChildId(2, 0) == 77
                && tree.fetches == 3 && !tree.made.get(0).isClosed(), tree.getChildrenCount(2));

        View collapsed = tree.getGroupView(0, false, null, null);
        View expanded = tree.getGroupView(1, true, null, null);
        check("tree group layouts", "a".equals(text(collapsed, R.id.name)) && collapsed.findViewById(R.id.check) != null
                && "b".equals(text(expanded, android.R.id.text1)) && expanded.findViewById(R.id.check) == null,
                text(collapsed, R.id.name) + " " + text(expanded, android.R.id.text1));
        View child = tree.getChildView(1, 0, false, null, null);
        View last = tree.getChildView(1, 2, true, null, null);
        check("tree child layouts", "b0".equals(text(child, android.R.id.text1)) && child.findViewById(R.id.check) == null
                && "b2".equals(text(last, R.id.name)) && last.findViewById(R.id.check) != null,
                text(child, android.R.id.text1) + " " + text(last, R.id.name));
        tree.setViewBinder((view, cursor, column) -> {
            if (!(view instanceof TextView)) return false;
            ((TextView) view).setText("<" + cursor.getString(column) + ">");
            return true;
        });
        tree.getChildView(1, 1, false, child, null);
        check("tree binder", "<b1>".equals(text(child, android.R.id.text1)) && tree.getViewBinder() != null,
                text(child, android.R.id.text1));
        tree.setViewBinder(null);

        // notifyDataSetChanged(false) keeps the children cursors, the plain call closes them.
        final int[] changes = {0};
        tree.registerDataSetObserver(new DataSetObserver() {
            public void onChanged() { changes[0]++; }
        });
        tree.notifyDataSetChanged(false);
        boolean kept = !tree.made.get(0).isClosed() && !tree.made.get(1).isClosed();
        tree.notifyDataSetChanged();
        check("tree release", kept && changes[0] == 2 && tree.made.get(0).isClosed() && tree.made.get(1).isClosed()
                && late.isClosed() && !groups.isClosed(), changes[0] + " " + kept);

        ExpandableListView list = new ExpandableListView(activity);
        list.setAdapter(tree);
        list.expandGroup(1);
        int flat = list.getAdapter().getCount();
        MatrixCursor bKids = (MatrixCursor) tree.getChild(1, 0);
        list.collapseGroup(1);
        check("tree collapse closes", flat == 6 && bKids.isClosed() && list.getAdapter().getCount() == 3,
                flat + " " + bKids.isClosed());

        // setGroupCursor keeps the children cursors, changeCursor drops them; both close the old groups.
        MatrixCursor aKids = (MatrixCursor) tree.getChild(0, 0);
        MatrixCursor groups2 = treeGroups("p", "q");
        tree.setGroupCursor(groups2);
        check("tree set groups", groups.isClosed() && !aKids.isClosed() && tree.getGroupCount() == 2
                && tree.getCursor() == groups2 && list.getAdapter().getCount() == 2, tree.getGroupCount());
        MatrixCursor groups3 = treeGroups("x");
        tree.changeCursor(groups3);
        check("tree change cursor", groups2.isClosed() && aKids.isClosed() && tree.getGroupCount() == 1
                && "x0".equals(tree.getChild(0, 0).getString(1)), tree.getGroupCount());
        tree.changeCursor(null);
        check("tree no cursor", tree.getGroupCount() == 0 && groups3.isClosed() && list.getAdapter().getCount() == 0
                && "".equals(tree.convertToString(null)), tree.getGroupCount());

        tree.setFilterQueryProvider(constraint -> treeGroups("f:" + constraint, "g"));
        tree.getFilter().filter("z", count -> check("tree filter", count == 2 && tree.getGroupCount() == 2
                && "f:z".equals(tree.getGroup(0).getString(1)), count + " " + tree.getGroupCount()));
    }

    private static void tokenizer() {
        MultiAutoCompleteTextView.CommaTokenizer t = new MultiAutoCompleteTextView.CommaTokenizer();
        String s = "ann, bob, ca";
        check("token start", t.findTokenStart(s, s.length()) == 10, t.findTokenStart(s, s.length()));
        check("token end", t.findTokenEnd(s, 5) == 8, t.findTokenEnd(s, 5));
        check("terminate", "carl, ".contentEquals(t.terminateToken("carl"))
                && "x,".contentEquals(t.terminateToken("x,")), t.terminateToken("carl"));
    }
}
