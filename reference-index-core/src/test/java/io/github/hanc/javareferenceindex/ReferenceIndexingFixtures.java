package io.github.hanc.javareferenceindex;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hanc.javareferenceindex.model.FileReferenceSet;
import io.github.hanc.javareferenceindex.model.JavaCompilerSettings;
import io.github.hanc.javareferenceindex.model.ProjectCoordinates;
import io.github.hanc.javareferenceindex.model.ProjectIndex;
import io.github.hanc.javareferenceindex.model.ProjectIndexingRequest;
import io.github.hanc.javareferenceindex.model.SourceRoot;
import io.github.hanc.javareferenceindex.model.SourceSetCoordinates;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

final class ReferenceIndexingFixtures {
    private ReferenceIndexingFixtures() {
    }

    static ProjectIndexingRequest request(Path sourceRoot, List<Path> sourceFiles, List<Path> classpathEntries) {
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
        try {
            return Path.of(ReferenceIndexingFixtures.class.getResource("/fixtures/" + fixtureName + "/src/main/java").toURI())
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
