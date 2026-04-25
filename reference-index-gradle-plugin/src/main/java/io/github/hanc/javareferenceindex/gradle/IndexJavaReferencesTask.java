package io.github.hanc.javareferenceindex.gradle;

import io.github.hanc.javareferenceindex.api.JavaReferenceIndexers;
import io.github.hanc.javareferenceindex.model.JavaCompilerSettings;
import io.github.hanc.javareferenceindex.model.ProjectCoordinates;
import io.github.hanc.javareferenceindex.model.ProjectIndexingRequest;
import io.github.hanc.javareferenceindex.model.SourceRoot;
import io.github.hanc.javareferenceindex.model.SourceSetCoordinates;
import java.io.Serializable;
import java.io.File;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Internal;
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

    @TaskAction
    public void indexJavaReferences() {
        sourceSets.forEach(this::indexSourceSet);
    }

    private void indexSourceSet(SourceSetSpec sourceSet) {
        List<Path> sourceFiles = sourceSet.sourceFiles().stream().map(Path::of).toList();
        if (sourceFiles.isEmpty()) {
            return;
        }

        ProjectCoordinates projectCoordinates = new ProjectCoordinates(sourceSet.projectPath());
        SourceSetCoordinates sourceSetCoordinates = new SourceSetCoordinates(sourceSet.sourceSetName());
        List<SourceRoot> sourceRoots = sourceSet.sourceRoots().stream()
            .map(sourceRoot -> new SourceRoot(
                Path.of(sourceRoot.path()),
                new ProjectCoordinates(sourceRoot.projectPath()),
                new SourceSetCoordinates(sourceRoot.sourceSetName())
            ))
            .toList();
        List<Path> classpathEntries = sourceSet.classpathEntries().stream().map(Path::of).toList();

        var request = new ProjectIndexingRequest(
            projectCoordinates,
            sourceSetCoordinates,
            sourceRoots,
            sourceFiles,
            classpathEntries,
            JavaCompilerSettings.java21()
        );

        var index = JavaReferenceIndexers.jdt().index(request);
        index.files().forEach(file -> {
            String sourceType = typeName(sourceSet.rootDir(), file.sourceFile());
            file.sourceReferences().forEach(reference -> getLogger().lifecycle(
                "project={} sourceSet={} file={} sourceRef={} target={} targetProject={}",
                sourceSet.projectPath(),
                sourceSet.sourceSetName(),
                sourceType,
                reference.qualifiedName(),
                rootRelativePath(sourceSet.rootDir(), reference.sourceFile()),
                owningProjectPath(sourceSet.projects(), reference.sourceFile())
            ));
            file.binaryReferences().forEach(reference -> getLogger().lifecycle(
                "project={} sourceSet={} file={} binaryRef={} classpath={}",
                sourceSet.projectPath(),
                sourceSet.sourceSetName(),
                sourceType,
                reference.qualifiedName(),
                reference.classpathEntry().getFileName()
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

    private static String owningProjectPath(List<ProjectSpec> projects, Path sourceFile) {
        Path normalized = sourceFile.toAbsolutePath().normalize();
        return projects.stream()
            .filter(candidate -> normalized.startsWith(Path.of(candidate.projectDir()).toAbsolutePath().normalize()))
            .max(Comparator.comparingInt(candidate -> Path.of(candidate.projectDir()).toAbsolutePath().normalize().getNameCount()))
            .map(ProjectSpec::path)
            .orElse(":");
    }

    public record SourceSetSpec(
        String projectPath,
        String sourceSetName,
        String rootDir,
        List<ProjectSpec> projects,
        List<SourceRootSpec> sourceRoots,
        List<String> sourceFiles,
        List<String> classpathEntries
    ) implements Serializable {
        public SourceSetSpec {
            projects = List.copyOf(projects);
            sourceRoots = List.copyOf(sourceRoots);
            sourceFiles = List.copyOf(sourceFiles);
            classpathEntries = List.copyOf(classpathEntries);
        }
    }

    public record ProjectSpec(String path, String projectDir) implements Serializable {}

    public record SourceRootSpec(String path, String projectPath, String sourceSetName) implements Serializable {}
}
