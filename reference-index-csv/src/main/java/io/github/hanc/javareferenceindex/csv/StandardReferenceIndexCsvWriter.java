package io.github.hanc.javareferenceindex.csv;

import io.github.hanc.javareferenceindex.model.ProjectCoordinates;
import io.github.hanc.javareferenceindex.model.ProjectIndex;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class StandardReferenceIndexCsvWriter implements ReferenceIndexCsvWriter {
    private static final String[] HEADER = {
        "source_project",
        "source_path",
        "target_kind",
        "target_project",
        "target"
    };

    @Override
    public void write(ProjectIndex index, CsvReferenceIndexWriteRequest request) throws IOException {
        Path parent = request.outputFile().toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Map<ProjectCoordinates, Path> projectDirectories = projectDirectories(request);
        Set<CsvReferenceRow> rows = new LinkedHashSet<>();
        for (var file : index.files()) {
            String sourceProject = index.project().path();
            String sourcePath = relativePath(file.sourceFile(), projectDirectories.get(index.project()), index.project());

            for (var reference : file.sourceReferences()) {
                rows.add(new CsvReferenceRow(
                    sourceProject,
                    sourcePath,
                    "source",
                    reference.targetProject().path(),
                    relativePath(
                        reference.sourceFile(),
                        projectDirectories.get(reference.targetProject()),
                        reference.targetProject()
                    )
                ));
            }

            for (var reference : file.binaryReferences()) {
                rows.add(new CsvReferenceRow(sourceProject, sourcePath, "binary", "", reference.target()));
            }

            for (var ignored : file.unresolvedReferences()) {
                rows.add(new CsvReferenceRow(sourceProject, sourcePath, "", "", ""));
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(request.outputFile(), StandardCharsets.UTF_8)) {
            writeRow(writer, HEADER);
            for (CsvReferenceRow row : rows) {
                writeRow(writer, row.sourceProject(), row.sourcePath(), row.targetKind(), row.targetProject(), row.target());
            }
        }
    }

    private static Map<ProjectCoordinates, Path> projectDirectories(CsvReferenceIndexWriteRequest request) {
        return request.projectDirectories().stream()
            .collect(Collectors.toUnmodifiableMap(
                ProjectDirectory::project,
                projectDirectory -> projectDirectory.directory().toAbsolutePath().normalize(),
                (first, second) -> first
            ));
    }

    private static String relativePath(Path file, Path projectDirectory, ProjectCoordinates project) {
        if (projectDirectory == null) {
            throw new IllegalArgumentException("Missing directory for project " + project.path());
        }

        Path normalizedFile = file.toAbsolutePath().normalize();
        if (!normalizedFile.startsWith(projectDirectory)) {
            throw new IllegalArgumentException(
                "File " + normalizedFile + " is not under project " + project.path() + " directory " + projectDirectory
            );
        }
        return projectDirectory.relativize(normalizedFile).toString().replace('\\', '/');
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
        String target
    ) {}
}
