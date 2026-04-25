package example;

import static example.StaticImportTarget.value;

public class UsesStaticImport {
    public int read() {
        return value();
    }
}
