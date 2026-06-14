package io.github.chonghan.javareferenceindex.gradle;

import io.github.chonghan.javareferenceindex.api.JavaReferenceIndexers;
import io.github.chonghan.javareferenceindex.csv.CsvReferenceIndexWriteRequest;
import io.github.chonghan.javareferenceindex.csv.ReferenceIndexCsvWriters;
import io.github.chonghan.javareferenceindex.model.ClasspathEntry;
import io.github.chonghan.javareferenceindex.model.JavaCompilerSettings;
import io.github.chonghan.javareferenceindex.model.ProjectCoordinates;
import io.github.chonghan.javareferenceindex.model.ProjectIndex;
import io.github.chonghan.javareferenceindex.model.ProjectIndexingRequest;
import io.github.chonghan.javareferenceindex.model.SourceRoot;
import io.github.chonghan.javareferenceindex.model.SourceSetCoordinates;
import java.io.Serializable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Gradle task that indexes Java source references for each configured source set.
 */
@CacheableTask
public abstract class IndexJavaReferencesTask extends DefaultTask {
    private String projectPath;
    private List<SourceSetSpec> sourceSets = List.of();

    /**
     * Creates the Java reference indexing task.
     */
    public IndexJavaReferencesTask() {
    }

    /**
     * Returns the Gradle project path that owns this task.
     *
     * @return the owning Gradle project path
     */
    @Input
    public String getProjectPath() {
        return projectPath;
    }

