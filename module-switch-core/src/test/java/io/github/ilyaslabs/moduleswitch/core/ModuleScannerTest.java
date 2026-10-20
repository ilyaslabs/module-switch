package io.github.ilyaslabs.moduleswitch.core;

import java.util.List;

import io.github.ilyaslabs.moduleswitch.core.fixtures.alpha.api.AlphaApi;
import io.github.ilyaslabs.moduleswitch.core.fixtures.alpha.api.Marker;
import io.github.ilyaslabs.moduleswitch.core.fixtures.alpha.api.Shape;
import io.github.ilyaslabs.moduleswitch.core.fixtures.beta.api.BetaApi;
import org.junit.jupiter.api.Test;

import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleScannerTest {

    private static final String FIXTURES = "io.github.ilyaslabs.moduleswitch.core.fixtures";

    private final DefaultResourceLoader resourceLoader = new DefaultResourceLoader();

    private final ModuleScanner scanner = new ModuleScanner(new ModulePackageResolver(this.resourceLoader),
            this.resourceLoader);

    @Test
    void findsModulesAndTheirPackages() {
        ModuleCatalog catalog = this.scanner.scan(List.of(FIXTURES));

        assertThat(catalog.names()).containsExactly("alpha", "beta");
        assertThat(catalog.module("alpha")).hasValueSatisfying((alpha) -> {
            assertThat(alpha.apiPackages()).containsExactly(FIXTURES + ".alpha.api");
            assertThat(alpha.implPackages()).containsExactly(FIXTURES + ".alpha.impl");
        });
    }

    @Test
    void findsApiInterfacesButNotSealedOrMarkerInterfaces() {
        ModuleCatalog catalog = this.scanner.scan(List.of(FIXTURES));

        assertThat(catalog.apiInterfaces("alpha")).containsExactly(AlphaApi.class)
            .doesNotContain(Shape.class, Marker.class);
        assertThat(catalog.apiInterfaces("beta")).containsExactly(BetaApi.class);
        assertThat(catalog.apiInterfaces("unknown")).isEmpty();
    }

    @Test
    void overlappingBasePackagesDoNotDuplicate() {
        ModuleCatalog catalog = this.scanner.scan(List.of(FIXTURES, FIXTURES + ".alpha"));

        assertThat(catalog.apiInterfaces("alpha")).containsExactly(AlphaApi.class);
    }

    @Test
    void emptyWhenNothingDeclared() {
        assertThat(this.scanner.scan(List.of("io.github.ilyaslabs.moduleswitch.core.fixtures.plain")).names()).isEmpty();
        assertThat(this.scanner.scan(List.of()).names()).isEmpty();
    }

    @Test
    void listsClassNamesUnderAPackage() {
        assertThat(this.scanner.classNamesUnder(FIXTURES + ".alpha.impl"))
            .contains(FIXTURES + ".alpha.impl.AlphaService", FIXTURES + ".alpha.impl.deep.AlphaHelper")
            .noneMatch((name) -> name.endsWith("package-info"));
    }
}
