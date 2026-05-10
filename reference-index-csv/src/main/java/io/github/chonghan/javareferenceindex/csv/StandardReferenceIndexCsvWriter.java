package io.github.chonghan.javareferenceindex.csv;

import io.github.chonghan.javareferenceindex.model.ProjectIndex;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

final class StandardReferenceIndexCsvWriter implements ReferenceIndexCsvWriter {
    private static final String[] HEADER = {
        "source_project",
        "source_path",
        "target_kind",
        "target_project",
        "target_path",
        "target_type"
    };

    @Override
    public void write(ProjectIndex index, CsvReferenceIndexWriteRequest request) throws IOException {
        Path parent = request.outputFile().toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path rootDirectory = request.rootDirectory().toAbsolutePath().normalize();
        Set<CsvReferenceRow> rows = new LinkedHashSet<>();
        for (var file : index.files()) {
            String sourceProject = index.project().path();
            String sourcePath = relativePath(file.sourceFile(), rootDirectory);

            for (var reference : file.sourceReferences()) {
                if (sameFile(file.sourceFile(), reference.sourceFile())) {
                    continue;
                }
                rows.add(new CsvReferenceRow(
                    sourceProject,
                    sourcePath,
                    "source",
                    reference.targetProject().path(),
                    relativePath(reference.sourceFile(), rootDirectory),
                    reference.qualifiedName()
                ));
            }

            for (var reference : file.binaryReferences()) {
                rows.add(new CsvReferenceRow(
                    sourceProject,
                    sourcePath,
                    "binary",
                    reference.targetProject(),
                    "",
                    reference.targetType()
                ));
            }

            for (var ignored : file.unresolvedReferences()) {
                rows.add(new CsvReferenceRow(sourceProject, sourcePath, "", "", "", ""));
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(request.outputFile(), StandardCharsets.UTF_8)) {
            writeRow(writer, HEADER);
            for (CsvReferenceRow row : rows) {
                writeRow(
                    writer,
                    row.sourceProject(),
                    row.sourcePath(),
                    row.targetKind(),
                    row.targetProject(),
                    row.targetPath(),
                    row.targetType()
                );
            }
        }
    }

    private static String relativePath(Path file, Path rootDirectory) {
        Path normalizedFile = file.toAbsolutePath().normalize();
        if (!normalizedFile.startsWith(rootDirectory)) {
            throw new IllegalArgumentException(
                "File " + normalizedFile + " is not under root directory " + rootDirectory
            );
        }
        return rootDirectory.relativize(normalizedFile).toString().replace('\\', '/');
    }

    private static boolean sameFile(Path first, Path second) {
        return first.toAbsolutePath().normalize().equals(second.toAbsolutePath().normalize());
    }

    private static void writeRow(BufferedWriter writer, String... columns) throws IOException {
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) {
                writer.write(',');
            }
            writer.write(escape(columns[i]));
        }
        writer.newLine();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean requiresQuoting = value.indexOf(',') >= 0
            || value.indexOf('"') >= 0
            || value.indexOf('\n') >= 0
            || value.indexOf('\r') >= 0;
        if (!requiresQuoting) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private record CsvReferenceRow(
        String sourceProject,
        String sourcePath,
        String targetKind,
        String targetProject,
        String targetPath,
        String targetType
    ) {}
}
