package io.github.hanc.javareferenceindex.api;

import io.github.hanc.javareferenceindex.model.ProjectIndex;
import io.github.hanc.javareferenceindex.model.ProjectIndexingRequest;

public interface JavaReferenceIndexer {
    ProjectIndex index(ProjectIndexingRequest request);
}
