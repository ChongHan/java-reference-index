package io.github.chonghan.javareferenceindex.model;

import java.util.Objects;

public record UnresolvedReference(
    String name
) {
    public UnresolvedReference {
        Objects.requireNonNull(name, "name");
    }
}
