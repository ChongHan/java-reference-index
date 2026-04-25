package io.github.hanc.javareferenceindex.internal.jdt;

import io.github.hanc.javareferenceindex.api.JavaReferenceIndexer;
import io.github.hanc.javareferenceindex.internal.resolve.SourceAndClasspathTypeReferenceResolver;
import io.github.hanc.javareferenceindex.model.FileReferenceSet;
import io.github.hanc.javareferenceindex.model.ProjectIndex;
import io.github.hanc.javareferenceindex.model.ProjectIndexingRequest;
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
        List<FileReferenceSet> files = request.sourceFiles().stream()
            .map(sourceFile -> fileReferenceScanner.scan(sourceFile, request))
            .toList();
        return new ProjectIndex(request.project(), request.sourceSet(), files);
    }
}
