package java.text;

public interface AttributedCharacterIterator extends CharacterIterator {
    class Attribute implements java.io.Serializable {
        private final String name;

        protected Attribute(String name) {
            this.name = name;
        }

        protected String getName() {
            return name;
        }

        public String toString() {
            return getClass().getName() + "(" + name + ")";
        }
    }
}
