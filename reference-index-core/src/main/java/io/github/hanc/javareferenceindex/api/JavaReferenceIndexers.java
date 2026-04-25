package io.github.hanc.javareferenceindex.api;

import io.github.hanc.javareferenceindex.internal.jdt.JdtJavaReferenceIndexer;

public final class JavaReferenceIndexers {
    private JavaReferenceIndexers() {
    }

    public static JavaReferenceIndexer jdt() {
        return new JdtJavaReferenceIndexer();
    }
}
