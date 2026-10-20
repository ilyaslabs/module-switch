package io.github.ilyaslabs.moduleswitch.it;

import io.github.ilyaslabs.moduleswitch.test.ModuleRules;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModuleRulesTest {

    @Test
    void wellStructuredApplicationPasses() {
        ModuleRules.verify("io.github.ilyaslabs.moduleswitch.it.shop");
    }

    @Test
    void reportsEveryViolation() {
        assertThat(ModuleRules.of("io.github.ilyaslabs.moduleswitch.it.badshop").violations())
            .anySatisfy((v) -> assertThat(v).contains("overloads method 'find'"))
            .anySatisfy((v) -> assertThat(v).contains("LeftApi.unnamed parameter 1 has no @Param"))
            .anySatisfy((v) -> assertThat(v).contains("RightService (module 'right') uses"))
            .anySatisfy((v) -> assertThat(v).contains("Module 'right' has no @ModuleApi package"));
    }

    @Test
    void verifyFailsWithAllViolations() {
        assertThatThrownBy(() -> ModuleRules.verify("io.github.ilyaslabs.moduleswitch.it.badshop"))
            .isInstanceOf(AssertionError.class)
            .hasMessageStartingWith("Module rules violated (4)");
    }

    @Test
    void packagesWithoutModulesFail() {
        assertThat(ModuleRules.of("io.github.ilyaslabs.moduleswitch.it.nothing").violations()).singleElement()
            .asString()
            .contains("No modules found");
    }

    @Test
    void needsAtLeastOnePackage() {
        assertThatThrownBy(ModuleRules::of).isInstanceOf(IllegalArgumentException.class);
    }
}
