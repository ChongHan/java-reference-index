package io.github.chonghan.javareferenceindex.gradle;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

/**
 * Gradle plugin that registers Java reference indexing and query tasks.
 */
public class JavaReferenceIndexPlugin implements Plugin<Project> {
    static final String INDEX_TASK_NAME = "javaReferenceIndex";
    static final String INDEX_ALL_TASK_NAME = "javaReferenceIndexAll";
    static final String QUERY_TASK_NAME = "javaReferenceQuery";

    /**
     * Creates the Java reference index plugin.
     */
    public JavaReferenceIndexPlugin() {
    }

    /**
     * Registers Java reference index tasks for the supplied project.
     *
     * @param project project to configure
     */
    @Override
    public void apply(Project project) {
        var taskProvider = indexTask(project);
        var queryTaskProvider = queryTask(project);
        queryTaskProvider.configure(task -> {
            task.dependsOn(taskProvider);
            addReferenceIndexFiles(task, project, taskProvider);
        });

        project.getPlugins().withType(JavaPlugin.class, javaPlugin ->
            project.afterEvaluate(evaluatedProject ->
                taskProvider.configure(task -> {
                    task.setSourceSets(sourceSetSpecs(evaluatedProject));
                    task.getSourceInputFiles().from(sourceInputFiles(evaluatedProject));
                    task.dependsOn(compileJavaTasks(evaluatedProject));
                })
            )
        );

        if (!project.equals(project.getRootProject())) {
            return;
        }

        var indexAllTaskProvider = indexAllTask(project);
        indexAllTaskProvider.configure(task -> task.dependsOn(taskProvider));
        queryTaskProvider.configure(task -> task.dependsOn(indexAllTaskProvider));
        configureRootAggregateTasks(project, indexAllTaskProvider);
    }

    private static void configureRootAggregateTasks(Project rootProject, TaskProvider<?> indexAllTaskProvider) {
        if (!rootAggregateTaskRequested(rootProject)) {
            return;
        }
        // Project paths and directories are immutable model state and safe to inspect with Isolated Projects.
        rootProject.getAllprojects().stream()
            .filter(candidate -> !candidate.equals(rootProject))
            .forEach(candidate -> {
                String indexTaskPath = candidate.getPath() + ":" + INDEX_TASK_NAME;
                indexAllTaskProvider.configure(task -> task.dependsOn(indexTaskPath));
                queryTask(rootProject).configure(task -> addReferenceIndexFiles(
                    task,
                    rootProject,
                    candidate.getProjectDir().toPath().resolve("build/reference-index")
                ));
            });
    }

    private static boolean rootAggregateTaskRequested(Project project) {
        return project.getGradle().getStartParameter().getTaskNames().stream()
            .anyMatch(JavaReferenceIndexPlugin::isRootAggregateTask);
    }

    private static boolean isRootAggregateTask(String taskName) {
        return taskName.equals(INDEX_ALL_TASK_NAME)
            || taskName.equals(":" + INDEX_ALL_TASK_NAME)
            || taskName.equals(":" + QUERY_TASK_NAME);
    }

    private static TaskProvider<Task> indexAllTask(Project project) {
        var tasks = project.getTasks();
        if (tasks.getNames().contains(INDEX_ALL_TASK_NAME)) {
            return tasks.named(INDEX_ALL_TASK_NAME);
        }
        return tasks.register(INDEX_ALL_TASK_NAME, task -> {
            task.setGroup("verification");
            task.setDescription("Build Java reference edge CSVs for all projects.");
        });
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
        addReferenceIndexFiles(task, project, indexTaskProvider.flatMap(IndexJavaReferencesTask::getOutputDirectory));
    }

