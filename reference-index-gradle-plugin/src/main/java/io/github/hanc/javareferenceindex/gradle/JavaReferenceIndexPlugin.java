package io.github.hanc.javareferenceindex.gradle;

import java.io.File;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;

public class JavaReferenceIndexPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        var taskProvider = project.getTasks().register("indexJavaReferences", IndexJavaReferencesTask.class);

        project.getPlugins().withType(JavaPlugin.class, javaPlugin ->
            project.getGradle().projectsEvaluated(gradle ->
                taskProvider.configure(task -> task.setSourceSets(sourceSetSpecs(project)))
            )
        );
    }

    private static List<IndexJavaReferencesTask.SourceSetSpec> sourceSetSpecs(Project project) {
        JavaPluginExtension java = project.getExtensions().findByType(JavaPluginExtension.class);
        if (java == null) {
            return List.of();
        }
        return java.getSourceSets().stream()
            .map(sourceSet -> sourceSetSpec(project, sourceSet))
            .toList();
    }

    private static IndexJavaReferencesTask.SourceSetSpec sourceSetSpec(Project project, SourceSet sourceSet) {
        return new IndexJavaReferencesTask.SourceSetSpec(
            project.getPath(),
            sourceSet.getName(),
            project.getRootDir().toPath().toAbsolutePath().normalize().toString(),
            projects(project),
            sourceRoots(project, sourceSet),
            sourceFiles(sourceSet),
            classpathEntries(sourceSet)
        );
    }

    private static List<IndexJavaReferencesTask.ProjectSpec> projects(Project project) {
        return project.getRootProject().getAllprojects().stream()
            .map(candidate -> new IndexJavaReferencesTask.ProjectSpec(
                candidate.getPath(),
                candidate.getProjectDir().toPath().toAbsolutePath().normalize().toString()
            ))
            .toList();
    }

    private static List<IndexJavaReferencesTask.SourceRootSpec> sourceRoots(Project project, SourceSet sourceSet) {
        var currentSourceRoots = sourceSet.getAllJava().getSrcDirs().stream()
            .filter(File::isDirectory)
            .map(sourceRoot -> new IndexJavaReferencesTask.SourceRootSpec(
                sourceRoot.toPath().toAbsolutePath().normalize().toString(),
                project.getPath(),
                sourceSet.getName()
            ));

        var dependencySourceRoots = project.getRootProject().getAllprojects().stream()
            .filter(candidateProject -> !candidateProject.equals(project))
            .flatMap(candidateProject -> {
                JavaPluginExtension java = candidateProject.getExtensions().findByType(JavaPluginExtension.class);
                if (java == null) {
                    return Stream.<IndexJavaReferencesTask.SourceRootSpec>empty();
                }
                return java.getSourceSets().stream()
                    .flatMap(candidateSourceSet -> candidateSourceSet.getAllJava().getSrcDirs().stream()
                        .filter(File::isDirectory)
                        .map(sourceRoot -> new IndexJavaReferencesTask.SourceRootSpec(
                            sourceRoot.toPath().toAbsolutePath().normalize().toString(),
                            candidateProject.getPath(),
                            candidateSourceSet.getName()
                        )));
            });

        return Stream.concat(currentSourceRoots, dependencySourceRoots)
            .sorted(Comparator.comparing(IndexJavaReferencesTask.SourceRootSpec::path))
            .toList();
    }

    private static List<String> sourceFiles(SourceSet sourceSet) {
        return sourceSet.getAllJava().getFiles().stream()
            .filter(File::isFile)
            .map(File::toPath)
            .map(Path::toAbsolutePath)
            .map(Path::normalize)
            .map(Path::toString)
            .sorted()
            .toList();
    }

    private static List<String> classpathEntries(SourceSet sourceSet) {
        return sourceSet.getCompileClasspath().getFiles().stream()
            .map(File::toPath)
            .map(Path::toAbsolutePath)
            .map(Path::normalize)
            .map(Path::toString)
            .sorted()
            .toList();
    }
}
