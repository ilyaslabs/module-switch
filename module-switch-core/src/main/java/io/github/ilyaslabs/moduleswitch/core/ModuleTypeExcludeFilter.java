package io.github.ilyaslabs.moduleswitch.core;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

/**
 * Skips classes of modules that are not {@code local} during {@code @SpringBootApplication}'s
 * component scan. Works on class metadata only, so skipped classes are never loaded.
 */
public class ModuleTypeExcludeFilter extends TypeExcludeFilter {

    private final ModuleSwitchProperties properties;

    private final ModulePackageResolver resolver;

    public ModuleTypeExcludeFilter(ModuleSwitchProperties properties, ModulePackageResolver resolver) {
        this.properties = properties;
        this.resolver = resolver;
    }

    @Override
    public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory)
            throws IOException {
        Optional<ModuleMembership> membership = this.resolver.forClassName(metadataReader.getClassMetadata().getClassName());
        return membership.isPresent() && membership.get().isImpl()
                && this.properties.modeOf(membership.get().module()) != ModuleMode.LOCAL;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ModuleTypeExcludeFilter that
                && Objects.equals(this.properties.getModules().keySet(), that.properties.getModules().keySet());
    }

    @Override
    public int hashCode() {
        return Objects.hash(ModuleTypeExcludeFilter.class, this.properties.getModules().keySet());
    }
}
