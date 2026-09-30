package java.lang;

public final class StackTraceElement implements java.io.Serializable {
    private final String declaringClass;
    private final String methodName;
    private final String fileName;
    private final int lineNumber;

    public StackTraceElement(String declaringClass, String methodName, String fileName, int lineNumber) {
        this.declaringClass = declaringClass;
        this.methodName = methodName;
        this.fileName = fileName;
        this.lineNumber = lineNumber;
    }

    public String getFileName() {
        return fileName;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getClassName() {
        return declaringClass;
    }

    public String getMethodName() {
        return methodName;
    }

    public boolean isNativeMethod() {
        return lineNumber == -2;
    }

    public String toString() {
        String loc;
        if (isNativeMethod()) {
            loc = "(Native Method)";
        } else if (fileName != null && lineNumber >= 0) {
            loc = "(" + fileName + ":" + lineNumber + ")";
        } else if (fileName != null) {
            loc = "(" + fileName + ")";
        } else {
            loc = "(Unknown Source)";
        }
        return declaringClass + "." + methodName + loc;
    }

    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof StackTraceElement)) {
            return false;
        }
        StackTraceElement e = (StackTraceElement) obj;
        return e.declaringClass.equals(declaringClass) && e.lineNumber == lineNumber
                && eq(methodName, e.methodName) && eq(fileName, e.fileName);
    }

    private static boolean eq(Object a, Object b) {
        return a == b || (a != null && a.equals(b));
    }

    public int hashCode() {
        return 31 * declaringClass.hashCode() + methodName.hashCode() + lineNumber;
    }
}
