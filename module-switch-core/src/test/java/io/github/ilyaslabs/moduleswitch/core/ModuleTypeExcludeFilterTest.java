package io.github.ilyaslabs.moduleswitch.core;

import io.github.ilyaslabs.moduleswitch.core.fixtures.alpha.api.AlphaApi;
import io.github.ilyaslabs.moduleswitch.core.fixtures.alpha.impl.AlphaService;
import io.github.ilyaslabs.moduleswitch.core.fixtures.alpha.impl.deep.AlphaHelper;
import io.github.ilyaslabs.moduleswitch.core.fixtures.plain.PlainConsumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;

import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleTypeExcludeFilterTest {

    private final MetadataReaderFactory readers = new SimpleMetadataReaderFactory();

    private boolean excluded(ModuleMode alphaMode, Class<?> type) throws Exception {
        ModuleSwitchProperties properties = new ModuleSwitchProperties();
        ModuleSwitchProperties.ModuleProperties alpha = new ModuleSwitchProperties.ModuleProperties();
        alpha.setMode(alphaMode);
        properties.getModules().put("alpha", alpha);
        ModuleTypeExcludeFilter filter = new ModuleTypeExcludeFilter(properties, new ModulePackageResolver());
        return filter.match(this.readers.getMetadataReader(type.getName()), this.readers);
    }

    @Test
    void localModuleClassesAreKept() throws Exception {
        assertThat(excluded(ModuleMode.LOCAL, AlphaService.class)).isFalse();
        assertThat(excluded(ModuleMode.LOCAL, AlphaHelper.class)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = ModuleMode.class, names = { "HTTP", "OFF" })
    void nonLocalModuleClassesAreSkipped(ModuleMode mode) throws Exception {
        assertThat(excluded(mode, AlphaService.class)).isTrue();
        assertThat(excluded(mode, AlphaHelper.class)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(ModuleMode.class)
    void apiAndNonModuleClassesAreAlwaysKept(ModuleMode mode) throws Exception {
        assertThat(excluded(mode, AlphaApi.class)).isFalse();
        assertThat(excluded(mode, PlainConsumer.class)).isFalse();
    }

    @Test
    void unconfiguredModulesAreOff() throws Exception {
        ModuleTypeExcludeFilter filter = new ModuleTypeExcludeFilter(new ModuleSwitchProperties(),
                new ModulePackageResolver());

        assertThat(filter.match(this.readers.getMetadataReader(AlphaService.class.getName()), this.readers)).isTrue();
    }
}
