package io.github.chonghan.javareferenceindex.internal.jdt;

import io.github.chonghan.javareferenceindex.api.JavaReferenceIndexer;
import io.github.chonghan.javareferenceindex.internal.resolve.SourceAndClasspathTypeReferenceResolver;
import io.github.chonghan.javareferenceindex.model.ProjectIndex;
import io.github.chonghan.javareferenceindex.model.ProjectIndexingRequest;

public final class JdtJavaReferenceIndexer implements JavaReferenceIndexer {
    private final FileReferenceScanner fileReferenceScanner;

    public JdtJavaReferenceIndexer() {
        this(new JdtFileReferenceScanner(
            new JdtCompilationUnitParser(),
            new SourceAndClasspathTypeReferenceResolver()
        ));
    }

    JdtJavaReferenceIndexer(FileReferenceScanner fileReferenceScanner) {
        this.fileReferenceScanner = fileReferenceScanner;
    }

    @Override
    public ProjectIndex index(ProjectIndexingRequest request) {
        var files = fileReferenceScanner.scan(request);
        return new ProjectIndex(request.project(), request.sourceSet(), files);
    }
}
