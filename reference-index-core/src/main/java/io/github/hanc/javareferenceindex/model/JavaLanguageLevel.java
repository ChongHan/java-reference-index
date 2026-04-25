package io.github.hanc.javareferenceindex.model;

public enum JavaLanguageLevel {
    JAVA_21("21");

    private final String compilerLevel;

    JavaLanguageLevel(String compilerLevel) {
        this.compilerLevel = compilerLevel;
    }

    public String compilerLevel() {
        return compilerLevel;
    }
}
