package io.github.hanc.javareferenceindex.gradle;

import java.io.File;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;

public class JavaReferenceIndexPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        var taskProvider = project.getTasks().register("indexJavaReferences", IndexJavaReferencesTask.class, task ->
            task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("reference-index"))
        );

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
            sourceRoots(project, sourceSet),
            sourceFiles(sourceSet),
            classpathEntries(project, sourceSet)
        );
    }

    private static List<IndexJavaReferencesTask.SourceRootSpec> sourceRoots(Project project, SourceSet sourceSet) {
        var currentSourceRoots = sourceSet.getAllJava().getSrcDirs().stream()
            .filter(File::isDirectory)
            .map(sourceRoot -> new IndexJavaReferencesTask.SourceRootSpec(
                sourceRoot.toPath().toAbsolutePath().normalize().toString(),
                project.getPath(),
                sourceSet.getName()
            ));

        var dependencySourceRoots = projectDependencyPaths(project, sourceSet).stream()
            .map(project.getRootProject()::findProject)
            .flatMap(candidateProject -> {
                if (candidateProject == null) {
                    return Stream.<IndexJavaReferencesTask.SourceRootSpec>empty();
                }
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

    private static List<String> projectDependencyPaths(Project project, SourceSet sourceSet) {
        var configuration = project.getConfigurations().getByName(sourceSet.getCompileClasspathConfigurationName());
        return configuration.getIncoming().getResolutionResult().getAllComponents().stream()
            .map(component -> component.getId())
            .filter(ProjectComponentIdentifier.class::isInstance)
            .map(ProjectComponentIdentifier.class::cast)
            .map(ProjectComponentIdentifier::getProjectPath)
            .filter(projectPath -> !projectPath.equals(project.getPath()))
            .distinct()
            .sorted()
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

    private static List<IndexJavaReferencesTask.ClasspathEntrySpec> classpathEntries(Project project, SourceSet sourceSet) {
        Map<String, String> artifactTargets = artifactTargets(project, sourceSet);
        return sourceSet.getCompileClasspath().getFiles().stream()
            .map(File::toPath)
            .map(Path::toAbsolutePath)
            .map(Path::normalize)
            .map(path -> new IndexJavaReferencesTask.ClasspathEntrySpec(
                path.toString(),
                artifactTargets.getOrDefault(path.toString(), path.getFileName().toString())
            ))
            .sorted(Comparator.comparing(IndexJavaReferencesTask.ClasspathEntrySpec::path))
            .toList();
    }

    private static Map<String, String> artifactTargets(Project project, SourceSet sourceSet) {
        var configuration = project.getConfigurations().getByName(sourceSet.getCompileClasspathConfigurationName());
        return configuration.getIncoming().getArtifacts().getArtifacts().stream()
            .collect(Collectors.toMap(
                artifact -> artifact.getFile().toPath().toAbsolutePath().normalize().toString(),
                JavaReferenceIndexPlugin::targetFor,
                (first, second) -> first
            ));
    }

    private static String targetFor(ResolvedArtifactResult artifact) {
        ComponentIdentifier componentIdentifier = artifact.getId().getComponentIdentifier();
        if (componentIdentifier instanceof ModuleComponentIdentifier module) {
            return module.getGroup() + ":" + module.getModule() + ":" + module.getVersion();
        }
        return componentIdentifier.getDisplayName();
    }
}
