package io.github.chonghan.javareferenceindex;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.chonghan.javareferenceindex.model.ClasspathEntry;
import io.github.chonghan.javareferenceindex.model.FileReferenceSet;
import io.github.chonghan.javareferenceindex.model.JavaCompilerSettings;
import io.github.chonghan.javareferenceindex.model.ProjectCoordinates;
import io.github.chonghan.javareferenceindex.model.ProjectIndex;
import io.github.chonghan.javareferenceindex.model.ProjectIndexingRequest;
import io.github.chonghan.javareferenceindex.model.SourceRoot;
import io.github.chonghan.javareferenceindex.model.SourceSetCoordinates;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

final class ReferenceIndexingFixtures {
    private ReferenceIndexingFixtures() {
    }

    static ProjectIndexingRequest request(Path sourceRoot, List<Path> sourceFiles, List<Path> classpathEntries) {
        return requestWithClasspathEntries(sourceRoot, sourceFiles, classpathEntries.stream().map(ClasspathEntry::of).toList());
    }

    static ProjectIndexingRequest requestWithClasspathEntries(
        Path sourceRoot,
        List<Path> sourceFiles,
        List<ClasspathEntry> classpathEntries
    ) {
        ProjectCoordinates project = new ProjectCoordinates(":fixture");
        SourceSetCoordinates sourceSet = new SourceSetCoordinates("main");
        return new ProjectIndexingRequest(
            project,
            sourceSet,
            List.of(new SourceRoot(sourceRoot, project, sourceSet)),
            sourceFiles,
            classpathEntries,
            JavaCompilerSettings.java21()
        );
    }

    static FileReferenceSet singleFile(ProjectIndex index) {
        assertThat(index.files()).hasSize(1);
        return index.files().getFirst();
    }

    static Path fixtureSourceRoot(String fixtureName) {
        return fixturePath(fixtureName).resolve("src/main/java").toAbsolutePath().normalize();
    }

    static Path fixturePath(String fixtureName) {
        try {
            return Path.of(ReferenceIndexingFixtures.class.getResource("/fixtures/" + fixtureName).toURI())
                .toAbsolutePath()
                .normalize();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    static Path classpathJarContaining(Class<?> type) {
        try {
            return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI())
                .toAbsolutePath()
                .normalize();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
