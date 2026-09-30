package java.nio.file;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class Files {
    private Files() {
    }

    public static boolean exists(Path path, LinkOption... options) {
        return path.toFile().exists();
    }

    public static boolean notExists(Path path, LinkOption... options) {
        return !exists(path);
    }

    public static boolean isDirectory(Path path, LinkOption... options) {
        return path.toFile().isDirectory();
    }

    public static boolean isRegularFile(Path path, LinkOption... options) {
        return path.toFile().isFile();
    }

    public static boolean isReadable(Path path) {
        return path.toFile().canRead();
    }

    public static boolean isWritable(Path path) {
        return path.toFile().canWrite();
    }

    public static long size(Path path) throws IOException {
        return path.toFile().length();
    }

    public static Path createDirectories(Path dir, Object... attrs) throws IOException {
        dir.toFile().mkdirs();
        return dir;
    }

    public static Path createDirectory(Path dir, Object... attrs) throws IOException {
        if (!dir.toFile().mkdir()) {
            throw new IOException("cannot create " + dir);
        }
        return dir;
    }

    public static Path createFile(Path path, Object... attrs) throws IOException {
        if (!path.toFile().createNewFile()) {
            throw new IOException("file exists: " + path);
        }
        return path;
    }

    public static void delete(Path path) throws IOException {
        if (!path.toFile().delete()) {
            throw new NoSuchFileException(path.toString());
        }
    }

    public static boolean deleteIfExists(Path path) throws IOException {
        return path.toFile().delete();
    }

    public static Path move(Path source, Path target, CopyOption... options) throws IOException {
        if (!source.toFile().renameTo(target.toFile())) {
            throw new IOException("move failed");
        }
        return target;
    }

    public static Path copy(Path source, Path target, CopyOption... options) throws IOException {
        write(target, readAllBytes(source));
        return target;
    }

    public static long copy(InputStream in, Path target, CopyOption... options) throws IOException {
        OutputStream out = new FileOutputStream(target.toFile());
        try {
            return in.transferTo(out);
        } finally {
            out.close();
        }
    }

    public static long copy(Path source, OutputStream out) throws IOException {
        InputStream in = new FileInputStream(source.toFile());
        try {
            return in.transferTo(out);
        } finally {
            in.close();
        }
    }

    public static byte[] readAllBytes(Path path) throws IOException {
        File f = path.toFile();
        if (!f.exists()) {
            throw new NoSuchFileException(path.toString());
        }
        InputStream in = new FileInputStream(f);
        try {
            return in.readAllBytes();
        } finally {
            in.close();
        }
    }

    public static String readString(Path path) throws IOException {
        return new String(readAllBytes(path), StandardCharsets.UTF_8);
    }

    public static String readString(Path path, Charset cs) throws IOException {
        return new String(readAllBytes(path), cs);
    }

    public static List<String> readAllLines(Path path) throws IOException {
        return readAllLines(path, StandardCharsets.UTF_8);
    }

    public static List<String> readAllLines(Path path, Charset cs) throws IOException {
        BufferedReader r = newBufferedReader(path, cs);
        try {
            ArrayList<String> result = new ArrayList<String>();
            String line;
            while ((line = r.readLine()) != null) {
                result.add(line);
            }
            return result;
        } finally {
            r.close();
        }
    }

    public static Path write(Path path, byte[] bytes, OpenOption... options) throws IOException {
        boolean append = false;
        for (OpenOption o : options) {
            if (o == StandardOpenOption.APPEND) {
                append = true;
            }
        }
        OutputStream out = new FileOutputStream(path.toFile(), append);
        try {
            out.write(bytes);
        } finally {
            out.close();
        }
        return path;
    }

    public static Path writeString(Path path, CharSequence csq, OpenOption... options) throws IOException {
        return write(path, csq.toString().getBytes(StandardCharsets.UTF_8), options);
    }

    public static Path write(Path path, Iterable<? extends CharSequence> lines, OpenOption... options) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (CharSequence l : lines) {
            sb.append(l).append('\n');
        }
        return writeString(path, sb, options);
    }

    public static BufferedReader newBufferedReader(Path path) throws IOException {
        return newBufferedReader(path, StandardCharsets.UTF_8);
    }

    public static BufferedReader newBufferedReader(Path path, Charset cs) throws IOException {
        return new BufferedReader(new InputStreamReader(new FileInputStream(path.toFile()), cs));
    }

    public static BufferedWriter newBufferedWriter(Path path, OpenOption... options) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(newOutputStream(path, options), StandardCharsets.UTF_8));
    }

    public static InputStream newInputStream(Path path, OpenOption... options) throws IOException {
        return new FileInputStream(path.toFile());
    }

    public static OutputStream newOutputStream(Path path, OpenOption... options) throws IOException {
        boolean append = false;
        for (OpenOption o : options) {
            if (o == StandardOpenOption.APPEND) {
                append = true;
            }
        }
        return new FileOutputStream(path.toFile(), append);
    }

    public static java.util.stream.Stream<Path> list(Path dir) throws IOException {
        String[] names = dir.toFile().list();
        ArrayList<Path> l = new ArrayList<Path>();
        if (names != null) {
            for (String n : names) {
                l.add(dir.resolve(n));
            }
        }
        return l.stream();
    }
}
