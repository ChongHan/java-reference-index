package io.github.hanc.javareferenceindex.csv;

import java.nio.file.Path;

public record CsvReferenceIndexWriteRequest(
    Path outputFile,
    Path rootDirectory
) {}
