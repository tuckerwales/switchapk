package java.lang;

public class Package {
    private final String name;

    Package(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static Package getPackage(String name) {
        return new Package(name);
    }

    public String getImplementationVersion() {
        return null;
    }

    public String getSpecificationVersion() {
        return null;
    }

    public String toString() {
        return "package " + name;
    }

    public int hashCode() {
        return name.hashCode();
    }
}
