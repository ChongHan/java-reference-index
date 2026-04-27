package io.github.chonghan.javareferenceindex.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.chonghan.javareferenceindex.model.BinaryReference;
import io.github.chonghan.javareferenceindex.model.FileReferenceSet;
import io.github.chonghan.javareferenceindex.model.ProjectCoordinates;
import io.github.chonghan.javareferenceindex.model.ProjectIndex;
import io.github.chonghan.javareferenceindex.model.SourceReference;
import io.github.chonghan.javareferenceindex.model.SourceSetCoordinates;
import io.github.chonghan.javareferenceindex.model.UnresolvedReference;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandardReferenceIndexCsvWriterTest {
    private final ReferenceIndexCsvWriter writer = ReferenceIndexCsvWriters.standard();

    @TempDir
    Path tempDir;

    @Test
    void write_withSourceBinaryAndUnresolvedReferences_writesMinimalReferenceRows() throws IOException {
        ProjectCoordinates app = new ProjectCoordinates(":apps:service-a");
        ProjectCoordinates lib = new ProjectCoordinates(":libs:shared");
        Path appDir = tempDir.resolve("apps/service-a");
        Path libDir = tempDir.resolve("libs/shared");
        Path appFile = appDir.resolve("src/main/java/app/App.java");
        Path libFile = libDir.resolve("src/main/java/shared/LibraryType.java");
        createFile(appFile);
        createFile(libFile);

        ProjectIndex index = new ProjectIndex(
            app,
            new SourceSetCoordinates("main"),
            List.of(new FileReferenceSet(
                appFile,
                List.of(new SourceReference("shared.LibraryType", libFile, lib, new SourceSetCoordinates("main"))),
                List.of(new BinaryReference(
                    "org.agrona.collections.IntArrayList",
                    "org.agrona:agrona:2.4.1",
                    "org.agrona.collections.IntArrayList"
                )),
                List.of(new UnresolvedReference("MissingType"))
            ))
        );
        Path outputFile = tempDir.resolve("build/reference-index/references.csv");

        writer.write(index, new CsvReferenceIndexWriteRequest(outputFile, tempDir));

        assertThat(Files.readAllLines(outputFile))
            .containsExactly(
                "source_project,source_path,target_kind,target_project,target",
                ":apps:service-a,apps/service-a/src/main/java/app/App.java,source,:libs:shared,libs/shared/src/main/java/shared/LibraryType.java",
                ":apps:service-a,apps/service-a/src/main/java/app/App.java,binary,org.agrona:agrona:2.4.1,org.agrona.collections.IntArrayList",
                ":apps:service-a,apps/service-a/src/main/java/app/App.java,,,"
            );
    }

    @Test
    void write_withNoReferences_writesHeaderOnly() throws IOException {
        ProjectCoordinates app = new ProjectCoordinates(":app");
        Path appDir = tempDir.resolve("app");
        Path appFile = appDir.resolve("src/main/java/app/Plain.java");
        createFile(appFile);
        ProjectIndex index = new ProjectIndex(
            app,
            new SourceSetCoordinates("main"),
            List.of(new FileReferenceSet(appFile, List.of(), List.of(), List.of()))
        );
        Path outputFile = tempDir.resolve("references.csv");

        writer.write(index, new CsvReferenceIndexWriteRequest(outputFile, tempDir));

        assertThat(Files.readAllLines(outputFile))
            .containsExactly("source_project,source_path,target_kind,target_project,target");
    }

    @Test
    void write_withDuplicateReferences_writesEachTargetOnce() throws IOException {
        ProjectCoordinates app = new ProjectCoordinates(":app");
        ProjectCoordinates lib = new ProjectCoordinates(":lib");
        SourceSetCoordinates main = new SourceSetCoordinates("main");
        Path appDir = tempDir.resolve("app");
        Path libDir = tempDir.resolve("lib");
        Path appFile = appDir.resolve("src/main/java/app/App.java");
        Path libFile = libDir.resolve("src/main/java/lib/LibraryType.java");
        createFile(appFile);
        createFile(libFile);

        SourceReference sourceReference = new SourceReference("lib.LibraryType", libFile, lib, main);
        BinaryReference binaryReference = new BinaryReference(
            "org.agrona.collections.IntArrayList",
            "org.agrona:agrona:2.4.1",
            "org.agrona.collections.IntArrayList"
        );
        ProjectIndex index = new ProjectIndex(
            app,
            main,
            List.of(new FileReferenceSet(
                appFile,
                List.of(sourceReference, sourceReference),
                List.of(binaryReference, binaryReference),
                List.of(new UnresolvedReference("MissingType"), new UnresolvedReference("OtherMissingType"))
            ))
        );
        Path outputFile = tempDir.resolve("references.csv");

        writer.write(index, new CsvReferenceIndexWriteRequest(outputFile, tempDir));

        assertThat(Files.readAllLines(outputFile))
            .containsExactly(
                "source_project,source_path,target_kind,target_project,target",
                ":app,app/src/main/java/app/App.java,source,:lib,lib/src/main/java/lib/LibraryType.java",
                ":app,app/src/main/java/app/App.java,binary,org.agrona:agrona:2.4.1,org.agrona.collections.IntArrayList",
                ":app,app/src/main/java/app/App.java,,,"
            );
    }

    @Test
    void write_withCsvSpecialCharacters_escapesValues() throws IOException {
        ProjectCoordinates app = new ProjectCoordinates(":app");
        Path appDir = tempDir.resolve("app");
        Path appFile = appDir.resolve("src/main/java/app/App.java");
        createFile(appFile);
        ProjectIndex index = new ProjectIndex(
            app,
            new SourceSetCoordinates("main"),
            List.of(new FileReferenceSet(
                appFile,
                List.of(),
                List.of(new BinaryReference(
                    "example.Binary",
                    "example:quoted,\"dependency\":1.0",
                    "example.Binary"
                )),
                List.of()
            ))
        );
        Path outputFile = tempDir.resolve("references.csv");

        writer.write(index, new CsvReferenceIndexWriteRequest(outputFile, tempDir));

        assertThat(Files.readAllLines(outputFile))
            .containsExactly(
                "source_project,source_path,target_kind,target_project,target",
                ":app,app/src/main/java/app/App.java,binary,\"example:quoted,\"\"dependency\"\":1.0\",example.Binary"
            );
    }

    @Test
    void write_withSourceOutsideRootDirectory_failsClearly() {
        ProjectCoordinates app = new ProjectCoordinates(":app");
        Path rootDirectory = tempDir.resolve("root");
        Path appFile = tempDir.resolve("other/app/src/main/java/app/App.java");
        ProjectIndex index = new ProjectIndex(
            app,
            new SourceSetCoordinates("main"),
            List.of(new FileReferenceSet(appFile, List.of(), List.of(), List.of()))
        );

        assertThatThrownBy(() -> writer.write(
            index,
            new CsvReferenceIndexWriteRequest(tempDir.resolve("references.csv"), rootDirectory)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("is not under root directory");
    }

    private static void createFile(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "");
    }
}
