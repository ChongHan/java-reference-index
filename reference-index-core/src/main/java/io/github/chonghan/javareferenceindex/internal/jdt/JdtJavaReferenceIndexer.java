package io.github.chonghan.javareferenceindex.internal.jdt;

import io.github.chonghan.javareferenceindex.api.JavaReferenceIndexer;
import io.github.chonghan.javareferenceindex.internal.resolve.SourceAndClasspathTypeReferenceResolver;
import io.github.chonghan.javareferenceindex.model.FileReferenceSet;
import io.github.chonghan.javareferenceindex.model.ProjectIndex;
import io.github.chonghan.javareferenceindex.model.ProjectIndexingRequest;
import java.util.List;

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
        List<FileReferenceSet> files = request.sourceFiles().parallelStream()
            .map(sourceFile -> fileReferenceScanner.scan(sourceFile, request))
            .toList();
        return new ProjectIndex(request.project(), request.sourceSet(), files);
    }
}
