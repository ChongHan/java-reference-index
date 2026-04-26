package io.github.hanc.javareferenceindex.internal.jdt;

import io.github.hanc.javareferenceindex.model.ClasspathEntry;
import io.github.hanc.javareferenceindex.model.ProjectIndexingRequest;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

final class JdtCompilationUnitParser implements CompilationUnitParser {
    @Override
    public CompilationUnit parse(Path sourceFile, ProjectIndexingRequest request) {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setStatementsRecovery(true);
        parser.setUnitName(sourceFile.getFileName().toString());
        parser.setSource(readSource(sourceFile, request.compilerSettings().encoding()).toCharArray());
        parser.setEnvironment(
            toStrings(request.classpathEntries()),
            sourceRootPaths(request),
            null,
            true
        );

        Map<String, String> options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(request.compilerSettings().effectiveSourceLevel().compilerLevel(), options);
        parser.setCompilerOptions(options);

        return (CompilationUnit) parser.createAST(null);
    }

    private static String readSource(Path sourceFile, Charset encoding) {
        try {
            return Files.readString(sourceFile, encoding);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read source file " + sourceFile, e);
        }
    }

    private static String[] sourceRootPaths(ProjectIndexingRequest request) {
        return request.sourceRoots().stream()
            .map(sourceRoot -> sourceRoot.path().toAbsolutePath().normalize().toString())
            .toArray(String[]::new);
    }

    private static String[] toStrings(List<ClasspathEntry> entries) {
        return entries.stream()
            .map(ClasspathEntry::path)
            .map(Path::toAbsolutePath)
            .map(Path::normalize)
            .map(Path::toString)
            .toArray(String[]::new);
    }
}