    /**
     * Sets the Gradle project path that owns this task.
     *
     * @param projectPath the owning Gradle project path
     */
    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath;
    }

    /**
     * Returns the source sets that will be indexed.
     *
     * @return source set specifications for this task
     */
    @Internal
    public List<SourceSetSpec> getSourceSets() {
        return sourceSets;
    }

    /**
     * Sets the source sets that will be indexed.
     *
     * @param sourceSets source set specifications for this task
     */
    public void setSourceSets(List<SourceSetSpec> sourceSets) {
        this.sourceSets = List.copyOf(sourceSets);
    }

    /**
     * Returns stable source set configuration inputs for Gradle cache keys.
     *
     * @return normalized source set configuration values
     */
    @Input
    public List<String> getSourceSetConfiguration() {
        return sourceSets.stream()
            .flatMap(sourceSet -> sourceSet.configurationInputs().stream())
            .toList();
    }

    /**
     * Returns classpath target identifiers used as task inputs.
     *
     * @return sorted target identifiers for classpath entries
     */
    @Input
    public List<String> getClasspathTargets() {
        return sourceSets.stream()
            .flatMap(sourceSet -> sourceSet.classpathEntries().stream())
            .map(ClasspathEntrySpec::target)
            .sorted()
            .toList();
    }

    /**
     * Returns Java source files and source directories used by the task.
     *
     * @return the source input file collection
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSourceInputFiles();

    /**
     * Returns the compile classpath files used while resolving references.
     *
     * @return classpath files for all configured source sets
     */
    @Classpath
    public List<File> getClasspathInputFiles() {
        return sourceSets.stream()
            .flatMap(sourceSet -> sourceSet.classpathEntries().stream())
            .map(ClasspathEntrySpec::path)
            .map(File::new)
            .toList();
    }

    /**
     * Returns the directory where reference CSV files are written.
     *
     * @return the output directory property
     */
    @Internal
    public abstract DirectoryProperty getOutputDirectory();

    /**
     * Returns the expected CSV output files for the configured source sets.
     *
     * @return output CSV files for this task
     */
    @OutputFiles
    public List<File> getOutputFiles() {
        return sourceSets.stream()
            .map(sourceSet -> outputFile(sourceSet.sourceSetName()).toFile())
            .toList();
    }

    /**
     * Builds Java reference indexes and writes them as CSV files.
     */
    @TaskAction
    public void javaReferenceIndex() {
        sourceSets.forEach(this::indexSourceSet);
    }

    private void indexSourceSet(SourceSetSpec sourceSet) {
        long totalStart = System.nanoTime();
        long prepareStart = totalStart;

        List<Path> sourceFiles = sourceFiles(sourceSet);
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
            .filter(sourceRoot -> Files.isDirectory(sourceRoot.path()))
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
        long prepareNanos = elapsedSince(prepareStart);

        long indexStart = System.nanoTime();
        var index = JavaReferenceIndexers.jdt().index(request);
        long indexNanos = elapsedSince(indexStart);

        long csvStart = System.nanoTime();
        writeCsv(sourceSet, index);
        long csvNanos = elapsedSince(csvStart);

        if (getLogger().isInfoEnabled()) {
            logProfile(
                sourceSet,
                index,
                sourceRoots.size(),
                classpathEntries.size(),
                prepareNanos,
                indexNanos,
                csvNanos,
                elapsedSince(totalStart)
            );
        }
    }

    private void writeCsv(SourceSetSpec sourceSet, ProjectIndex index) {
        Path outputFile = outputFile(sourceSet.sourceSetName());

        try {
            ReferenceIndexCsvWriters.standard().write(
                index,
                new CsvReferenceIndexWriteRequest(outputFile, Path.of(sourceSet.rootDir()))
            );
        } catch (IOException e) {
            throw new GradleException("Failed to write Java reference index CSV", e);
        }
    }

    private Path outputFile(String sourceSetName) {
        return getOutputDirectory().get().getAsFile().toPath()
            .resolve(sourceSetName + "-references.csv");
    }

    private void logProfile(
        SourceSetSpec sourceSet,
        ProjectIndex index,
        int sourceRootCount,
        int classpathEntryCount,
        long prepareNanos,
        long indexNanos,
        long csvNanos,
        long totalNanos
    ) {
        ReferenceCounts counts = referenceCounts(index);
        getLogger().info(
            "[java-reference-index] %s %s files=%d sourceRoots=%d classpathEntries=%d prepare=%s index=%s csv=%s total=%s sourceRefs=%d binaryRefs=%d unresolvedRefs=%d".formatted(
                sourceSet.projectPath(),
                sourceSet.sourceSetName(),
                sourceFiles(sourceSet).size(),
                sourceRootCount,
                classpathEntryCount,
                duration(prepareNanos),
                duration(indexNanos),
                duration(csvNanos),
                duration(totalNanos),
                counts.sourceReferences(),
                counts.binaryReferences(),
                counts.unresolvedReferences()
            )
        );
    }

    private static List<Path> sourceFiles(SourceSetSpec sourceSet) {
        return sourceSet.sourceRoots().stream()
            .filter(sourceRoot -> sourceRoot.projectPath().equals(sourceSet.projectPath()))
            .filter(sourceRoot -> sourceRoot.sourceSetName().equals(sourceSet.sourceSetName()))
            .map(sourceRoot -> Path.of(sourceRoot.path()))
            .filter(Files::isDirectory)
            .flatMap(sourceRoot -> javaFiles(sourceRoot).stream())
            .distinct()
            .sorted()
            .toList();
    }

    private static List<Path> javaFiles(Path sourceRoot) {
        try (var paths = Files.walk(sourceRoot)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".java"))
                .toList();
        } catch (IOException e) {
            throw new GradleException("Failed to list Java source files under " + sourceRoot, e);
        }
    }

    private static ReferenceCounts referenceCounts(ProjectIndex index) {
        int sourceReferences = 0;
        int binaryReferences = 0;
        int unresolvedReferences = 0;
        for (var file : index.files()) {
            sourceReferences += file.sourceReferences().size();
            binaryReferences += file.binaryReferences().size();
            unresolvedReferences += file.unresolvedReferences().size();
        }
        return new ReferenceCounts(sourceReferences, binaryReferences, unresolvedReferences);
    }

    private static long elapsedSince(long startNanos) {
        return System.nanoTime() - startNanos;
    }

    private static String duration(long nanos) {
        double millis = nanos / 1_000_000.0d;
        if (millis < 1_000.0d) {
            return String.format(Locale.ROOT, "%.1fms", millis);
        }
        return String.format(Locale.ROOT, "%.3fs", millis / 1_000.0d);
    }

    private record ReferenceCounts(int sourceReferences, int binaryReferences, int unresolvedReferences) {}

    /**
     * Serializable description of one Java source set to index.
     *
     * @param projectPath owning Gradle project path
     * @param sourceSetName Gradle source set name
     * @param rootDir root directory used to normalize paths
     * @param sourceRoots source roots visible while indexing this source set
     * @param classpathEntries compile classpath entries visible while indexing this source set
     */
    public record SourceSetSpec(
        String projectPath,
        String sourceSetName,
        String rootDir,
        List<SourceRootSpec> sourceRoots,
        List<ClasspathEntrySpec> classpathEntries
    ) implements Serializable {
        /**
         * Creates a source set specification with immutable nested lists.
         *
         * @param projectPath owning Gradle project path
         * @param sourceSetName Gradle source set name
         * @param rootDir root directory used to normalize paths
         * @param sourceRoots source roots visible while indexing this source set
         * @param classpathEntries compile classpath entries visible while indexing this source set
         */
        public SourceSetSpec {
            sourceRoots = List.copyOf(sourceRoots);
            classpathEntries = List.copyOf(classpathEntries);
        }

        private List<String> configurationInputs() {
            Path rootDirPath = Path.of(rootDir);
            return java.util.stream.Stream.of(
                    java.util.stream.Stream.of(
                        "projectPath=" + projectPath,
                        "sourceSetName=" + sourceSetName
                    ),
                    sourceRoots.stream().map(sourceRoot -> "sourceRoot=" + sourceRoot.cacheKey(rootDirPath))
                )
                .flatMap(stream -> stream)
                .sorted()
                .toList();
        }
    }

    /**
     * Serializable description of a Java source root.
     *
     * @param path absolute source root path
     * @param projectPath Gradle project path that owns the source root
     * @param sourceSetName Gradle source set name that owns the source root
     */
    public record SourceRootSpec(String path, String projectPath, String sourceSetName) implements Serializable {
        private String cacheKey(Path rootDir) {
            return relativePath(rootDir, Path.of(path)) + "|" + projectPath + "|" + sourceSetName;
        }
    }

    /**
     * Serializable description of a compile classpath entry.
     *
     * @param path absolute classpath entry path
     * @param target display target for references resolved to this classpath entry
     */
    public record ClasspathEntrySpec(String path, String target) implements Serializable {}

    private static String relativePath(Path rootDir, Path path) {
        Path normalizedRootDir = rootDir.toAbsolutePath().normalize();
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (!normalizedPath.startsWith(normalizedRootDir)) {
            return normalizedPath.getFileName().toString();
        }
        return normalizedRootDir.relativize(normalizedPath).toString().replace('\\', '/');
    }
}
