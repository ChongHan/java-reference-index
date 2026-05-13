package io.github.chonghan.javareferenceindex.internal.jdt;

import io.github.chonghan.javareferenceindex.model.ClasspathEntry;
import io.github.chonghan.javareferenceindex.model.ProjectIndexingRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FileASTRequestor;

final class JdtCompilationUnitParser implements CompilationUnitParser {
    @Override
    public Map<Path, CompilationUnit> parse(ProjectIndexingRequest request) {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setStatementsRecovery(true);
        parser.setEnvironment(
            toStrings(request.classpathEntries()),
            sourceRootPaths(request),
            sourceRootEncodings(request),
            true
        );

        Map<String, String> options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(request.compilerSettings().effectiveSourceLevel().compilerLevel(), options);
        parser.setCompilerOptions(options);

        Map<Path, CompilationUnit> compilationUnits = new LinkedHashMap<>();
        parser.createASTs(
            sourceFilePaths(request),
            sourceFileEncodings(request),
            new String[0],
            new FileASTRequestor() {
                @Override
                public void acceptAST(String sourceFilePath, CompilationUnit ast) {
                    compilationUnits.put(Path.of(sourceFilePath).toAbsolutePath().normalize(), ast);
                }
            },
            null
        );
        return compilationUnits;
    }

    private static String[] sourceRootPaths(ProjectIndexingRequest request) {
        return request.sourceRoots().stream()
            .map(sourceRoot -> sourceRoot.path().toAbsolutePath().normalize().toString())
            .toArray(String[]::new);
    }

    private static String[] sourceRootEncodings(ProjectIndexingRequest request) {
        return request.sourceRoots().stream()
            .map(_sourceRoot -> request.compilerSettings().encoding().name())
            .toArray(String[]::new);
    }

    private static String[] sourceFilePaths(ProjectIndexingRequest request) {
        return request.sourceFiles().stream()
            .map(path -> path.toAbsolutePath().normalize().toString())
            .toArray(String[]::new);
    }

    private static String[] sourceFileEncodings(ProjectIndexingRequest request) {
        return request.sourceFiles().stream()
            .map(_sourceFile -> request.compilerSettings().encoding().name())
            .toArray(String[]::new);
    }

    private static String[] toStrings(List<ClasspathEntry> entries) {
        return entries.stream()
            .filter(JdtCompilationUnitParser::isSupportedClasspathEntry)
            .map(ClasspathEntry::path)
            .map(Path::toAbsolutePath)
            .map(Path::normalize)
            .map(Path::toString)
            .toArray(String[]::new);
    }

    private static boolean isSupportedClasspathEntry(ClasspathEntry entry) {
        Path path = entry.path().toAbsolutePath().normalize();
        if (Files.isDirectory(path)) {
            return true;
        }
        if (!Files.isRegularFile(path)) {
            return false;
        }
        String fileName = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return fileName.endsWith(".jar") || fileName.endsWith(".zip");
    }
}
