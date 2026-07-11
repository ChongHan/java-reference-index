package io.github.chonghan.javareferenceindex.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class JavaLanguageLevelTest {
    @Test
    void fromCompilerLevel_supportsEveryDeclaredLevel() {
        assertThat(JavaLanguageLevel.values()).allSatisfy(level ->
            assertThat(JavaLanguageLevel.fromCompilerLevel(level.compilerLevel())).isEqualTo(level)
        );
    }

    @Test
    void fromCompilerLevel_acceptsGradleJava8Alias() {
        assertThat(JavaLanguageLevel.fromCompilerLevel("8")).isEqualTo(JavaLanguageLevel.JAVA_8);
    }

    @Test
    void fromCompilerLevel_rejectsUnsupportedVersions() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> JavaLanguageLevel.fromCompilerLevel("26"))
            .withMessage("Unsupported Java language level: 26");
    }
}
