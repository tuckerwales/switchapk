package android.widget;

/** An {@link ExpandableListAdapter} with several group or child view types (AOSP). */
public interface HeterogeneousExpandableList {
    int getGroupType(int groupPosition);

    int getChildType(int groupPosition, int childPosition);

    int getGroupTypeCount();

    int getChildTypeCount();
}
