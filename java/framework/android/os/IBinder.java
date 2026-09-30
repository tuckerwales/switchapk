package android.os;

public interface IBinder {
    int FIRST_CALL_TRANSACTION = 0x00000001;
    int LAST_CALL_TRANSACTION = 0x00ffffff;
    int FLAG_ONEWAY = 0x00000001;
    int INTERFACE_TRANSACTION = ('_' << 24) | ('N' << 16) | ('T' << 8) | 'F';
    int DUMP_TRANSACTION = ('_' << 24) | ('D' << 16) | ('M' << 8) | 'P';
    int PING_TRANSACTION = ('_' << 24) | ('P' << 16) | ('N' << 8) | 'G';

    String getInterfaceDescriptor() throws RemoteException;
    boolean pingBinder();
    boolean isBinderAlive();
    IInterface queryLocalInterface(String descriptor);
    void dump(java.io.FileDescriptor fd, String[] args) throws RemoteException;
    void dumpAsync(java.io.FileDescriptor fd, String[] args) throws RemoteException;
    boolean transact(int code, Parcel data, Parcel reply, int flags) throws RemoteException;
    void linkToDeath(DeathRecipient recipient, int flags) throws RemoteException;
    boolean unlinkToDeath(DeathRecipient recipient, int flags);

    interface DeathRecipient {
        void binderDied();
    }
}
