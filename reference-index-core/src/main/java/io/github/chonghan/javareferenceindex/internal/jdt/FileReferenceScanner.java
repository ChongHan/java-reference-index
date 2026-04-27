package io.github.chonghan.javareferenceindex.internal.jdt;

import io.github.chonghan.javareferenceindex.model.FileReferenceSet;
import io.github.chonghan.javareferenceindex.model.ProjectIndexingRequest;
import java.nio.file.Path;

interface FileReferenceScanner {
    FileReferenceSet scan(Path sourceFile, ProjectIndexingRequest request);
}
