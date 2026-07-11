package io.github.chonghan.javareferenceindex.model;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record JavaCompilerSettings(
    JavaLanguageLevel sourceLevel,
    JavaLanguageLevel targetLevel,
    JavaLanguageLevel release,
    Charset encoding
) {
    public JavaCompilerSettings {
        Objects.requireNonNull(sourceLevel, "sourceLevel");
        Objects.requireNonNull(targetLevel, "targetLevel");
        Objects.requireNonNull(encoding, "encoding");
    }

    public static JavaCompilerSettings java21() {
        return new JavaCompilerSettings(
            JavaLanguageLevel.JAVA_21,
            JavaLanguageLevel.JAVA_21,
            JavaLanguageLevel.JAVA_21,
            StandardCharsets.UTF_8
        );
    }

    public JavaLanguageLevel effectiveSourceLevel() {
        return release == null ? sourceLevel : release;
    }
}
