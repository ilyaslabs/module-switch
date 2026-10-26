package io.github.ilyaslabs.moduleswitch.test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.github.ilyaslabs.moduleswitch.core.ApiContract;
import io.github.ilyaslabs.moduleswitch.core.BoundaryChecker;
import io.github.ilyaslabs.moduleswitch.core.ModuleCatalog;
import io.github.ilyaslabs.moduleswitch.core.ModuleMembership;
import io.github.ilyaslabs.moduleswitch.core.ModulePackageResolver;
import io.github.ilyaslabs.moduleswitch.core.ModuleScanner;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.util.ClassUtils;

/**
 * Checks an application's module structure in a plain unit test, without starting it.
 * <pre>{@code
 * @Test
 * void modulesFollowTheRules() {
 *     ModuleRules.verify("com.example.rms");
 * }
 * }</pre>
 * Verifies that:
 * <ul>
 * <li>every API interface follows the contract rules (no overloads, {@code @Param} on every
 * parameter);</li>
 * <li>no class uses another module's implementation classes, only its API;</li>
 * <li>API packages only use API types.</li>
 * </ul>
 */
public final class ModuleRules {

    private final List<String> basePackages;

    private ModuleRules(List<String> basePackages) {
        this.basePackages = List.copyOf(basePackages);
    }

    /** Rules for the modules under the given base packages. */
    public static ModuleRules of(String... basePackages) {
        if (basePackages.length == 0) {
            throw new IllegalArgumentException("Give at least one base package");
        }
        return new ModuleRules(List.of(basePackages));
    }

    /** Fails with an {@link AssertionError} listing every violation. */
    public static void verify(String... basePackages) {
        of(basePackages).check();
    }

    /** Fails with an {@link AssertionError} listing every violation. */
    public void check() {
        List<String> violations = violations();
        if (!violations.isEmpty()) {
            StringBuilder message = new StringBuilder("Module rules violated (").append(violations.size()).append("):");
            violations.forEach((violation) -> message.append(System.lineSeparator()).append(" - ").append(violation));
            throw new AssertionError(message.toString());
        }
    }

    /** Every violation found; empty when the structure is valid. */
    public List<String> violations() {
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        ModulePackageResolver resolver = new ModulePackageResolver(resourceLoader);
        ModuleScanner scanner = new ModuleScanner(resolver, resourceLoader);
        ModuleCatalog catalog = scanner.scan(this.basePackages);
        Set<String> violations = new LinkedHashSet<>();
        if (catalog.names().isEmpty()) {
            violations.add("No modules found under " + this.basePackages
                    + ". Declare them with @Module and @ModuleApi in package-info.java.");
        }
        for (ModuleCatalog.ModuleInfo module : catalog.modules()) {
            for (Class<?> api : module.apiInterfaces()) {
                violations.addAll(ApiContract.validate(api));
            }
            if (module.apiPackages().isEmpty()) {
                violations.add("Module '" + module.name() + "' has no @ModuleApi package. Other modules can only "
                        + "reach it through its API.");
            }
        }
        BoundaryChecker checker = new BoundaryChecker(resolver);
        ClassLoader classLoader = ClassUtils.getDefaultClassLoader();
        for (String base : this.basePackages) {
            for (String className : scanner.classNamesUnder(base)) {
                Optional<ModuleMembership> membership = resolver.forClassName(className);
                if (membership.isEmpty()) {
                    continue;
                }
                try {
                    violations.addAll(checker.check(ClassUtils.forName(className, classLoader)));
                }
                catch (ClassNotFoundException | LinkageError ex) {
                    violations.add("Could not load " + className + ": " + ex);
                }
            }
        }
        return new ArrayList<>(violations);
    }
}
