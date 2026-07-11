package io.github.chonghan.javareferenceindex.model;

import java.util.Objects;

public record SourceSetCoordinates(
    String name
) {
    public SourceSetCoordinates {
        Objects.requireNonNull(name, "name");
    }
}
