package io.github.hanc.javareferenceindex.gradle;

import io.github.hanc.javareferenceindex.api.JavaReferenceIndexers;
import io.github.hanc.javareferenceindex.csv.CsvReferenceIndexWriteRequest;
import io.github.hanc.javareferenceindex.csv.ReferenceIndexCsvWriters;
import io.github.hanc.javareferenceindex.model.ClasspathEntry;
import io.github.hanc.javareferenceindex.model.JavaCompilerSettings;
import io.github.hanc.javareferenceindex.model.ProjectCoordinates;
import io.github.hanc.javareferenceindex.model.ProjectIndex;
import io.github.hanc.javareferenceindex.model.ProjectIndexingRequest;
import io.github.hanc.javareferenceindex.model.SourceRoot;
import io.github.hanc.javareferenceindex.model.SourceSetCoordinates;
import java.io.Serializable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

public abstract class IndexJavaReferencesTask extends DefaultTask {
    private List<SourceSetSpec> sourceSets = List.of();

    @Internal
    public List<SourceSetSpec> getSourceSets() {
        return sourceSets;
    }

    public void setSourceSets(List<SourceSetSpec> sourceSets) {
        this.sourceSets = List.copyOf(sourceSets);
    }

    @Input
    public List<String> getSourceSetConfiguration() {
        return sourceSets.stream()
            .flatMap(sourceSet -> sourceSet.configurationInputs().stream())
            .toList();
    }

    @Input
    public List<String> getClasspathTargets() {
        return sourceSets.stream()
            .flatMap(sourceSet -> sourceSet.classpathEntries().stream())
            .map(ClasspathEntrySpec::target)
            .sorted()
            .toList();
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public List<File> getSourceInputFiles() {
        return sourceSets.stream()
            .flatMap(sourceSet -> sourceSet.sourceFiles().stream())
            .map(File::new)
            .toList();
    }

    @Classpath
    public List<File> getClasspathInputFiles() {
        return sourceSets.stream()
            .flatMap(sourceSet -> sourceSet.classpathEntries().stream())
            .map(ClasspathEntrySpec::path)
            .map(File::new)
            .toList();
    }

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void indexJavaReferences() {
        sourceSets.forEach(this::indexSourceSet);
    }

    private void indexSourceSet(SourceSetSpec sourceSet) {
        List<Path> sourceFiles = sourceSet.sourceFiles().stream().map(Path::of).toList();

        ProjectCoordinates projectCoordinates = new ProjectCoordinates(sourceSet.projectPath());
        SourceSetCoordinates sourceSetCoordinates = new SourceSetCoordinates(sourceSet.sourceSetName());
        List<SourceRoot> sourceRoots = sourceSet.sourceRoots().stream()
            .map(sourceRoot -> new SourceRoot(
                Path.of(sourceRoot.path()),
                new ProjectCoordinates(sourceRoot.projectPath()),
                new SourceSetCoordinates(sourceRoot.sourceSetName())
            ))
            .toList();
        List<ClasspathEntry> classpathEntries = sourceSet.classpathEntries().stream()
            .map(classpathEntry -> ClasspathEntry.of(Path.of(classpathEntry.path()), classpathEntry.target()))
            .toList();

        var request = new ProjectIndexingRequest(
            projectCoordinates,
            sourceSetCoordinates,
            sourceRoots,
            sourceFiles,
            classpathEntries,
            JavaCompilerSettings.java21()
        );

        var index = JavaReferenceIndexers.jdt().index(request);
        logIndex(sourceSet, index);
        writeCsv(sourceSet, index);
    }

    private void writeCsv(SourceSetSpec sourceSet, ProjectIndex index) {
        Path outputFile = getOutputDirectory().get().getAsFile().toPath()
            .resolve(sourceSet.sourceSetName() + "-references.csv");

        try {
            ReferenceIndexCsvWriters.standard().write(
                index,
                new CsvReferenceIndexWriteRequest(outputFile, Path.of(sourceSet.rootDir()))
            );
        } catch (IOException e) {
            throw new GradleException("Failed to write Java reference index CSV", e);
        }
    }

    private void logIndex(SourceSetSpec sourceSet, ProjectIndex index) {
        index.files().forEach(file -> {
            String sourceType = typeName(sourceSet.rootDir(), file.sourceFile());
            file.sourceReferences().forEach(reference -> getLogger().lifecycle(
                "project={} sourceSet={} file={} sourceRef={} target={} targetProject={}",
                sourceSet.projectPath(),
                sourceSet.sourceSetName(),
                sourceType,
                reference.qualifiedName(),
                rootRelativePath(sourceSet.rootDir(), reference.sourceFile()),
                reference.targetProject().path()
            ));
            file.binaryReferences().forEach(reference -> getLogger().lifecycle(
                "project={} sourceSet={} file={} binaryRef={} target={}",
                sourceSet.projectPath(),
                sourceSet.sourceSetName(),
                sourceType,
                reference.qualifiedName(),
                reference.target()
            ));
            file.unresolvedReferences().forEach(reference -> getLogger().lifecycle(
                "project={} sourceSet={} file={} unresolvedRef={}",
                sourceSet.projectPath(),
                sourceSet.sourceSetName(),
                sourceType,
                reference.name()
            ));
        });
    }

    private static String typeName(String rootDir, Path sourceFile) {
        return Path.of(rootDir)
            .relativize(sourceFile.toAbsolutePath().normalize())
            .toString()
            .replace(File.separatorChar, '.')
            .replaceFirst("^.*src\\.main\\.java\\.", "")
            .replaceFirst("^.*src\\.test\\.java\\.", "")
            .replaceFirst("\\.java$", "");
    }

    private static String rootRelativePath(String rootDir, Path path) {
        Path root = Path.of(rootDir).toAbsolutePath().normalize();
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.startsWith(root)) {
            return root.relativize(normalized).toString();
        }
        return normalized.toString();
    }

    public record SourceSetSpec(
        String projectPath,
        String sourceSetName,
        String rootDir,
        List<SourceRootSpec> sourceRoots,
        List<String> sourceFiles,
        List<ClasspathEntrySpec> classpathEntries
    ) implements Serializable {
        public SourceSetSpec {
            sourceRoots = List.copyOf(sourceRoots);
            sourceFiles = List.copyOf(sourceFiles);
            classpathEntries = List.copyOf(classpathEntries);
        }

        private List<String> configurationInputs() {
            return java.util.stream.Stream.of(
                    java.util.stream.Stream.of(
                        "projectPath=" + projectPath,
                        "sourceSetName=" + sourceSetName,
                        "rootDir=" + rootDir
                    ),
                    sourceRoots.stream().map(sourceRoot -> "sourceRoot=" + sourceRoot),
                    sourceFiles.stream().map(sourceFile -> "sourceFile=" + sourceFile),
                    classpathEntries.stream().map(classpathEntry -> "classpathEntry=" + classpathEntry)
                )
                .flatMap(stream -> stream)
                .sorted()
                .toList();
        }
    }

    public record SourceRootSpec(String path, String projectPath, String sourceSetName) implements Serializable {}

    public record ClasspathEntrySpec(String path, String target) implements Serializable {}
}
