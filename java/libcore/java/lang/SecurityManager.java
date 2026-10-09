package java.lang;

/** Android never installs a security manager; every check passes, as on Android. */
@Deprecated
public class SecurityManager {
    @Deprecated
    protected boolean inCheck;

    public SecurityManager() {
    }

    public boolean getInCheck() {
        return inCheck;
    }

    protected Class[] getClassContext() {
        return new Class<?>[0];
    }

    protected ClassLoader currentClassLoader() {
        return null;
    }

    protected Class<?> currentLoadedClass() {
        return null;
    }

    protected int classDepth(String a0) {
        return -1;
    }

    protected int classLoaderDepth() {
        return -1;
    }

    protected boolean inClass(String a0) {
        return false;
    }

    protected boolean inClassLoader() {
        return false;
    }

    public Object getSecurityContext() {
        return null;
    }

    public void checkPermission(java.security.Permission a0) {
    }

    public void checkPermission(java.security.Permission a0, Object a1) {
    }

    public void checkCreateClassLoader() {
    }

    public void checkAccess(Thread a0) {
    }

    public void checkAccess(ThreadGroup a0) {
    }

    public void checkExit(int a0) {
    }

    public void checkExec(String a0) {
    }

    public void checkLink(String a0) {
    }

    public void checkRead(java.io.FileDescriptor a0) {
    }

    public void checkRead(String a0) {
    }

    public void checkRead(String a0, Object a1) {
    }

    public void checkWrite(java.io.FileDescriptor a0) {
    }

    public void checkWrite(String a0) {
    }

    public void checkDelete(String a0) {
    }

    public void checkConnect(String a0, int a1) {
    }

    public void checkConnect(String a0, int a1, Object a2) {
    }

    public void checkListen(int a0) {
    }

    public void checkAccept(String a0, int a1) {
    }

    public void checkMulticast(java.net.InetAddress a0) {
    }

    public void checkMulticast(java.net.InetAddress a0, byte a1) {
    }

    public void checkPropertiesAccess() {
    }

    public void checkPropertyAccess(String a0) {
    }

    public boolean checkTopLevelWindow(Object a0) {
        return true;
    }

    public void checkPrintJobAccess() {
    }

    public void checkSystemClipboardAccess() {
    }

    public void checkAwtEventQueueAccess() {
    }

    public void checkPackageAccess(String a0) {
    }

    public void checkPackageDefinition(String a0) {
    }

    public void checkSetFactory() {
    }

    public void checkMemberAccess(Class<?> a0, int a1) {
    }

    public void checkSecurityAccess(String a0) {
    }

    public ThreadGroup getThreadGroup() {
        return Thread.currentThread().getThreadGroup();
    }

}
