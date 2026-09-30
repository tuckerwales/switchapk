package java.nio.file;

public class FileSystemException extends java.io.IOException {
    public FileSystemException(String file) {
        super(file);
    }
}
