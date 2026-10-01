package com.example.adapters;

import android.app.Activity;
import android.database.MatrixCursor;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ExpandableListView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AutoCompleteTextView over an ArrayAdapter, a ListView over a SimpleCursorAdapter (MatrixCursor
 * with a ViewBinder for the colour swatch) and an ExpandableListView over a
 * SimpleExpandableListAdapter with the framework's list item layouts.
 */
public class MainActivity extends Activity {
    static final String[] COUNTRIES = {
        "Cambodia", "Cameroon", "Canada", "Chile", "China", "Colombia", "Croatia", "Cuba", "Denmark", "France",
    };
    static final String[] GROUPS = {"Fruit", "Vegetables", "Grains"};
    static final String[][] CHILDREN = {
        {"Apple", "Banana", "Cherry"}, {"Carrot", "Leek"}, {"Oats", "Rice", "Wheat", "Barley"},
    };

    private TextView mStatus;

    void status(String s) {
        mStatus.setText(s);
        Log.i("ADAPT", s);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.main);
        mStatus = (TextView) findViewById(R.id.status);
        AdapterChecks.run(this);

        AutoCompleteTextView auto = (AutoCompleteTextView) findViewById(R.id.auto);
        auto.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, COUNTRIES));
        auto.setOnItemClickListener((parent, view, position, id) ->
                status("picked " + parent.getItemAtPosition(position)));

        MatrixCursor cursor = new MatrixCursor(new String[] {"_id", "name", "color"});
        int[] colors = {0xFFE53935, 0xFF43A047, 0xFF1E88E5, 0xFFFDD835, 0xFF8E24AA};
        String[] names = {"Red", "Green", "Blue", "Yellow", "Purple"};
        for (int i = 0; i < names.length; i++) cursor.addRow(new Object[] {100 + i, names[i], colors[i]});
        SimpleCursorAdapter cursorAdapter = new SimpleCursorAdapter(this, R.layout.row, cursor,
                new String[] {"name", "color"}, new int[] {R.id.name, R.id.swatch}, 0);
        cursorAdapter.setViewBinder((view, c, column) -> {
            if (view.getId() != R.id.swatch) return false;
            view.setBackgroundColor(c.getInt(column));
            return true;
        });
        ListView list = (ListView) findViewById(R.id.cursor);
        list.setAdapter(cursorAdapter);
        list.setOnItemClickListener((parent, view, position, id) -> status("row " + position + " id " + id));

        List<Map<String, String>> groups = new ArrayList<Map<String, String>>();
        List<List<Map<String, String>>> children = new ArrayList<List<Map<String, String>>>();
        for (int g = 0; g < GROUPS.length; g++) {
            Map<String, String> gm = new HashMap<String, String>();
            gm.put("name", GROUPS[g]);
            groups.add(gm);
            List<Map<String, String>> kids = new ArrayList<Map<String, String>>();
            for (String child : CHILDREN[g]) {
                Map<String, String> cm = new HashMap<String, String>();
                cm.put("name", child);
                kids.add(cm);
            }
            children.add(kids);
        }
        ExpandableListView expand = (ExpandableListView) findViewById(R.id.expand);
        expand.setAdapter(new SimpleExpandableListAdapter(this, groups,
                android.R.layout.simple_expandable_list_item_1, new String[] {"name"}, new int[] {android.R.id.text1},
                children, android.R.layout.simple_list_item_1, new String[] {"name"}, new int[] {android.R.id.text1}));
        expand.setOnGroupExpandListener(g -> Log.i("ADAPT", "expand " + g));
        expand.setOnGroupCollapseListener(g -> Log.i("ADAPT", "collapse " + g));
        expand.setOnChildClickListener((parent, v, g, c, id) -> {
            status("child " + CHILDREN[g][c]);
            return true;
        });
    }
}
