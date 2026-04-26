package io.github.hanc.javareferenceindex.csv;

import io.github.hanc.javareferenceindex.model.ProjectIndex;
import java.io.IOException;

public interface ReferenceIndexCsvWriter {
    void write(ProjectIndex index, CsvReferenceIndexWriteRequest request) throws IOException;
}
