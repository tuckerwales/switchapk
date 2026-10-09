package java.nio.file;

public class FileSystemException extends java.io.IOException {
    private final String file;
    private final String other;

    public FileSystemException(String file) {
        super((String) null);
        this.file = file;
        this.other = null;
    }

    public FileSystemException(String file, String other, String reason) {
        super(reason);
        this.file = file;
        this.other = other;
    }

    public String getFile() {
        return file;
    }

    public String getOtherFile() {
        return other;
    }

    public String getReason() {
        return super.getMessage();
    }

    public String getMessage() {
        if (file == null && other == null) {
            return getReason();
        }
        StringBuilder sb = new StringBuilder();
        if (file != null) {
            sb.append(file);
        }
        if (other != null) {
            sb.append(" -> ").append(other);
        }
        if (getReason() != null) {
            sb.append(": ").append(getReason());
        }
        return sb.toString();
    }
}
