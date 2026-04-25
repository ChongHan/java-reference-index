package example;

import org.agrona.collections.IntArrayList;

public class UsesAgrona {
    private IntArrayList values;

    public int size() {
        return values.size();
    }
}
