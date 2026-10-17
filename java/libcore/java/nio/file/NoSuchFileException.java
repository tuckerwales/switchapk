package java.nio.file;

public class NoSuchFileException extends FileSystemException {
    private static final long serialVersionUID = -1390291775875351931L;

    public NoSuchFileException(String file) {
        super(file);
    }
}
