package java.util.zip;

import java.nio.file.attribute.FileTime;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * A zip entry. getTimeLocal/setTimeLocal are missing because libcore has no java.time.
 */
public class ZipEntry implements ZipConstants, Cloneable {
    public static final int STORED = 0;
    public static final int DEFLATED = 8;

    String name;
    long xdostime = -1;
    FileTime mtime;
    FileTime atime;
    FileTime ctime;
    long crc = -1;
    long size = -1;
    long csize = -1;
    int method = -1;
    int flag = 0;
    byte[] extra;
    String comment;

    public ZipEntry(String name) {
        Objects.requireNonNull(name, "name");
        if (name.length() > 0xFFFF) {
            throw new IllegalArgumentException("entry name too long");
        }
        this.name = name;
    }

    public ZipEntry(ZipEntry e) {
        Objects.requireNonNull(e, "entry");
        name = e.name;
        xdostime = e.xdostime;
        mtime = e.mtime;
        atime = e.atime;
        ctime = e.ctime;
        crc = e.crc;
        size = e.size;
        csize = e.csize;
        method = e.method;
        flag = e.flag;
        extra = e.extra;
        comment = e.comment;
    }

    ZipEntry() {
    }

    public String getName() {
        return name;
    }

    public void setTime(long time) {
        this.xdostime = ZipUtils.javaToExtendedDosTime(time);
        if (xdostime != ZipUtils.DOSTIME_BEFORE_1980 && time <= ZipUtils.UPPER_DOSTIME_BOUND) {
            this.mtime = null;
        } else {
            this.mtime = FileTime.from(time, TimeUnit.MILLISECONDS);
        }
    }

    public long getTime() {
        if (mtime != null) {
            return mtime.toMillis();
        }
        return (xdostime != -1) ? ZipUtils.extendedDosToJavaTime(xdostime) : -1;
    }

    public ZipEntry setLastModifiedTime(FileTime time) {
        this.mtime = Objects.requireNonNull(time, "lastModifiedTime");
        this.xdostime = ZipUtils.javaToExtendedDosTime(time.to(TimeUnit.MILLISECONDS));
        return this;
    }

    public FileTime getLastModifiedTime() {
        if (mtime != null) {
            return mtime;
        }
        if (xdostime == -1) {
            return null;
        }
        return FileTime.from(getTime(), TimeUnit.MILLISECONDS);
    }

    public ZipEntry setLastAccessTime(FileTime time) {
        this.atime = Objects.requireNonNull(time, "lastAccessTime");
        return this;
    }

    public FileTime getLastAccessTime() {
        return atime;
    }

    public ZipEntry setCreationTime(FileTime time) {
        this.ctime = Objects.requireNonNull(time, "creationTime");
        return this;
    }

    public FileTime getCreationTime() {
        return ctime;
    }

    public void setSize(long size) {
        if (size < 0) {
            throw new IllegalArgumentException("invalid entry size");
        }
        this.size = size;
    }

    public long getSize() {
        return size;
    }

    public long getCompressedSize() {
        return csize;
    }

    public void setCompressedSize(long csize) {
        this.csize = csize;
    }

    public void setCrc(long crc) {
        if (crc < 0 || crc > 0xFFFFFFFFL) {
            throw new IllegalArgumentException("invalid entry crc-32");
        }
        this.crc = crc;
    }

    public long getCrc() {
        return crc;
    }

    public void setMethod(int method) {
        if (method != STORED && method != DEFLATED) {
            throw new IllegalArgumentException("invalid compression method");
        }
        this.method = method;
    }

    public int getMethod() {
        return method;
    }

    public void setExtra(byte[] extra) {
        setExtra0(extra, false);
    }

    /* Sets the extra field and reads the zip64 sizes (when doZip64) and the extended timestamp from it. */
    void setExtra0(byte[] extra, boolean doZip64) {
        if (extra != null) {
            if (extra.length > 0xFFFF) {
                throw new IllegalArgumentException("invalid extra field length");
            }
            int off = 0;
            int len = extra.length;
            while (off + 4 < len) {
                int tag = ZipUtils.get16(extra, off);
                int sz = ZipUtils.get16(extra, off + 2);
                off += 4;
                if (off + sz > len) {
                    break;
                }
                if (tag == ZipUtils.ZIP64_EXTID && doZip64) {
                    int pos = off;
                    if (size == ZipUtils.ZIP64_MAGICVAL && pos + 8 <= off + sz) {
                        size = ZipUtils.get64(extra, pos);
                        pos += 8;
                    }
                    if (csize == ZipUtils.ZIP64_MAGICVAL && pos + 8 <= off + sz) {
                        csize = ZipUtils.get64(extra, pos);
                    }
                } else if (tag == ZipUtils.EXTID_EXTT && sz >= 5) {
                    int flags = extra[off] & 0xff;
                    if ((flags & 1) != 0) {
                        long secs = (int) ZipUtils.get32(extra, off + 1);
                        mtime = FileTime.from(secs, TimeUnit.SECONDS);
                    }
                }
                off += sz;
            }
        }
        this.extra = extra;
    }

    public byte[] getExtra() {
        return extra;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getComment() {
        return comment;
    }

    public boolean isDirectory() {
        return name.endsWith("/");
    }

    public String toString() {
        return getName();
    }

    public int hashCode() {
        return name.hashCode();
    }

    public Object clone() {
        try {
            ZipEntry e = (ZipEntry) super.clone();
            e.extra = (extra == null) ? null : extra.clone();
            return e;
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }
}
