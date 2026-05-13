package io.github.chonghan.javareferenceindex.internal.jdt;

import io.github.chonghan.javareferenceindex.model.ProjectIndexingRequest;
import java.nio.file.Path;
import java.util.Map;
import org.eclipse.jdt.core.dom.CompilationUnit;

interface CompilationUnitParser {
    Map<Path, CompilationUnit> parse(ProjectIndexingRequest request);
}
