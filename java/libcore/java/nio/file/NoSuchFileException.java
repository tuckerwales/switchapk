package java.nio.file;

public class NoSuchFileException extends FileSystemException {
    public NoSuchFileException(String file) {
        super(file);
    }
}
