package io.github.chonghan.javareferenceindex.model;

public enum JavaLanguageLevel {
    JAVA_8("1.8"),
    JAVA_9("9"),
    JAVA_10("10"),
    JAVA_11("11"),
    JAVA_12("12"),
    JAVA_13("13"),
    JAVA_14("14"),
    JAVA_15("15"),
    JAVA_16("16"),
    JAVA_17("17"),
    JAVA_18("18"),
    JAVA_19("19"),
    JAVA_20("20"),
    JAVA_21("21");

    private final String compilerLevel;

    JavaLanguageLevel(String compilerLevel) {
        this.compilerLevel = compilerLevel;
    }

    public String compilerLevel() {
        return compilerLevel;
    }

    public static JavaLanguageLevel fromCompilerLevel(String compilerLevel) {
        String normalized = "8".equals(compilerLevel) ? "1.8" : compilerLevel;
        for (JavaLanguageLevel level : values()) {
            if (level.compilerLevel.equals(normalized)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unsupported Java language level: " + compilerLevel);
    }
}
