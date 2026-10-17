package android.webkit;

import java.util.ArrayList;

/** Framework-internal: WebView's navigation history. Thread-safe. */
final class BackForwardListImpl extends WebBackForwardList {
    private static final long serialVersionUID = 1L;

    private final ArrayList<HistoryItemImpl> mItems = new ArrayList<HistoryItemImpl>();
    private int mCurrentIndex = -1;

    BackForwardListImpl() {
    }

    /** Drops forward entries after the current one, appends a new entry and makes it current. */
    synchronized void add(String url, String title) {
        add(url, url, title);
    }

    synchronized void add(String url, String originalUrl, String title) {
        while (mItems.size() > mCurrentIndex + 1) mItems.remove(mItems.size() - 1);
        mItems.add(new HistoryItemImpl(url, originalUrl, title));
        mCurrentIndex = mItems.size() - 1;
    }

    /** Ignored when out of range. */
    synchronized void setCurrentIndex(int index) {
        if (index >= 0 && index < mItems.size()) mCurrentIndex = index;
    }

    /** Sets the title of the current entry (for titles that arrive after the page was added). */
    synchronized void setCurrentTitle(String title) {
        if (mCurrentIndex >= 0) mItems.get(mCurrentIndex).setTitle(title);
    }

    /** Keeps only a single current entry for url/title, or empties the list when url is null. */
    synchronized void clear(String currentUrl, String currentTitle) {
        mItems.clear();
        mCurrentIndex = -1;
        if (currentUrl != null) add(currentUrl, currentTitle);
    }

    /** A snapshot that later navigation does not change. */
    synchronized BackForwardListImpl copy() {
        BackForwardListImpl l = new BackForwardListImpl();
        for (HistoryItemImpl item : mItems) l.mItems.add(item.clone());
        l.mCurrentIndex = mCurrentIndex;
        return l;
    }

    @Override
    public synchronized WebHistoryItem getCurrentItem() {
        return getItemAtIndex(mCurrentIndex);
    }

    @Override
    public synchronized int getCurrentIndex() {
        return mCurrentIndex;
    }

    @Override
    public synchronized WebHistoryItem getItemAtIndex(int index) {
        if (index < 0 || index >= mItems.size()) return null;
        return mItems.get(index);
    }

    @Override
    public synchronized int getSize() {
        return mItems.size();
    }

    @Override
    protected synchronized BackForwardListImpl clone() {
        return copy();
    }
}
