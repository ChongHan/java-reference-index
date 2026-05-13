package io.github.chonghan.javareferenceindex.internal.jdt;

import io.github.chonghan.javareferenceindex.model.FileReferenceSet;
import io.github.chonghan.javareferenceindex.model.ProjectIndexingRequest;
import java.util.List;

interface FileReferenceScanner {
    List<FileReferenceSet> scan(ProjectIndexingRequest request);
}
