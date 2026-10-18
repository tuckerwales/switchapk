package android.view.contentcapture;

import android.graphics.Insets;
import android.view.View;
import android.view.ViewStructure;
import android.view.autofill.AutofillId;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Session used when notifying the content capture service about events in the UI.
 *
 * switchapk has no content capture service, so the framework never hands a session to apps
 * ({@link View#getContentCaptureSession()} returns null and there is no public constructor). The
 * class still keeps the client-side state AOSP keeps (id, context, child sessions, destroyed flag);
 * every notification is dropped, which is what AOSP does once a session is disabled.
 */
public abstract class ContentCaptureSession implements AutoCloseable {
    private static final Random sIdGenerator = new Random();

    private final int mId;
    private ContentCaptureSessionId mContentCaptureSessionId;
    private ContentCaptureContext mClientContext;
    private ArrayList<ContentCaptureSession> mChildren;
    private boolean mDestroyed;

    ContentCaptureSession() {
        int id;
        do {
            id = sIdGenerator.nextInt();
        } while (id == 0);
        mId = id;
    }

    ContentCaptureSession(ContentCaptureContext context) {
        this();
        mClientContext = context;
    }

    public final ContentCaptureSessionId getContentCaptureSessionId() {
        if (mContentCaptureSessionId == null) mContentCaptureSessionId = new ContentCaptureSessionId(mId);
        return mContentCaptureSessionId;
    }

    public final ContentCaptureSession createContentCaptureSession(ContentCaptureContext context) {
        if (context == null) throw new NullPointerException("context");
        ContentCaptureSession child = new ChildContentCaptureSession(context);
        synchronized (this) {
            if (mChildren == null) mChildren = new ArrayList<>(1);
            mChildren.add(child);
        }
        return child;
    }

    public final void setContentCaptureContext(ContentCaptureContext context) {
        mClientContext = context;
    }

    public final ContentCaptureContext getContentCaptureContext() { return mClientContext; }

    public final void destroy() {
        synchronized (this) {
            if (mDestroyed) return;
            mDestroyed = true;
            if (mChildren != null) {
                for (int i = 0; i < mChildren.size(); i++) mChildren.get(i).destroy();
            }
        }
    }

    @Override
    public void close() { destroy(); }

    public final void notifyViewAppeared(ViewStructure node) {
        if (node == null) throw new NullPointerException("node");
    }

    public final void notifyViewDisappeared(AutofillId id) {
        if (id == null) throw new NullPointerException("id");
    }

    public final void notifyViewsAppeared(List<ViewStructure> appearedNodes) {
        if (appearedNodes == null) throw new NullPointerException("appearedNodes");
    }

    public final void notifyViewsDisappeared(AutofillId hostId, long[] virtualIds) {
        if (hostId == null) throw new NullPointerException("hostId");
        if (virtualIds == null || virtualIds.length == 0) {
            throw new IllegalArgumentException("virtual ids cannot be empty");
        }
    }

    public final void notifyViewTextChanged(AutofillId id, CharSequence text) {
        if (id == null) throw new NullPointerException("id");
    }

    public final void notifyViewInsetsChanged(Insets viewInsets) {
        if (viewInsets == null) throw new NullPointerException("viewInsets");
    }

    public final void notifySessionResumed() {}

    public final void notifySessionPaused() {}

    public final ViewStructure newViewStructure(View view) {
        return new SimpleViewStructure();
    }

    public AutofillId newAutofillId(AutofillId hostId, long virtualChildId) {
        if (hostId == null) throw new NullPointerException("hostId");
        return new AutofillId(hostId, (int) virtualChildId);
    }

    public final ViewStructure newVirtualViewStructure(AutofillId parentId, long virtualId) {
        return new SimpleViewStructure();
    }

    @Override
    public String toString() { return Integer.toString(mId); }

    /** framework-internal: session created by {@link #createContentCaptureSession}. */
    private static final class ChildContentCaptureSession extends ContentCaptureSession {
        ChildContentCaptureSession(ContentCaptureContext context) { super(context); }
    }

    /** framework-internal: structure returned by the newViewStructure calls; nothing reads it. */
    private static final class SimpleViewStructure extends ViewStructure {}
}
