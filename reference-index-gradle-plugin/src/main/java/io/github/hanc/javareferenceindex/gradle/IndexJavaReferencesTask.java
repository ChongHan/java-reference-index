package io.github.hanc.javareferenceindex.gradle;

import io.github.hanc.javareferenceindex.api.JavaReferenceIndexers;
import io.github.hanc.javareferenceindex.model.JavaCompilerSettings;
import io.github.hanc.javareferenceindex.model.JavaLanguageLevel;
import io.github.hanc.javareferenceindex.model.ProjectCoordinates;
import io.github.hanc.javareferenceindex.model.ProjectIndexingRequest;
import io.github.hanc.javareferenceindex.model.SourceRoot;
import io.github.hanc.javareferenceindex.model.SourceSetCoordinates;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.compile.JavaCompile;

public abstract class IndexJavaReferencesTask extends DefaultTask {
    @TaskAction
    public void indexJavaReferences() {
        Project project = getProject();
        JavaPluginExtension java = project.getExtensions().findByType(JavaPluginExtension.class);
        if (java == null) {
            getLogger().lifecycle("java reference index: project={} has no Java plugin", project.getPath());
            return;
        }

        SourceSetContainer sourceSets = java.getSourceSets();
        sourceSets.forEach(sourceSet -> indexSourceSet(project, sourceSet));
    }

    private void indexSourceSet(Project project, SourceSet sourceSet) {
        List<Path> sourceFiles = sourceSet.getAllJava().getFiles().stream()
            .filter(File::isFile)
            .map(File::toPath)
            .sorted(Comparator.comparing(Path::toString))
            .toList();
        if (sourceFiles.isEmpty()) {
            return;
        }

        ProjectCoordinates projectCoordinates = new ProjectCoordinates(project.getPath());
        SourceSetCoordinates sourceSetCoordinates = new SourceSetCoordinates(sourceSet.getName());
        List<SourceRoot> sourceRoots = sourceRoots(project, sourceSet, projectCoordinates, sourceSetCoordinates);
        List<Path> classpathEntries = sourceSet.getCompileClasspath().getFiles().stream()
            .map(File::toPath)
            .sorted(Comparator.comparing(Path::toString))
            .toList();

        var request = new ProjectIndexingRequest(
            projectCoordinates,
            sourceSetCoordinates,
            sourceRoots,
            sourceFiles,
            classpathEntries,
            compilerSettings(project, sourceSet)
        );

        var index = JavaReferenceIndexers.jdt().index(request);
        index.files().forEach(file -> {
            String sourceType = typeName(project, file.sourceFile());
            file.sourceReferences().forEach(reference -> getLogger().lifecycle(
                "project={} sourceSet={} file={} sourceRef={} target={} targetProject={}",
                project.getPath(),
                sourceSet.getName(),
                sourceType,
                reference.qualifiedName(),
                projectRelativePath(project, reference.sourceFile()),
                owningProjectPath(project, reference.sourceFile())
            ));
            file.binaryReferences().forEach(reference -> getLogger().lifecycle(
                "project={} sourceSet={} file={} binaryRef={} classpath={}",
                project.getPath(),
                sourceSet.getName(),
                sourceType,
                reference.qualifiedName(),
                reference.classpathEntry().getFileName()
            ));
            file.unresolvedReferences().forEach(reference -> getLogger().lifecycle(
                "project={} sourceSet={} file={} unresolvedRef={}",
                project.getPath(),
                sourceSet.getName(),
                sourceType,
                reference.name()
            ));
        });
    }

    private static List<SourceRoot> sourceRoots(
        Project project,
        SourceSet sourceSet,
        ProjectCoordinates projectCoordinates,
        SourceSetCoordinates sourceSetCoordinates
    ) {
        List<SourceRoot> sourceRoots = new ArrayList<>();
        sourceSet.getAllJava().getSrcDirs().stream()
            .filter(File::isDirectory)
            .map(File::toPath)
            .sorted(Comparator.comparing(Path::toString))
            .map(path -> new SourceRoot(path, projectCoordinates, sourceSetCoordinates))
            .forEach(sourceRoots::add);

        project.getRootProject().getAllprojects().forEach(candidateProject -> {
            if (candidateProject.equals(project)) {
                return;
            }
            JavaPluginExtension java = candidateProject.getExtensions().findByType(JavaPluginExtension.class);
            if (java == null) {
                return;
            }
            ProjectCoordinates candidateProjectCoordinates = new ProjectCoordinates(candidateProject.getPath());
            java.getSourceSets().forEach(candidateSourceSet -> {
                SourceSetCoordinates candidateSourceSetCoordinates = new SourceSetCoordinates(candidateSourceSet.getName());
                candidateSourceSet.getAllJava().getSrcDirs().stream()
                    .filter(File::isDirectory)
                    .map(File::toPath)
                    .sorted(Comparator.comparing(Path::toString))
                    .map(path -> new SourceRoot(path, candidateProjectCoordinates, candidateSourceSetCoordinates))
                    .forEach(sourceRoots::add);
            });
        });

        return sourceRoots;
    }

    private static JavaCompilerSettings compilerSettings(Project project, SourceSet sourceSet) {
        JavaLanguageLevel languageLevel = JavaLanguageLevel.JAVA_21;
        Charset encoding = StandardCharsets.UTF_8;
        JavaCompile compileTask = javaCompileTask(project, sourceSet);
        if (compileTask != null && compileTask.getOptions().getEncoding() != null) {
            encoding = Charset.forName(compileTask.getOptions().getEncoding());
        }
        return new JavaCompilerSettings(languageLevel, languageLevel, languageLevel, encoding);
    }

    private static JavaCompile javaCompileTask(Project project, SourceSet sourceSet) {
        return project.getTasks()
            .withType(JavaCompile.class)
            .matching(task -> task.getName().equals(sourceSet.getCompileJavaTaskName()))
            .stream()
            .findFirst()
            .orElse(null);
    }

    private static String typeName(Project project, Path sourceFile) {
        return project.getRootDir().toPath()
            .relativize(sourceFile.toAbsolutePath().normalize())
            .toString()
            .replace(File.separatorChar, '.')
            .replaceFirst("^.*src\\.main\\.java\\.", "")
            .replaceFirst("^.*src\\.test\\.java\\.", "")
            .replaceFirst("\\.java$", "");
    }

    private static String projectRelativePath(Project project, Path path) {
        Path root = project.getRootDir().toPath().toAbsolutePath().normalize();
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.startsWith(root)) {
            return root.relativize(normalized).toString();
        }
        return normalized.toString();
    }

    private static String owningProjectPath(Project project, Path sourceFile) {
        Path normalized = sourceFile.toAbsolutePath().normalize();
        return project.getRootProject().getAllprojects().stream()
            .filter(candidate -> normalized.startsWith(candidate.getProjectDir().toPath().toAbsolutePath().normalize()))
            .max(Comparator.comparingInt(candidate -> candidate.getProjectDir().toPath().toAbsolutePath().normalize().getNameCount()))
            .map(Project::getPath)
            .orElse(project.getPath());
    }
}
