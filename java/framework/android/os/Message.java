package android.os;

public final class Message implements Parcelable {
    public int what;
    public int arg1;
    public int arg2;
    public Object obj;
    public Messenger replyTo;
    public int sendingUid = -1;

    int flags;
    long when;
    Bundle data;
    Handler target;
    Runnable callback;
    Message next;

    static final int FLAG_IN_USE = 1;
    static final int FLAG_ASYNCHRONOUS = 2;

    private static final Object sPoolSync = new Object();
    private static Message sPool;
    private static int sPoolSize = 0;
    private static final int MAX_POOL_SIZE = 50;

    public static Message obtain() {
        synchronized (sPoolSync) {
            if (sPool != null) {
                Message m = sPool;
                sPool = m.next;
                m.next = null;
                m.flags = 0;
                sPoolSize--;
                return m;
            }
        }
        return new Message();
    }

    public static Message obtain(Message orig) {
        Message m = obtain();
        m.what = orig.what;
        m.arg1 = orig.arg1;
        m.arg2 = orig.arg2;
        m.obj = orig.obj;
        m.replyTo = orig.replyTo;
        m.sendingUid = orig.sendingUid;
        if (orig.data != null) m.data = new Bundle(orig.data);
        m.target = orig.target;
        m.callback = orig.callback;
        return m;
    }

    public static Message obtain(Handler h) {
        Message m = obtain();
        m.target = h;
        return m;
    }

    public static Message obtain(Handler h, Runnable callback) {
        Message m = obtain();
        m.target = h;
        m.callback = callback;
        return m;
    }

    public static Message obtain(Handler h, int what) {
        Message m = obtain();
        m.target = h;
        m.what = what;
        return m;
    }

    public static Message obtain(Handler h, int what, Object obj) {
        Message m = obtain();
        m.target = h;
        m.what = what;
        m.obj = obj;
        return m;
    }

    public static Message obtain(Handler h, int what, int arg1, int arg2) {
        Message m = obtain();
        m.target = h;
        m.what = what;
        m.arg1 = arg1;
        m.arg2 = arg2;
        return m;
    }

    public static Message obtain(Handler h, int what, int arg1, int arg2, Object obj) {
        Message m = obtain();
        m.target = h;
        m.what = what;
        m.arg1 = arg1;
        m.arg2 = arg2;
        m.obj = obj;
        return m;
    }

    public void recycle() {
        if (isInUse()) return;
        recycleUnchecked();
    }

    void recycleUnchecked() {
        flags = FLAG_IN_USE;
        what = 0;
        arg1 = 0;
        arg2 = 0;
        obj = null;
        replyTo = null;
        sendingUid = -1;
        when = 0;
        target = null;
        callback = null;
        data = null;
        synchronized (sPoolSync) {
            if (sPoolSize < MAX_POOL_SIZE) {
                next = sPool;
                sPool = this;
                sPoolSize++;
            }
        }
    }

    public void copyFrom(Message o) {
        this.flags = o.flags & ~FLAG_IN_USE;
        this.what = o.what;
        this.arg1 = o.arg1;
        this.arg2 = o.arg2;
        this.obj = o.obj;
        this.replyTo = o.replyTo;
        this.sendingUid = o.sendingUid;
        this.data = o.data != null ? (Bundle) o.data.clone() : null;
    }

    public long getWhen() { return when; }
    public void setTarget(Handler target) { this.target = target; }
    public Handler getTarget() { return target; }
    public Runnable getCallback() { return callback; }
    public Message setCallback(Runnable r) { callback = r; return this; }

    public Bundle getData() {
        if (data == null) data = new Bundle();
        return data;
    }

    public Bundle peekData() { return data; }
    public void setData(Bundle data) { this.data = data; }
    public Message setWhat(int what) { this.what = what; return this; }

    public void sendToTarget() { target.sendMessage(this); }

    public boolean isAsynchronous() { return (flags & FLAG_ASYNCHRONOUS) != 0; }

    public void setAsynchronous(boolean async) {
        if (async) flags |= FLAG_ASYNCHRONOUS;
        else flags &= ~FLAG_ASYNCHRONOUS;
    }

    boolean isInUse() { return ((flags & FLAG_IN_USE) == FLAG_IN_USE); }
    void markInUse() { flags |= FLAG_IN_USE; }

    public Message() {}

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("{ when=").append(when - SystemClock.uptimeMillis()).append("ms");
        if (target != null) {
            if (callback != null) b.append(" callback=").append(callback.getClass().getName());
            else b.append(" what=").append(what);
            if (arg1 != 0) b.append(" arg1=").append(arg1);
            if (arg2 != 0) b.append(" arg2=").append(arg2);
            if (obj != null) b.append(" obj=").append(obj);
            b.append(" target=").append(target.getClass().getName());
        } else {
            b.append(" barrier=").append(arg1);
        }
        return b.append(" }").toString();
    }

    public int describeContents() { return 0; }
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(what);
        dest.writeInt(arg1);
        dest.writeInt(arg2);
        dest.writeValue(obj);
        dest.writeBundle(data);
    }

    public static final Parcelable.Creator<Message> CREATOR = new Parcelable.Creator<Message>() {
        public Message createFromParcel(Parcel source) {
            Message msg = Message.obtain();
            msg.what = source.readInt();
            msg.arg1 = source.readInt();
            msg.arg2 = source.readInt();
            msg.obj = source.readValue(null);
            msg.data = source.readBundle();
            return msg;
        }

        public Message[] newArray(int size) { return new Message[size]; }
    };
}
