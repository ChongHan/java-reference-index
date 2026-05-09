package io.github.chonghan.javareferenceindex.gradle;

import java.io.File;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
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
import org.gradle.api.tasks.TaskProvider;

public class JavaReferenceIndexPlugin implements Plugin<Project> {
    static final String INDEX_TASK_NAME = "javaReferenceIndex";
    static final String QUERY_TASK_NAME = "javaReferenceQuery";

    @Override
    public void apply(Project project) {
        var taskProvider = indexTask(project);
        var queryTaskProvider = queryTask(project);
        queryTaskProvider.configure(task -> {
            task.dependsOn(taskProvider);
            addReferenceIndexFiles(task, project, taskProvider);
        });
        if (!project.equals(project.getRootProject())) {
            var rootIndexTaskProvider = indexTask(project.getRootProject());
            rootIndexTaskProvider.configure(task -> task.dependsOn(project.getPath() + ":" + INDEX_TASK_NAME));
            queryTask(project.getRootProject()).configure(task -> {
                task.dependsOn(rootIndexTaskProvider);
                addReferenceIndexFiles(task, project, taskProvider);
            });
        }

        project.getPlugins().withType(JavaPlugin.class, javaPlugin ->
            project.afterEvaluate(evaluatedProject ->
                taskProvider.configure(task -> {
                    task.setSourceSets(sourceSetSpecs(evaluatedProject));
                    task.dependsOn(compileJavaTasks(evaluatedProject));
                })
            )
        );
    }

