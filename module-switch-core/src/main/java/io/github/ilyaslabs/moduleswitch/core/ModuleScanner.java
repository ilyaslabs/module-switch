package io.github.ilyaslabs.moduleswitch.core;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.ClassUtils;

/**
 * Discovers modules from {@code package-info} declarations under a set of base packages, and the
 * API interfaces in each module's API packages.
 * <p>
 * An API interface is a top-level, non-sealed interface with at least one abstract method. Sealed
 * interfaces are treated as DTO hierarchies, not as module APIs.
 */
public class ModuleScanner {

    private final ModulePackageResolver resolver;

    private final PathMatchingResourcePatternResolver resources;

    private final MetadataReaderFactory metadataReaderFactory;

    private final ClassLoader classLoader;

    public ModuleScanner(ModulePackageResolver resolver, ResourceLoader resourceLoader) {
        this.resolver = resolver;
        this.resources = new PathMatchingResourcePatternResolver(resourceLoader);
        this.metadataReaderFactory = new CachingMetadataReaderFactory(resourceLoader);
        ClassLoader loader = resourceLoader.getClassLoader();
        this.classLoader = (loader != null) ? loader : ClassUtils.getDefaultClassLoader();
    }

    public ModuleCatalog scan(Collection<String> basePackages) {
        Map<String, Set<String>> apiPackages = new LinkedHashMap<>();
        Map<String, Set<String>> implPackages = new LinkedHashMap<>();
        for (String packageName : declaringPackages(basePackages)) {
            this.resolver.declaredOn(packageName).ifPresent((membership) -> {
                Map<String, Set<String>> target = membership.isApi() ? apiPackages : implPackages;
                target.computeIfAbsent(membership.module(), (k) -> new LinkedHashSet<>()).add(packageName);
            });
        }
        Set<String> names = new LinkedHashSet<>(apiPackages.keySet());
        names.addAll(implPackages.keySet());
        Map<String, ModuleCatalog.ModuleInfo> modules = new LinkedHashMap<>();
        for (String name : names) {
            Set<String> apis = apiPackages.getOrDefault(name, Set.of());
            List<Class<?>> interfaces = new ArrayList<>();
            for (String apiPackage : apis) {
                interfaces.addAll(apiInterfaces(name, apiPackage));
            }
            modules.put(name, new ModuleCatalog.ModuleInfo(name, apis, implPackages.getOrDefault(name, Set.of()),
                    interfaces.stream().distinct().toList()));
        }
        return new ModuleCatalog(modules);
    }

    /** Every class (top-level and nested) in a package tree, by name. */
    public List<String> classNamesUnder(String packageName) {
        List<String> names = new ArrayList<>();
        for (Resource resource : find(packageName, "**/*.class")) {
            try {
                ClassMetadata metadata = this.metadataReaderFactory.getMetadataReader(resource).getClassMetadata();
                if (!metadata.getClassName().endsWith(".package-info") && !metadata.getClassName().equals("module-info")) {
                    names.add(metadata.getClassName());
                }
            }
            catch (IOException ex) {
                throw new IllegalStateException("Could not read " + resource, ex);
            }
        }
        return names;
    }

    private Set<String> declaringPackages(Collection<String> basePackages) {
        Set<String> packages = new LinkedHashSet<>();
        for (String base : basePackages) {
            for (Resource resource : find(base, "**/package-info.class")) {
                try {
                    String className = this.metadataReaderFactory.getMetadataReader(resource)
                        .getClassMetadata()
                        .getClassName();
                    packages.add(ClassUtils.getPackageName(className));
                }
                catch (IOException ex) {
                    throw new IllegalStateException("Could not read " + resource, ex);
                }
            }
        }
        return packages;
    }

    private List<Class<?>> apiInterfaces(String module, String apiPackage) {
        List<Class<?>> interfaces = new ArrayList<>();
        for (Resource resource : find(apiPackage, "**/*.class")) {
            try {
                ClassMetadata metadata = this.metadataReaderFactory.getMetadataReader(resource).getClassMetadata();
                if (!metadata.isInterface() || metadata.isAnnotation() || metadata.hasEnclosingClass()) {
                    continue;
                }
                Optional<ModuleMembership> membership = this.resolver.forClassName(metadata.getClassName());
                if (membership.isEmpty() || !membership.get().isApi() || !membership.get().module().equals(module)) {
                    continue;
                }
                Class<?> type = ClassUtils.forName(metadata.getClassName(), this.classLoader);
                if (isApiInterface(type)) {
                    interfaces.add(type);
                }
            }
            catch (IOException | ClassNotFoundException | LinkageError ex) {
                throw new IllegalStateException("Could not read " + resource, ex);
            }
        }
        return interfaces;
    }

    static boolean isApiInterface(Class<?> type) {
        if (!type.isInterface() || type.isAnnotation() || type.isSealed()) {
            return false;
        }
        for (Method method : type.getMethods()) {
            if (Modifier.isAbstract(method.getModifiers()) && method.getDeclaringClass() != Object.class) {
                return true;
            }
        }
        return false;
    }

    private Resource[] find(String packageName, String pattern) {
        String location = "classpath*:" + ClassUtils.convertClassNameToResourcePath(packageName) + "/" + pattern;
        try {
            return this.resources.getResources(location);
        }
        catch (IOException ex) {
            throw new IllegalStateException("Could not scan " + location, ex);
        }
    }
}
