package io.github.chonghan.javareferenceindex.model;

import java.util.Objects;

public record ProjectCoordinates(
    String path
) {
    public ProjectCoordinates {
        Objects.requireNonNull(path, "path");
    }
}
