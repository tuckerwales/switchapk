package java.util;

public abstract class AbstractSequentialList<E> extends AbstractList<E> {
    protected AbstractSequentialList() {
    }

    public E get(int index) {
        return listIterator(index).next();
    }

    public E set(int index, E element) {
        ListIterator<E> e = listIterator(index);
        E oldVal = e.next();
        e.set(element);
        return oldVal;
    }

    public void add(int index, E element) {
        listIterator(index).add(element);
    }

    public E remove(int index) {
        ListIterator<E> e = listIterator(index);
        E outCast = e.next();
        e.remove();
        return outCast;
    }

    public Iterator<E> iterator() {
        return listIterator();
    }

    public abstract ListIterator<E> listIterator(int index);
}
