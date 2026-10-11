package android.app;

import android.content.ComponentName;
import android.content.Intent;

/** The one task an app has here: its launcher activity's. */
public class TaskInfo {
    public ComponentName baseActivity;
    public Intent baseIntent;
    public boolean isRunning;
    public int numActivities;
    public ComponentName origActivity;
    public ActivityManager.TaskDescription taskDescription;
    public int taskId;
    public ComponentName topActivity;

    TaskInfo() {
    }

    public boolean isVisible() {
        return isRunning;
    }

    public String toString() {
        return "TaskInfo{taskId=" + taskId + " isRunning=" + isRunning + " baseIntent=" + baseIntent
                + " baseActivity=" + baseActivity + " topActivity=" + topActivity + " origActivity=" + origActivity
                + " numActivities=" + numActivities + "}";
    }
}