    private static TaskProvider<IndexJavaReferencesTask> indexTask(Project project) {
        var tasks = project.getTasks();
        if (tasks.getNames().contains(INDEX_TASK_NAME)) {
            return tasks.named(INDEX_TASK_NAME, IndexJavaReferencesTask.class);
        }
        return tasks.register(INDEX_TASK_NAME, IndexJavaReferencesTask.class, task -> {
            task.setProjectPath(project.getPath());
            task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("reference-index"));
            task.setGroup("verification");
            task.setDescription("Build Java reference edge CSVs. Run with --info to log per-source-set timing.");
        });
    }

    private static TaskProvider<QueryJavaReferencesTask> queryTask(Project project) {
        var tasks = project.getTasks();
        if (tasks.getNames().contains(QUERY_TASK_NAME)) {
            return tasks.named(QUERY_TASK_NAME, QueryJavaReferencesTask.class);
        }
        return tasks.register(QUERY_TASK_NAME, QueryJavaReferencesTask.class, task -> {
            task.setGroup("verification");
            task.setDescription(QueryJavaReferencesTask.taskDescription());
        });
    }

    private static void addReferenceIndexFiles(
        QueryJavaReferencesTask task,
        Project project,
        TaskProvider<IndexJavaReferencesTask> indexTaskProvider
    ) {
        var referenceIndexFiles = project.fileTree(
            indexTaskProvider.flatMap(IndexJavaReferencesTask::getOutputDirectory),
            fileTree -> fileTree.include("*-references.csv")
        );
        task.getReferenceIndexFiles().from(referenceIndexFiles);
    }

    private static List<?> compileJavaTasks(Project project) {
        JavaPluginExtension java = project.getExtensions().findByType(JavaPluginExtension.class);
        if (java == null) {
            return List.of();
        }
        return java.getSourceSets().stream()
            .map(sourceSet -> project.getTasks().named(sourceSet.getCompileJavaTaskName()))
            .toList();
    }

    private static List<IndexJavaReferencesTask.SourceSetSpec> sourceSetSpecs(Project project) {
        JavaPluginExtension java = project.getExtensions().findByType(JavaPluginExtension.class);
        if (java == null) {
            return List.of();
        }
        return java.getSourceSets().stream()
            .flatMap(sourceSet -> {
                List<String> sourceFiles = sourceFiles(sourceSet);
                if (sourceFiles.isEmpty()) {
                    return Stream.empty();
                }
                return Stream.of(sourceSetSpec(project, sourceSet, sourceFiles));
            })
            .toList();
    }

    private static IndexJavaReferencesTask.SourceSetSpec sourceSetSpec(
        Project project,
        SourceSet sourceSet,
        List<String> sourceFiles
    ) {
        return new IndexJavaReferencesTask.SourceSetSpec(
            project.getPath(),
            sourceSet.getName(),
            project.getRootDir().toPath().toAbsolutePath().normalize().toString(),
            sourceRoots(project, sourceSet),
            sourceFiles,
            classpathEntries(project, sourceSet)
        );
    }

    private static List<IndexJavaReferencesTask.SourceRootSpec> sourceRoots(Project project, SourceSet sourceSet) {
        Set<Path> compileClasspath = sourceSet.getCompileClasspath().getFiles().stream()
            .map(JavaReferenceIndexPlugin::normalizedPath)
            .collect(Collectors.toSet());

        var currentSourceRoots = sourceRootSpecs(project, sourceSet, false);

        var currentProjectClasspathSourceRoots = projectSourceRoots(project, compileClasspath, false);

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
                return projectSourceRoots(candidateProject, compileClasspath, true);
            });

        return Stream.of(currentSourceRoots, currentProjectClasspathSourceRoots, dependencySourceRoots)
            .flatMap(sourceRoots -> sourceRoots)
            .sorted(Comparator.comparing(IndexJavaReferencesTask.SourceRootSpec::path))
            .distinct()
            .toList();
    }

    private static Stream<IndexJavaReferencesTask.SourceRootSpec> projectSourceRoots(
        Project project,
        Set<Path> compileClasspath,
        boolean includeMainFallback
    ) {
        JavaPluginExtension java = project.getExtensions().findByType(JavaPluginExtension.class);
        if (java == null) {
            return Stream.empty();
        }
        List<SourceSet> matchingSourceSets = java.getSourceSets().stream()
            .filter(candidateSourceSet -> outputIsOnClasspath(candidateSourceSet, compileClasspath))
            .toList();
        if (!matchingSourceSets.isEmpty()) {
            return matchingSourceSets.stream()
                .flatMap(candidateSourceSet -> sourceRootSpecs(project, candidateSourceSet, true));
        }
        if (!includeMainFallback) {
            return Stream.empty();
        }
        return java.getSourceSets().matching(candidateSourceSet -> SourceSet.MAIN_SOURCE_SET_NAME.equals(candidateSourceSet.getName()))
            .stream()
            .flatMap(candidateSourceSet -> sourceRootSpecs(project, candidateSourceSet, false));
    }

    private static boolean outputIsOnClasspath(SourceSet sourceSet, Set<Path> compileClasspath) {
        return sourceSetOutputPaths(sourceSet).anyMatch(compileClasspath::contains);
    }

    private static Stream<Path> sourceSetOutputPaths(SourceSet sourceSet) {
        Stream<Path> classesDirs = sourceSet.getOutput().getClassesDirs().getFiles().stream()
            .map(JavaReferenceIndexPlugin::normalizedPath);
        File resourcesDir = sourceSet.getOutput().getResourcesDir();
        if (resourcesDir == null) {
            return classesDirs;
        }
        return Stream.concat(classesDirs, Stream.of(normalizedPath(resourcesDir)));
    }

    private static Stream<IndexJavaReferencesTask.SourceRootSpec> sourceRootSpecs(
        Project project,
        SourceSet sourceSet,
        boolean includeMissingDirectories
    ) {
        return sourceSet.getAllJava().getSrcDirs().stream()
            .filter(sourceRoot -> includeMissingDirectories || sourceRoot.isDirectory())
            .map(sourceRoot -> new IndexJavaReferencesTask.SourceRootSpec(
                normalizedPath(sourceRoot).toString(),
                project.getPath(),
                sourceSet.getName()
            ));
    }

    private static Path normalizedPath(File file) {
        return file.toPath().toAbsolutePath().normalize();
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
            .filter(File::exists)
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
