package java.io;

public class FileWriter extends OutputStreamWriter {
    public FileWriter(String fileName) throws IOException {
        super(new FileOutputStream(fileName));
    }

    public FileWriter(String fileName, boolean append) throws IOException {
        super(new FileOutputStream(fileName, append));
    }

    public FileWriter(File file) throws IOException {
        super(new FileOutputStream(file));
    }

    public FileWriter(File file, boolean append) throws IOException {
        super(new FileOutputStream(file, append));
    }

    public FileWriter(FileDescriptor fd) {
        super(new FileOutputStream(fd));
    }

    public FileWriter(String fileName, java.nio.charset.Charset charset) throws IOException {
        super(new FileOutputStream(fileName), charset);
    }

    public FileWriter(File file, java.nio.charset.Charset charset, boolean append) throws IOException {
        super(new FileOutputStream(file, append), charset);
    }
}
