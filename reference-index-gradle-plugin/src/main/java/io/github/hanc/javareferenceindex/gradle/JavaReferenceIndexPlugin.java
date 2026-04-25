package io.github.hanc.javareferenceindex.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class JavaReferenceIndexPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getTasks().register("indexJavaReferences", IndexJavaReferencesTask.class);
    }
}
