package io.github.chonghan.javareferenceindex.api;

import io.github.chonghan.javareferenceindex.internal.jdt.JdtJavaReferenceIndexer;

public final class JavaReferenceIndexers {
    private JavaReferenceIndexers() {
    }

    public static JavaReferenceIndexer jdt() {
        return new JdtJavaReferenceIndexer();
    }
}
