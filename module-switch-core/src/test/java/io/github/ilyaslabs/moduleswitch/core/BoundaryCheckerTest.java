package io.github.ilyaslabs.moduleswitch.core;

import io.github.ilyaslabs.moduleswitch.core.fixtures.alpha.api.AlphaApi;
import io.github.ilyaslabs.moduleswitch.core.fixtures.alpha.impl.AlphaService;
import io.github.ilyaslabs.moduleswitch.core.fixtures.beta.impl.BetaService;
import io.github.ilyaslabs.moduleswitch.core.fixtures.beta.impl.BetaViolation;
import io.github.ilyaslabs.moduleswitch.core.fixtures.plain.PlainConsumer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BoundaryCheckerTest {

    private final BoundaryChecker checker = new BoundaryChecker(new ModulePackageResolver());

    @Test
    void usingAnotherModuleThroughItsApiIsAllowed() {
        assertThat(this.checker.check(BetaService.class)).isEmpty();
    }

    @Test
    void usingOwnModuleIsAllowed() {
        assertThat(this.checker.check(AlphaService.class)).isEmpty();
        assertThat(this.checker.check(AlphaApi.class)).isEmpty();
    }

    @Test
    void reportsFieldAndGenericReferencesToAnotherModulesImplementation() {
        assertThat(this.checker.check(BetaViolation.class)).hasSize(2)
            .allSatisfy((p) -> assertThat(p).contains("(module 'beta')").contains("module 'alpha'"))
            .anySatisfy((p) -> assertThat(p).contains("AlphaService"))
            .anySatisfy((p) -> assertThat(p).contains("AlphaHelper"));
    }

    @Test
    void reportsCodeOutsideModulesUsingAnImplementation() {
        assertThat(this.checker.check(PlainConsumer.class)).singleElement()
            .satisfies((p) -> assertThat(p).contains("code outside any module").contains("AlphaService"));
    }
}
