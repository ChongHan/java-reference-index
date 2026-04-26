package io.github.hanc.javareferenceindex.csv;

import io.github.hanc.javareferenceindex.model.ProjectCoordinates;
import java.nio.file.Path;

public record ProjectDirectory(
    ProjectCoordinates project,
    Path directory
) {}
