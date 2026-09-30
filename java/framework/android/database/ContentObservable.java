package android.database;

import android.net.Uri;

public class ContentObservable extends Observable<ContentObserver> {
    @Override
    public void registerObserver(ContentObserver observer) { super.registerObserver(observer); }

    public void dispatchChange(boolean selfChange, Uri uri) {
        synchronized (mObservers) {
            for (ContentObserver observer : mObservers) {
                if (!selfChange || observer.deliverSelfNotifications()) observer.dispatchChange(selfChange, uri);
            }
        }
    }

    @Deprecated
    public void dispatchChange(boolean selfChange) { dispatchChange(selfChange, null); }

    @Deprecated
    public void notifyChange(boolean selfChange) {
        synchronized (mObservers) {
            for (ContentObserver observer : mObservers) observer.onChange(selfChange, null);
        }
    }
}
