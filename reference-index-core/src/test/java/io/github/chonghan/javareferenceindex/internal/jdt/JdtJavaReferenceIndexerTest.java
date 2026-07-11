package io.github.chonghan.javareferenceindex.internal.jdt;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.chonghan.javareferenceindex.model.JavaCompilerSettings;
import io.github.chonghan.javareferenceindex.model.ProjectCoordinates;
import io.github.chonghan.javareferenceindex.model.ProjectIndexingRequest;
import io.github.chonghan.javareferenceindex.model.SourceSetCoordinates;
import java.util.List;
import org.junit.jupiter.api.Test;

class JdtJavaReferenceIndexerTest {
    @Test
    void index_withNoSourceFiles_doesNotInvokeScanner() {
        FileReferenceScanner scanner = request -> {
            throw new AssertionError("Scanner should not be invoked for an empty source set");
        };
        var indexer = new JdtJavaReferenceIndexer(scanner);
        var request = new ProjectIndexingRequest(
            new ProjectCoordinates(":empty"),
            new SourceSetCoordinates("main"),
            List.of(),
            List.of(),
            List.of(),
            JavaCompilerSettings.java21()
        );

        var index = indexer.index(request);

        assertThat(index.project()).isEqualTo(request.project());
        assertThat(index.sourceSet()).isEqualTo(request.sourceSet());
        assertThat(index.files()).isEmpty();
    }
}
