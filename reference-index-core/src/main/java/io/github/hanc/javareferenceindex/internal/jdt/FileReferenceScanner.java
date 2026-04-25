package io.github.hanc.javareferenceindex.internal.jdt;

import io.github.hanc.javareferenceindex.model.FileReferenceSet;
import io.github.hanc.javareferenceindex.model.ProjectIndexingRequest;
import java.nio.file.Path;

interface FileReferenceScanner {
    FileReferenceSet scan(Path sourceFile, ProjectIndexingRequest request);
}
