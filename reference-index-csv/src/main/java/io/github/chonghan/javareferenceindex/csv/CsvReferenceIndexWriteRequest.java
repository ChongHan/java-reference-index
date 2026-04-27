package io.github.chonghan.javareferenceindex.csv;

import java.nio.file.Path;

public record CsvReferenceIndexWriteRequest(
    Path outputFile,
    Path rootDirectory
) {}
