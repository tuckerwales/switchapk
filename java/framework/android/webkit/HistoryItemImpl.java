package android.webkit;

import android.graphics.Bitmap;

/** Framework-internal: a history entry kept by BackForwardListImpl. */
final class HistoryItemImpl extends WebHistoryItem {
    private String mUrl;
    private String mOriginalUrl;
    private String mTitle;
    private Bitmap mFavicon;

    HistoryItemImpl(String url, String originalUrl, String title) {
        mUrl = url;
        mOriginalUrl = originalUrl != null ? originalUrl : url;
        mTitle = title;
    }

    void setUrl(String url) {
        mUrl = url;
    }

    void setTitle(String title) {
        mTitle = title;
    }

    void setFavicon(Bitmap favicon) {
        mFavicon = favicon;
    }

    @Override
    public String getUrl() {
        return mUrl;
    }

    @Override
    public String getOriginalUrl() {
        return mOriginalUrl;
    }

    @Override
    public String getTitle() {
        // Chromium returns the URL when a page has no title.
        return mTitle != null && mTitle.length() > 0 ? mTitle : mUrl;
    }

    @Override
    public Bitmap getFavicon() {
        return mFavicon;
    }

    @Override
    protected HistoryItemImpl clone() {
        HistoryItemImpl copy = new HistoryItemImpl(mUrl, mOriginalUrl, mTitle);
        copy.mFavicon = mFavicon;
        return copy;
    }
}
