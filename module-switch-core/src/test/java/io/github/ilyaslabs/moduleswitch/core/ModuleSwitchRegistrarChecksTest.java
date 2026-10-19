package io.github.ilyaslabs.moduleswitch.core;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleSwitchRegistrarChecksTest {

    private final ModuleCatalog catalog = new ModuleScanner(new ModulePackageResolver(), new DefaultResourceLoader())
        .scan(List.of("io.github.ilyaslabs.moduleswitch.core.fixtures"));

    private static ModuleSwitchProperties.ModuleProperties module(ModuleMode mode) {
        ModuleSwitchProperties.ModuleProperties settings = new ModuleSwitchProperties.ModuleProperties();
        settings.setMode(mode);
        return settings;
    }

    @Test
    void validConfigurationHasNoProblems() {
        ModuleSwitchProperties properties = new ModuleSwitchProperties();
        properties.getModules().put("alpha", module(ModuleMode.LOCAL));
        ModuleSwitchProperties.ModuleProperties beta = module(ModuleMode.HTTP);
        beta.setUrl(URI.create("http://beta:8081"));
        properties.getModules().put("beta", beta);
        properties.getExpose().setPort(8081);
        properties.getExpose().setModules(List.of("alpha"));

        assertThat(ModuleSwitchRegistrar.checkConfiguration(properties, this.catalog)).isEmpty();
    }

    @Test
    void emptyConfigurationIsValid() {
        assertThat(ModuleSwitchRegistrar.checkConfiguration(new ModuleSwitchProperties(), this.catalog)).isEmpty();
    }

    @Test
    void unknownModuleNamesListTheKnownOnes() {
        ModuleSwitchProperties properties = new ModuleSwitchProperties();
        properties.getModules().put("alfa", module(ModuleMode.LOCAL));

        assertThat(ModuleSwitchRegistrar.checkConfiguration(properties, this.catalog)).singleElement()
            .isEqualTo("Unknown module 'alfa' in module-switch.modules. Known modules: alpha, beta.");
    }

    @Test
    void httpNeedsUrl() {
        ModuleSwitchProperties properties = new ModuleSwitchProperties();
        properties.getModules().put("beta", module(ModuleMode.HTTP));

        assertThat(ModuleSwitchRegistrar.checkConfiguration(properties, this.catalog)).singleElement()
            .asString()
            .contains("'beta' is http but has no url");
    }

    @Test
    void retryAttemptsMustBePositive() {
        ModuleSwitchProperties properties = new ModuleSwitchProperties();
        ModuleSwitchProperties.ModuleProperties alpha = module(ModuleMode.LOCAL);
        alpha.getRetry().setMaxAttempts(0);
        properties.getModules().put("alpha", alpha);

        assertThat(ModuleSwitchRegistrar.checkConfiguration(properties, this.catalog)).singleElement()
            .asString()
            .contains("max-attempts");
    }

    @Test
    void onlyLocalModulesCanBeExposed() {
        ModuleSwitchProperties properties = new ModuleSwitchProperties();
        properties.getExpose().setPort(8081);
        properties.getExpose().setModules(List.of("alpha", "gamma"));

        assertThat(ModuleSwitchRegistrar.checkConfiguration(properties, this.catalog)).containsExactly(
                "Module 'alpha' is listed in module-switch.expose.modules but is off. Only local modules can be exposed.",
                "Unknown module 'gamma' in module-switch.expose.modules. Known modules: alpha, beta.");
    }

    @Test
    void exposedModulesNeedAPort() {
        ModuleSwitchProperties properties = new ModuleSwitchProperties();
        properties.getModules().put("alpha", module(ModuleMode.LOCAL));
        properties.getExpose().setModules(List.of("alpha"));

        assertThat(ModuleSwitchRegistrar.checkConfiguration(properties, this.catalog)).singleElement()
            .asString()
            .contains("expose.port is not");
    }

    @Test
    void apiRulesAreChecked() {
        ModuleCatalog bad = new ModuleScanner(new ModulePackageResolver(), new DefaultResourceLoader())
            .scan(List.of("io.github.ilyaslabs.moduleswitch.core.badapi"));

        assertThat(ModuleSwitchRegistrar.checkApis(bad)).hasSize(4);
        assertThat(ModuleSwitchRegistrar.checkApis(this.catalog)).isEmpty();
    }
}
