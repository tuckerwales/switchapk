package java.text;

public interface CharacterIterator extends Cloneable {
    char DONE = '￿';

    char first();

    char last();

    char current();

    char next();

    char previous();

    char setIndex(int position);

    int getBeginIndex();

    int getEndIndex();

    int getIndex();

    Object clone();
}
