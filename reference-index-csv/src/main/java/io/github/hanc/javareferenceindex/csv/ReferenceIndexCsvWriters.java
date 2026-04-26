package io.github.hanc.javareferenceindex.csv;

public final class ReferenceIndexCsvWriters {
    private ReferenceIndexCsvWriters() {
    }

    public static ReferenceIndexCsvWriter standard() {
        return new StandardReferenceIndexCsvWriter();
    }
}
