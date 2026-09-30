package java.nio.file;

import java.io.File;

public interface Path extends Comparable<Path>, Iterable<Path> {
    Path getFileName();

    Path getParent();

    Path getRoot();

    boolean isAbsolute();

    Path resolve(String other);

    Path resolve(Path other);

    Path toAbsolutePath();

    Path normalize();

    File toFile();

    int getNameCount();

    Path getName(int index);

    boolean startsWith(String other);

    boolean endsWith(String other);

    Path relativize(Path other);

    default Path resolveSibling(String other) {
        Path parent = getParent();
        return parent == null ? Paths.get(other) : parent.resolve(other);
    }
}
