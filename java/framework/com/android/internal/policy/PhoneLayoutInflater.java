package com.android.internal.policy;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;

/** framework-internal. Inflater that resolves unqualified tags in the widget, webkit and app packages. */
public class PhoneLayoutInflater extends LayoutInflater {
    private static final String[] sClassPrefixList = {"android.widget.", "android.webkit.", "android.app."};

    public PhoneLayoutInflater(Context context) { super(context); }

    protected PhoneLayoutInflater(LayoutInflater original, Context newContext) { super(original, newContext); }

    @Override
    protected View onCreateView(String name, AttributeSet attrs) throws ClassNotFoundException {
        for (String prefix : sClassPrefixList) {
            try {
                View view = createView(name, prefix, attrs);
                if (view != null) return view;
            } catch (ClassNotFoundException e) {
                // try the next prefix
            }
        }
        return super.onCreateView(name, attrs);
    }

    @Override
    public LayoutInflater cloneInContext(Context newContext) { return new PhoneLayoutInflater(this, newContext); }
}
