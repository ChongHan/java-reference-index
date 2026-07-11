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
import java.util.regex.Pattern;
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
import org.gradle.api.tasks.compile.JavaCompile;

/**
 * Gradle plugin that registers Java reference indexing and query tasks.
 */
public class JavaReferenceIndexPlugin implements Plugin<Project> {
    static final String INDEX_TASK_NAME = "javaReferenceIndex";
    static final String INDEX_ALL_TASK_NAME = "javaReferenceIndexAll";
    static final String QUERY_TASK_NAME = "javaReferenceQuery";

    private static final String INDEX_ELEMENTS_CONFIGURATION_NAME = "javaReferenceIndexElements";
    private static final String INDEXES_CONFIGURATION_NAME = "javaReferenceIndexes";
    private static final String JAVA_IDENTIFIER =
        "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*";
    private static final Pattern PACKAGE_DECLARATION = Pattern.compile(
        "(?m)^\\s*package\\s+(" + JAVA_IDENTIFIER + "(?:\\." + JAVA_IDENTIFIER + ")*)\\s*;"
    );

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
        var indexTask = indexTask(project);
        configureIndexElements(project, indexTask);

        var queryTask = queryTask(project);
        queryTask.configure(task -> {
            task.dependsOn(indexTask);
            addReferenceIndexFiles(task, project, indexTask);
        });

