package io.github.ilyaslabs.moduleswitch.core;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.github.ilyaslabs.moduleswitch.Module;
import io.github.ilyaslabs.moduleswitch.ModuleApi;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.util.ClassUtils;

/**
 * Finds the module a class belongs to by reading {@code package-info} annotations of its package
 * and its parent packages.
 * <p>
 * Reads class metadata only, so resolving a class never loads it. Results are cached per package.
 */
public class ModulePackageResolver {

    private static final String PACKAGE_INFO = "package-info";

    private final MetadataReaderFactory metadataReaderFactory;

    private final Map<String, Optional<ModuleMembership>> cache = new ConcurrentHashMap<>();

    public ModulePackageResolver() {
        this(new DefaultResourceLoader());
    }

    public ModulePackageResolver(ResourceLoader resourceLoader) {
        this.metadataReaderFactory = new SimpleMetadataReaderFactory(resourceLoader);
    }

    /** The membership of a class, by its fully qualified name. */
    public Optional<ModuleMembership> forClassName(String className) {
        if (className == null) {
            return Optional.empty();
        }
        String outer = className.contains("$") ? className.substring(0, className.indexOf('$')) : className;
        return forPackage(ClassUtils.getPackageName(outer));
    }

    /** The membership of a class. */
    public Optional<ModuleMembership> forClass(Class<?> type) {
        Class<?> component = type;
        while (component.isArray()) {
            component = component.getComponentType();
        }
        return component.isPrimitive() ? Optional.empty() : forClassName(component.getName());
    }

    /** The membership of a package: the nearest declaration in it or a parent package. */
    public Optional<ModuleMembership> forPackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return Optional.empty();
        }
        Optional<ModuleMembership> cached = this.cache.get(packageName);
        if (cached != null) {
            return cached;
        }
        Optional<ModuleMembership> own = declaredOn(packageName);
        Optional<ModuleMembership> result = own.isPresent() ? own : forPackage(parentOf(packageName));
        this.cache.put(packageName, result);
        return result;
    }

    /** The membership declared directly on a package's {@code package-info}, if any. */
    public Optional<ModuleMembership> declaredOn(String packageName) {
        AnnotationMetadata metadata = readPackageInfo(packageName);
        if (metadata == null) {
            return Optional.empty();
        }
        String impl = attribute(metadata, Module.class);
        String api = attribute(metadata, ModuleApi.class);
        if (impl != null && api != null) {
            throw new ModuleSwitchConfigurationException(java.util.List.of("Package '" + packageName
                    + "' declares both @Module and @ModuleApi. A package is either a module's API or its implementation."));
        }
        if (impl != null) {
            return Optional.of(new ModuleMembership(impl, ModuleMembership.Kind.IMPL, packageName));
        }
        if (api != null) {
            return Optional.of(new ModuleMembership(api, ModuleMembership.Kind.API, packageName));
        }
        return Optional.empty();
    }

    private AnnotationMetadata readPackageInfo(String packageName) {
        try {
            MetadataReader reader = this.metadataReaderFactory.getMetadataReader(packageName + "." + PACKAGE_INFO);
            return reader.getAnnotationMetadata();
        }
        catch (FileNotFoundException ex) {
            return null;
        }
        catch (IOException ex) {
            throw new IllegalStateException("Could not read package-info of " + packageName, ex);
        }
    }

    private static String attribute(AnnotationMetadata metadata, Class<?> annotation) {
        Map<String, Object> attributes = metadata.getAnnotationAttributes(annotation.getName());
        if (attributes == null) {
            return null;
        }
        String value = ((String) attributes.get("value")).trim();
        if (value.isEmpty()) {
            throw new ModuleSwitchConfigurationException(java.util.List.of("@" + annotation.getSimpleName()
                    + " on package '" + metadata.getClassName().replace("." + PACKAGE_INFO, "")
                    + "' has an empty module name."));
        }
        return value;
    }

    private static String parentOf(String packageName) {
        int dot = packageName.lastIndexOf('.');
        return (dot > 0) ? packageName.substring(0, dot) : null;
    }
}
