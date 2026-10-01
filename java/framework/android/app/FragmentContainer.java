package android.app;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

/** Where a host's fragments put their views (AOSP FragmentContainer). */
@Deprecated
public abstract class FragmentContainer {
    public FragmentContainer() {}

    public abstract <T extends View> T onFindViewById(int id);

    public abstract boolean onHasView();

    /** Hidden AOSP API: creates a fragment from its class name. */
    public Fragment instantiate(Context context, String className, Bundle arguments) {
        return Fragment.instantiate(context, className, arguments);
    }
}
