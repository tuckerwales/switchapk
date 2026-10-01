package android.widget;

/** A group or child position in an expandable list, plus its flat list position (AOSP, hidden). */
class ExpandableListPosition {
    public static final int CHILD = 1;
    public static final int GROUP = 2;

    public int groupPos;
    public int childPos;
    int flatListPos;
    public int type;

    ExpandableListPosition() {}

    long getPackedPosition() {
        if (type == CHILD) return ExpandableListView.getPackedPositionForChild(groupPos, childPos);
        return ExpandableListView.getPackedPositionForGroup(groupPos);
    }

    static ExpandableListPosition obtainGroupPosition(int groupPosition) { return obtain(GROUP, groupPosition, 0, 0); }

    static ExpandableListPosition obtainChildPosition(int groupPosition, int childPosition) {
        return obtain(CHILD, groupPosition, childPosition, 0);
    }

    static ExpandableListPosition obtainPosition(long packedPosition) {
        if (packedPosition == ExpandableListView.PACKED_POSITION_VALUE_NULL) return null;
        ExpandableListPosition elp = new ExpandableListPosition();
        elp.groupPos = ExpandableListView.getPackedPositionGroup(packedPosition);
        if (ExpandableListView.getPackedPositionType(packedPosition) == ExpandableListView.PACKED_POSITION_TYPE_CHILD) {
            elp.type = CHILD;
            elp.childPos = ExpandableListView.getPackedPositionChild(packedPosition);
        } else {
            elp.type = GROUP;
        }
        return elp;
    }

    static ExpandableListPosition obtain(int type, int groupPos, int childPos, int flatListPos) {
        ExpandableListPosition elp = new ExpandableListPosition();
        elp.type = type;
        elp.groupPos = groupPos;
        elp.childPos = childPos;
        elp.flatListPos = flatListPos;
        return elp;
    }

    void recycle() {}
}
