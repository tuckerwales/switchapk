package java.util;

public abstract class ListResourceBundle extends ResourceBundle {
    private volatile Map<String, Object> lookup;

    public ListResourceBundle() {
    }

    public final Object handleGetObject(String key) {
        if (key == null) {
            throw new NullPointerException();
        }
        return table().get(key);
    }

    public Enumeration<String> getKeys() {
        return ResourceBundle.keys(table().keySet(), parent);
    }

    protected Set<String> handleKeySet() {
        return table().keySet();
    }

    protected abstract Object[][] getContents();

    private Map<String, Object> table() {
        Map<String, Object> t = lookup;
        if (t == null) {
            synchronized (this) {
                t = lookup;
                if (t == null) {
                    Object[][] contents = getContents();
                    HashMap<String, Object> temp = new HashMap<String, Object>(contents.length);
                    for (Object[] content : contents) {
                        String key = (String) content[0];
                        Object value = content[1];
                        if (key == null || value == null) {
                            throw new NullPointerException();
                        }
                        temp.put(key, value);
                    }
                    lookup = t = temp;
                }
            }
        }
        return t;
    }
}
