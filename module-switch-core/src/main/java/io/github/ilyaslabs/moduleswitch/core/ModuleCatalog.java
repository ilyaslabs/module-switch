package io.github.ilyaslabs.moduleswitch.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Every module found on the classpath, with its API and implementation packages and API
 * interfaces.
 */
public final class ModuleCatalog {

    private final Map<String, ModuleInfo> modules;

    ModuleCatalog(Map<String, ModuleInfo> modules) {
        this.modules = Collections.unmodifiableMap(new TreeMap<>(modules));
    }

    public static ModuleCatalog empty() {
        return new ModuleCatalog(Map.of());
    }

    public Set<String> names() {
        return this.modules.keySet();
    }

    public boolean contains(String module) {
        return this.modules.containsKey(module);
    }

    public Optional<ModuleInfo> module(String module) {
        return Optional.ofNullable(this.modules.get(module));
    }

    public Collection<ModuleInfo> modules() {
        return this.modules.values();
    }

    /** The API interfaces of a module; empty when the module is unknown. */
    public List<Class<?>> apiInterfaces(String module) {
        ModuleInfo info = this.modules.get(module);
        return (info != null) ? info.apiInterfaces() : List.of();
    }

    /**
     * One discovered module.
     *
     * @param name the module name
     * @param apiPackages packages declared with {@code @ModuleApi}
     * @param implPackages packages declared with {@code @Module}
     * @param apiInterfaces interfaces in the API packages that callers inject
     */
    public record ModuleInfo(String name, Set<String> apiPackages, Set<String> implPackages,
            List<Class<?>> apiInterfaces) {

        public ModuleInfo {
            apiPackages = Collections.unmodifiableSet(new TreeSet<>(apiPackages));
            implPackages = Collections.unmodifiableSet(new TreeSet<>(implPackages));
            List<Class<?>> sorted = new ArrayList<>(apiInterfaces);
            sorted.sort((a, b) -> a.getName().compareTo(b.getName()));
            apiInterfaces = List.copyOf(sorted);
        }
    }
}
