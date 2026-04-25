package example;

import static example.StaticFieldImportTarget.VALUE;

public class UsesStaticFieldImport {
    public int read() {
        return VALUE;
    }
}
