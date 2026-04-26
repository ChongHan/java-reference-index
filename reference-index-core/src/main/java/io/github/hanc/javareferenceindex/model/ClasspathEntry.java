package io.github.hanc.javareferenceindex.model;

import java.nio.file.Path;
import java.util.Objects;

public record ClasspathEntry(
    Path path,
    String target
) {
    public ClasspathEntry {
        Objects.requireNonNull(path, "path");
        if (target == null || target.isBlank()) {
            target = path.getFileName().toString();
        }
    }

    public static ClasspathEntry of(Path path) {
        return new ClasspathEntry(path, null);
    }

    public static ClasspathEntry of(Path path, String target) {
        return new ClasspathEntry(path, target);
    }
}
