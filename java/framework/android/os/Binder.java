package android.os;

public class Binder implements IBinder {
    private IInterface mOwner;
    private String mDescriptor;

    public Binder() {}
    public Binder(String descriptor) { mDescriptor = descriptor; }

    public static final int getCallingPid() { return Process.myPid(); }
    public static final int getCallingUid() { return Process.myUid(); }
    public static final UserHandle getCallingUserHandle() { return UserHandle.SYSTEM; }
    public static final long clearCallingIdentity() { return 0; }
    public static final void restoreCallingIdentity(long token) {}
    public static final void flushPendingCommands() {}
    public static final void joinThreadPool() {}

    public void attachInterface(IInterface owner, String descriptor) {
        mOwner = owner;
        mDescriptor = descriptor;
    }

    public String getInterfaceDescriptor() { return mDescriptor; }
    public boolean pingBinder() { return true; }
    public boolean isBinderAlive() { return true; }
    public IInterface queryLocalInterface(String descriptor) { return descriptor != null && descriptor.equals(mDescriptor) ? mOwner : null; }
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException { return false; }
    public void dump(java.io.FileDescriptor fd, String[] args) {}
    public void dumpAsync(java.io.FileDescriptor fd, String[] args) {}
    public final boolean transact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (data != null) data.setDataPosition(0);
        boolean r = onTransact(code, data, reply, flags);
        if (reply != null) reply.setDataPosition(0);
        return r;
    }
    public void linkToDeath(DeathRecipient recipient, int flags) {}
    public boolean unlinkToDeath(DeathRecipient recipient, int flags) { return true; }
}
