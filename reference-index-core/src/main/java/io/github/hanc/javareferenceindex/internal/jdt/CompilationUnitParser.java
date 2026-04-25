package io.github.hanc.javareferenceindex.internal.jdt;

import io.github.hanc.javareferenceindex.model.ProjectIndexingRequest;
import java.nio.file.Path;
import org.eclipse.jdt.core.dom.CompilationUnit;

interface CompilationUnitParser {
    CompilationUnit parse(Path sourceFile, ProjectIndexingRequest request);
}
