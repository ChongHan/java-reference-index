package example;

import static example.SharedMainType.VALUE;
import static example.SharedMainType.value;

public final class UsesSharedMainType {
    int combined() {
        return VALUE + value();
    }
}
