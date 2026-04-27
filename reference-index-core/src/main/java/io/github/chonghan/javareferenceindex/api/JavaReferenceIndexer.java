package io.github.chonghan.javareferenceindex.api;

import io.github.chonghan.javareferenceindex.model.ProjectIndex;
import io.github.chonghan.javareferenceindex.model.ProjectIndexingRequest;

public interface JavaReferenceIndexer {
    ProjectIndex index(ProjectIndexingRequest request);
}