        project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
            JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
            indexTask.configure(task -> {
                List<IndexJavaReferencesTask.SourceSetSpec> sourceSets = sourceSetSpecs(project, java);
                task.setSourceSets(sourceSets);
                task.getSourceInputFiles().from(sourceInputFiles(java));
                task.getDependencySourceInputFiles().from(dependencySourceInputFiles(project, sourceSets));
                task.dependsOn(compileJavaTasks(project, java));
            });
        });

        if (!project.equals(project.getRootProject())) {
            return;
        }

        var indexAllTask = indexAllTask(project);
        indexAllTask.configure(task -> task.dependsOn(indexTask));
        queryTask.configure(task -> task.dependsOn(indexAllTask));
        configureRootAggregateTasks(project, indexAllTask, queryTask);
    }

    private static void configureRootAggregateTasks(
        Project rootProject,
        TaskProvider<?> indexAllTask,
        TaskProvider<QueryJavaReferencesTask> queryTask
    ) {
        Configuration indexes = rootProject.getConfigurations().create(INDEXES_CONFIGURATION_NAME, configuration -> {
            configuration.setDescription("Java reference indexes produced by subprojects.");
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(true);
        });

        // Project paths are immutable model state and safe to inspect with Isolated Projects.
        rootProject.getAllprojects().stream()
            .filter(candidate -> !candidate.equals(rootProject))
            .map(Project::getPath)
            .map(projectPath -> rootProject.getDependencies().project(Map.of(
                "path", projectPath,
                "configuration", INDEX_ELEMENTS_CONFIGURATION_NAME
            )))
            .forEach(dependency -> rootProject.getDependencies().add(indexes.getName(), dependency));

        indexAllTask.configure(task -> task.dependsOn(indexes));
        queryTask.configure(task -> task.getReferenceIndexFiles().from(
            indexes.getAsFileTree().matching(pattern -> pattern.include("**/*-references.csv"))
        ));
    }

    private static void configureIndexElements(
        Project project,
        TaskProvider<IndexJavaReferencesTask> indexTaskProvider
    ) {
        Configuration indexElements = project.getConfigurations().create(
            INDEX_ELEMENTS_CONFIGURATION_NAME,
            configuration -> {
                configuration.setDescription("Java reference index produced by this project.");
                configuration.setCanBeConsumed(true);
                configuration.setCanBeResolved(false);
            }
        );
        project.getArtifacts().add(
            indexElements.getName(),
            indexTaskProvider.flatMap(IndexJavaReferencesTask::getOutputDirectory),
            artifact -> artifact.builtBy(indexTaskProvider)
        );
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
        TaskProvider<IndexJavaReferencesTask> indexTask
    ) {
        task.getReferenceIndexFiles().from(project.fileTree(
            indexTask.flatMap(IndexJavaReferencesTask::getOutputDirectory),
            fileTree -> fileTree.include("*-references.csv")
        ));
    }

    private static List<?> compileJavaTasks(Project project, JavaPluginExtension java) {
        return java.getSourceSets().stream()
            .map(sourceSet -> project.getTasks().named(sourceSet.getCompileJavaTaskName()))
            .toList();
    }

    private static List<IndexJavaReferencesTask.SourceSetSpec> sourceSetSpecs(
        Project project,
        JavaPluginExtension java
    ) {
        return java.getSourceSets().stream()
            .map(sourceSet -> sourceSetSpec(project, sourceSet, sourceRoots(project, java, sourceSet)))
            .toList();
    }

    private static IndexJavaReferencesTask.SourceSetSpec sourceSetSpec(
        Project project,
        SourceSet sourceSet,
        List<IndexJavaReferencesTask.SourceRootSpec> sourceRoots
    ) {
        JavaCompile compileJava = project.getTasks()
            .named(sourceSet.getCompileJavaTaskName(), JavaCompile.class)
            .get();
        Integer release = compileJava.getOptions().getRelease().getOrNull();
        return new IndexJavaReferencesTask.SourceSetSpec(
            project.getPath(),
            sourceSet.getName(),
            project.getRootDir().toPath().toAbsolutePath().normalize().toString(),
            sourceRoots,
            classpathEntries(project, sourceSet),
            compileJava.getSourceCompatibility(),
            compileJava.getTargetCompatibility(),
            release == null ? null : release.toString(),
            compileJava.getOptions().getEncoding()
        );
    }

    private static List<IndexJavaReferencesTask.SourceRootSpec> sourceRoots(
        Project project,
        JavaPluginExtension java,
        SourceSet sourceSet
    ) {
        Set<Path> compileClasspath = sourceSet.getCompileClasspath().getFiles().stream()
            .map(JavaReferenceIndexPlugin::normalizedPath)
            .collect(Collectors.toSet());

        var currentSourceRoots = sourceRootSpecs(project, sourceSet, false);

        var currentProjectClasspathSourceRoots = projectSourceRoots(project, java, compileClasspath);

        var dependencySourceRoots = projectDependencyPaths(project, sourceSet).stream()
            .map(project.getRootProject()::findProject)
            .filter(candidateProject -> candidateProject != null)
            .flatMap(candidateProject -> dependencyProjectSourceRoots(candidateProject, compileClasspath));

        return Stream.of(currentSourceRoots, currentProjectClasspathSourceRoots, dependencySourceRoots)
            .flatMap(sourceRoots -> sourceRoots)
            .sorted(Comparator.comparing(IndexJavaReferencesTask.SourceRootSpec::path))
            .distinct()
            .toList();
    }

    // Dependency resolution exposes project identities but not the target project's mutable Java model
    // under Isolated Projects, so discover source roots from its immutable project directory.
    private static Stream<IndexJavaReferencesTask.SourceRootSpec> dependencyProjectSourceRoots(
        Project project,
        Set<Path> compileClasspath
    ) {
        Path projectDirectory = project.getProjectDir().toPath().toAbsolutePath().normalize();
        List<Path> sourceRoots = Stream.concat(
                conventionalSourceRoots(projectDirectory).stream(),
                inferredSourceRoots(project, projectDirectory).stream()
            )
            .distinct()
            .toList();

        List<Path> selectedRoots = sourceRoots.stream()
            .filter(path -> sourceRootIsOnClasspath(project, path, compileClasspath))
            .toList();
        if (selectedRoots.isEmpty()) {
            // Custom artifacts do not necessarily retain a source-set name in their file name.
            selectedRoots = sourceRoots;
        }

        return selectedRoots.stream().map(path -> new IndexJavaReferencesTask.SourceRootSpec(
            path.toString(),
            project.getPath(),
            inferredSourceSetName(projectDirectory, path)
        ));
    }

    private static List<Path> conventionalSourceRoots(Path projectDirectory) {
        Path sourceDirectory = projectDirectory.resolve("src");
        if (!Files.isDirectory(sourceDirectory)) {
            return List.of();
        }
        try (Stream<Path> sourceSets = Files.list(sourceDirectory)) {
            return sourceSets
                .map(sourceSetDirectory -> sourceSetDirectory.resolve("java"))
                .filter(Files::isDirectory)
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Path> inferredSourceRoots(Project project, Path projectDirectory) {
        List<Path> nestedProjectDirectories = project.getRootProject().getAllprojects().stream()
            .filter(candidate -> !candidate.getPath().equals(project.getPath()))
            .map(candidate -> candidate.getProjectDir().toPath().toAbsolutePath().normalize())
            .filter(path -> path.startsWith(projectDirectory))
            .toList();

        try (Stream<Path> paths = Files.walk(projectDirectory)) {
            return paths
                .filter(path -> nestedProjectDirectories.stream().noneMatch(path::startsWith))
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".java"))
                .map(JavaReferenceIndexPlugin::sourceRootFromPackage)
                .distinct()
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path sourceRootFromPackage(Path sourceFile) {
        String source;
        try {
            source = Files.readString(sourceFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        var packageDeclaration = PACKAGE_DECLARATION.matcher(source);
        if (!packageDeclaration.find()) {
            return sourceFile.getParent();
        }

        Path sourceRoot = sourceFile.getParent();
        String[] packageSegments = packageDeclaration.group(1).split("\\.");
        for (int index = packageSegments.length - 1; index >= 0; index--) {
            if (sourceRoot == null
                || sourceRoot.getFileName() == null
                || !sourceRoot.getFileName().toString().equals(packageSegments[index])) {
                return sourceFile.getParent();
            }
            sourceRoot = sourceRoot.getParent();
        }
        return sourceRoot;
    }

    private static String inferredSourceSetName(Path projectDirectory, Path sourceRoot) {
        Path conventionalSourceDirectory = projectDirectory.resolve("src");
        if (sourceRoot.startsWith(conventionalSourceDirectory)
            && sourceRoot.getNameCount() >= conventionalSourceDirectory.getNameCount() + 2
            && sourceRoot.getFileName().toString().equals("java")) {
            return sourceRoot.getParent().getFileName().toString();
        }
        return SourceSet.MAIN_SOURCE_SET_NAME;
    }

    private static boolean sourceRootIsOnClasspath(Project project, Path sourceRoot, Set<Path> compileClasspath) {
        String sourceSetName = inferredSourceSetName(
            project.getProjectDir().toPath().toAbsolutePath().normalize(),
            sourceRoot
        );
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
        JavaPluginExtension java,
        Set<Path> compileClasspath
    ) {
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

    private static List<?> sourceInputFiles(JavaPluginExtension java) {
        return java.getSourceSets().stream()
            .map(SourceSet::getAllJava)
            .toList();
    }

    private static List<?> dependencySourceInputFiles(
        Project project,
        List<IndexJavaReferencesTask.SourceSetSpec> sourceSets
    ) {
        return sourceSets.stream()
            .flatMap(sourceSet -> sourceSet.sourceRoots().stream())
            .filter(sourceRoot -> !sourceRoot.projectPath().equals(project.getPath()))
            // Generated sources are tracked through the compiled classpath. Registering another project's
            // generated outputs directly would require cross-project task-model access under Isolated Projects.
            .filter(sourceRoot -> !isConventionalBuildOutput(project, sourceRoot))
            .distinct()
            .map(sourceRoot -> project.fileTree(sourceRoot.path(), tree -> tree.include("**/*.java")))
            .toList();
    }

    private static boolean isConventionalBuildOutput(
        Project project,
        IndexJavaReferencesTask.SourceRootSpec sourceRoot
    ) {
        Project owner = project.getRootProject().findProject(sourceRoot.projectPath());
        if (owner == null) {
            return false;
        }
        Path projectDirectory = normalizedPath(owner.getProjectDir());
        Path sourceRootPath = Path.of(sourceRoot.path()).toAbsolutePath().normalize();
        if (!sourceRootPath.startsWith(projectDirectory)) {
            return false;
        }
        Path relativePath = projectDirectory.relativize(sourceRootPath);
        if (relativePath.getNameCount() == 0) {
            return false;
        }
        String firstSegment = relativePath.getName(0).toString();
        return "build".equals(firstSegment) || ".gradle".equals(firstSegment);
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
