package io.github.chonghan.javareferenceindex.csv;

import io.github.chonghan.javareferenceindex.model.ProjectIndex;
import java.io.IOException;

public interface ReferenceIndexCsvWriter {
    void write(ProjectIndex index, CsvReferenceIndexWriteRequest request) throws IOException;
}