    private static void addReferenceIndexFiles(QueryJavaReferencesTask task, Project project, Object outputDirectory) {
        var referenceIndexFiles = project.fileTree(
            outputDirectory,
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
                List<IndexJavaReferencesTask.SourceRootSpec> sourceRoots = sourceRoots(project, sourceSet);
                if (sourceRoots.isEmpty()) {
                    return Stream.empty();
                }
                return Stream.of(sourceSetSpec(project, sourceSet, sourceRoots));
            })
            .toList();
    }

    private static IndexJavaReferencesTask.SourceSetSpec sourceSetSpec(
        Project project,
        SourceSet sourceSet,
        List<IndexJavaReferencesTask.SourceRootSpec> sourceRoots
    ) {
        return new IndexJavaReferencesTask.SourceSetSpec(
            project.getPath(),
            sourceSet.getName(),
            project.getRootDir().toPath().toAbsolutePath().normalize().toString(),
            sourceRoots,
            classpathEntries(project, sourceSet)
        );
    }

    private static List<IndexJavaReferencesTask.SourceRootSpec> sourceRoots(Project project, SourceSet sourceSet) {
        Set<Path> compileClasspath = sourceSet.getCompileClasspath().getFiles().stream()
            .map(JavaReferenceIndexPlugin::normalizedPath)
            .collect(Collectors.toSet());

        var currentSourceRoots = sourceRootSpecs(project, sourceSet, false);

        var currentProjectClasspathSourceRoots = projectSourceRoots(project, compileClasspath);

        var dependencySourceRoots = projectDependencyPaths(project, sourceSet).stream()
            .map(project.getRootProject()::findProject)
            .filter(candidateProject -> candidateProject != null)
            .flatMap(candidateProject -> conventionalProjectSourceRoots(candidateProject, compileClasspath));

        return Stream.of(currentSourceRoots, currentProjectClasspathSourceRoots, dependencySourceRoots)
            .flatMap(sourceRoots -> sourceRoots)
            .sorted(Comparator.comparing(IndexJavaReferencesTask.SourceRootSpec::path))
            .distinct()
            .toList();
    }

    // Dependency resolution exposes project identities but not the target project's mutable Java model
    // under Isolated Projects, so derive conventional source roots from its immutable project directory.
    private static Stream<IndexJavaReferencesTask.SourceRootSpec> conventionalProjectSourceRoots(
        Project project,
        Set<Path> compileClasspath
    ) {
        Path sourceDirectory = project.getProjectDir().toPath().resolve("src");
        if (!sourceDirectory.toFile().isDirectory()) {
            return Stream.empty();
        }
        try (Stream<Path> children = Files.list(sourceDirectory)) {
            List<Path> sourceRoots = children
                .map(sourceSetDirectory -> sourceSetDirectory.resolve("java"))
                .filter(path -> path.toFile().isDirectory())
                .toList();
            List<Path> selectedRoots = sourceRoots.stream()
                .filter(path -> sourceRootIsOnClasspath(project, path, compileClasspath))
                .toList();
            if (selectedRoots.isEmpty()) {
                selectedRoots = sourceRoots.stream()
                    .filter(path -> path.getParent().getFileName().toString().equals(SourceSet.MAIN_SOURCE_SET_NAME))
                    .toList();
            }
            return selectedRoots.stream().map(path -> new IndexJavaReferencesTask.SourceRootSpec(
                path.toAbsolutePath().normalize().toString(),
                project.getPath(),
                path.getParent().getFileName().toString()
            ));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean sourceRootIsOnClasspath(Project project, Path sourceRoot, Set<Path> compileClasspath) {
        String sourceSetName = sourceRoot.getParent().getFileName().toString();
        String projectName = project.getName();
        return compileClasspath.stream().anyMatch(path -> {
            String fileName = path.getFileName().toString();
            if (SourceSet.MAIN_SOURCE_SET_NAME.equals(sourceSetName)) {
                return fileName.equals(projectName + ".jar")
                    || path.endsWith(Path.of("classes", "java", SourceSet.MAIN_SOURCE_SET_NAME));
            }
            return fileName.equals(projectName + "-" + sourceSetName + ".jar")
                || path.endsWith(Path.of("classes", "java", sourceSetName));
        });
    }

    private static Stream<IndexJavaReferencesTask.SourceRootSpec> projectSourceRoots(
        Project project,
        Set<Path> compileClasspath
    ) {
        JavaPluginExtension java = project.getExtensions().findByType(JavaPluginExtension.class);
        if (java == null) {
            return Stream.empty();
        }
        List<SourceSet> matchingSourceSets = java.getSourceSets().stream()
            .filter(candidateSourceSet -> outputIsOnClasspath(project, candidateSourceSet, compileClasspath))
            .toList();
        return matchingSourceSets.stream()
            .flatMap(candidateSourceSet -> sourceRootSpecs(project, candidateSourceSet, true));
    }

    private static boolean outputIsOnClasspath(Project project, SourceSet sourceSet, Set<Path> compileClasspath) {
        return sourceSetOutputPaths(sourceSet).anyMatch(compileClasspath::contains)
            || sourceSetArtifactOutputs(project, sourceSet).anyMatch(compileClasspath::contains);
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

    private static Stream<Path> sourceSetArtifactOutputs(Project project, SourceSet sourceSet) {
        return project.getConfigurations().stream()
            .filter(Configuration::isCanBeConsumed)
            .flatMap(configuration -> configuration.getOutgoing().getArtifacts().stream())
            .filter(artifact -> artifactRepresentsSourceSet(artifact, sourceSet))
            .map(artifact -> normalizedPath(artifact.getFile()));
    }

    private static boolean artifactRepresentsSourceSet(PublishArtifact artifact, SourceSet sourceSet) {
        if (SourceSet.MAIN_SOURCE_SET_NAME.equals(sourceSet.getName())) {
            return artifact.getClassifier() == null;
        }
        return sourceSet.getName().equals(artifact.getClassifier());
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

    private static List<?> sourceInputFiles(Project project) {
        JavaPluginExtension java = project.getExtensions().findByType(JavaPluginExtension.class);
        if (java == null) {
            return List.of();
        }
        return java.getSourceSets().stream()
            .map(SourceSet::getAllJava)
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
