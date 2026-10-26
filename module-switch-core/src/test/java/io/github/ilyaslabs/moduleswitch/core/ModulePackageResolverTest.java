package io.github.ilyaslabs.moduleswitch.core;

import io.github.ilyaslabs.moduleswitch.core.fixtures.alpha.api.AlphaApi;
import io.github.ilyaslabs.moduleswitch.core.fixtures.alpha.impl.AlphaService;
import io.github.ilyaslabs.moduleswitch.core.fixtures.alpha.impl.deep.AlphaHelper;
import io.github.ilyaslabs.moduleswitch.core.fixtures.plain.PlainConsumer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModulePackageResolverTest {

    private final ModulePackageResolver resolver = new ModulePackageResolver();

    @Test
    void resolvesImplementationPackage() {
        assertThat(this.resolver.forClass(AlphaService.class)).hasValueSatisfying((membership) -> {
            assertThat(membership.module()).isEqualTo("alpha");
            assertThat(membership.isImpl()).isTrue();
            assertThat(membership.rootPackage()).isEqualTo(AlphaService.class.getPackageName());
        });
    }

    @Test
    void resolvesApiPackage() {
        assertThat(this.resolver.forClass(AlphaApi.class))
            .hasValueSatisfying((membership) -> assertThat(membership.isApi()).isTrue());
    }

    @Test
    void subpackagesInheritFromTheNearestDeclaration() {
        assertThat(this.resolver.forClass(AlphaHelper.class)).hasValueSatisfying((membership) -> {
            assertThat(membership.module()).isEqualTo("alpha");
            assertThat(membership.rootPackage()).isEqualTo(AlphaService.class.getPackageName());
        });
    }

    @Test
    void nestedClassesBelongToTheirOuterClassPackage() {
        assertThat(this.resolver.forClassName(AlphaService.class.getName() + "$Inner")).isPresent();
    }

    @Test
    void classesOutsideModulesHaveNoMembership() {
        assertThat(this.resolver.forClass(PlainConsumer.class)).isEmpty();
        assertThat(this.resolver.forClass(String.class)).isEmpty();
        assertThat(this.resolver.forClass(int.class)).isEmpty();
        assertThat(this.resolver.forClassName(null)).isEmpty();
    }

    @Test
    void arraysResolveTheirComponentType() {
        assertThat(this.resolver.forClass(AlphaService[].class)).isPresent();
    }

    @Test
    void packageWithBothAnnotationsIsRejected() {
        assertThatThrownBy(() -> this.resolver.forPackage("io.github.ilyaslabs.moduleswitch.core.conflict"))
            .isInstanceOf(ModuleSwitchConfigurationException.class)
            .hasMessageContaining("declares both @Module and @ModuleApi");
    }

    @Test
    void cachesResults() {
        assertThat(this.resolver.forClass(AlphaService.class)).isSameAs(this.resolver.forClass(AlphaService.class));
    }
}
