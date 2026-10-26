package io.github.ilyaslabs.moduleswitch.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.MethodMetadata;
import org.springframework.util.ClassUtils;

/**
 * Runs once all bean definitions are known: discovers modules, validates configuration and
 * structure, and registers the bean callers inject for each module API.
 * <p>
 * Startup fails, listing every problem, when:
 * <ul>
 * <li>configuration names an unknown module, an http module has no url, or an exposed module is
 * not local;</li>
 * <li>an API interface overloads a method or lacks {@code @Param};</li>
 * <li>a bean from a module that is not local was registered anyway, for example by a custom
 * {@code @ComponentScan}, {@code @Import} or {@code @Enable...Repositories};</li>
 * <li>a bean uses another module's implementation classes instead of its API.</li>
 * </ul>
 */
public class ModuleSwitchRegistrar implements BeanDefinitionRegistryPostProcessor, EnvironmentAware,
        ResourceLoaderAware, Ordered {

    private static final Log logger = LogFactory.getLog(ModuleSwitchRegistrar.class);

    private static final String LIBRARY_PACKAGE = "io.github.ilyaslabs.moduleswitch.";

    private Environment environment;

    private ResourceLoader resourceLoader = new DefaultResourceLoader();

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        ConfigurableListableBeanFactory beanFactory = (ConfigurableListableBeanFactory) registry;
        ModuleSwitchProperties properties = ModuleSwitchProperties.bind(this.environment);
        ModulePackageResolver resolver = new ModulePackageResolver(this.resourceLoader);
        Set<String> basePackages = basePackages(beanFactory, properties);
        ModuleCatalog catalog = new ModuleScanner(resolver, this.resourceLoader).scan(basePackages);

        List<String> problems = new ArrayList<>();
        problems.addAll(checkConfiguration(properties, catalog));
        problems.addAll(checkApis(catalog));
        problems.addAll(checkBypassedBeans(registry, beanFactory, properties, resolver));
        problems.addAll(checkBoundaries(registry, beanFactory, resolver, basePackages));
        if (!problems.isEmpty()) {
            throw new ModuleSwitchConfigurationException(problems);
        }

        ModuleSwitchRuntime runtime = new ModuleSwitchRuntime(properties, catalog, beanFactory);
        // A bean definition rather than a manual singleton, so its startup checks run.
        registry.registerBeanDefinition(ModuleSwitchRuntime.BEAN_NAME,
                new RootBeanDefinition(ModuleSwitchRuntime.class, () -> runtime));
        registerApiBeans(registry, runtime);
        logModes(properties, catalog);
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
    }

    private static Set<String> basePackages(ConfigurableListableBeanFactory beanFactory,
            ModuleSwitchProperties properties) {
        Set<String> packages = new LinkedHashSet<>();
        if (AutoConfigurationPackages.has(beanFactory)) {
            packages.addAll(AutoConfigurationPackages.get(beanFactory));
        }
        packages.addAll(properties.getBasePackages());
        return packages;
    }

    static List<String> checkConfiguration(ModuleSwitchProperties properties, ModuleCatalog catalog) {
        List<String> problems = new ArrayList<>();
        String known = catalog.names().isEmpty() ? "none found" : String.join(", ", catalog.names());
        properties.getModules().forEach((name, settings) -> {
            if (!catalog.contains(name)) {
                problems.add("Unknown module '" + name + "' in " + ModuleSwitchProperties.PREFIX
                        + ".modules. Known modules: " + known + ".");
            }
            if (settings.getMode() == ModuleMode.HTTP && settings.getUrl() == null) {
                problems.add("Module '" + name + "' is http but has no url. Set " + ModuleSwitchProperties.PREFIX
                        + ".modules." + name + ".url.");
            }
            if (settings.getRetry().getMaxAttempts() < 1) {
                problems.add("Module '" + name + "' has retry.max-attempts below 1.");
            }
        });
        ModuleSwitchProperties.Expose expose = properties.getExpose();
        for (String name : expose.getModules()) {
            if (!catalog.contains(name)) {
                problems.add("Unknown module '" + name + "' in " + ModuleSwitchProperties.PREFIX
                        + ".expose.modules. Known modules: " + known + ".");
            }
            else if (properties.modeOf(name) != ModuleMode.LOCAL) {
                problems.add("Module '" + name + "' is listed in " + ModuleSwitchProperties.PREFIX
                        + ".expose.modules but is " + mode(properties.modeOf(name))
                        + ". Only local modules can be exposed.");
            }
        }
        if (!expose.getModules().isEmpty() && expose.getPort() == null) {
            problems.add(ModuleSwitchProperties.PREFIX + ".expose.modules is set but "
                    + ModuleSwitchProperties.PREFIX + ".expose.port is not.");
        }
        return problems;
    }

    static List<String> checkApis(ModuleCatalog catalog) {
        List<String> problems = new ArrayList<>();
        for (ModuleCatalog.ModuleInfo module : catalog.modules()) {
            Map<String, List<String>> bySimpleName = new TreeMap<>();
            for (Class<?> api : module.apiInterfaces()) {
                problems.addAll(ApiContract.validate(api));
                bySimpleName.computeIfAbsent(api.getSimpleName(), (k) -> new ArrayList<>()).add(api.getName());
            }
            bySimpleName.forEach((simpleName, names) -> {
                if (names.size() > 1) {
                    problems.add("Module '" + module.name() + "' has several API interfaces named " + simpleName
                            + " " + names + ". The simple name is part of the endpoint path, so it must be unique.");
                }
            });
        }
        return problems;
    }

    private static List<String> checkBypassedBeans(BeanDefinitionRegistry registry,
            ConfigurableListableBeanFactory beanFactory, ModuleSwitchProperties properties,
            ModulePackageResolver resolver) {
        List<String> problems = new ArrayList<>();
        for (String beanName : registry.getBeanDefinitionNames()) {
            BeanDefinition definition = registry.getBeanDefinition(beanName);
            for (String className : classNamesOf(definition, registry)) {
                Optional<ModuleMembership> membership = resolver.forClassName(className);
                if (membership.isEmpty() || !membership.get().isImpl()) {
                    continue;
                }
                ModuleMode mode = properties.modeOf(membership.get().module());
                if (mode != ModuleMode.LOCAL) {
                    String source = Optional.ofNullable(definition.getResourceDescription()).orElse("unknown source");
                    problems.add("Bean '" + beanName + "' (" + className + ") belongs to module '"
                            + membership.get().module() + "', which is " + mode(mode)
                            + ", but was registered anyway (" + source + "). A custom @ComponentScan, @Import, "
                            + "@Bean method or @Enable...Repositories outside the module's own packages bypasses "
                            + "Module-Switch. Move it into the module's packages or remove it.");
                    break;
                }
            }
        }
        return problems;
    }

    private static Set<String> classNamesOf(BeanDefinition definition, BeanDefinitionRegistry registry) {
        Set<String> names = new LinkedHashSet<>();
        if (definition.getBeanClassName() != null) {
            names.add(definition.getBeanClassName());
        }
        if (definition instanceof AnnotatedBeanDefinition annotated) {
            MethodMetadata factoryMethod = annotated.getFactoryMethodMetadata();
            if (factoryMethod != null) {
                names.add(factoryMethod.getReturnTypeName());
                names.add(factoryMethod.getDeclaringClassName());
            }
        }
        String factoryBean = definition.getFactoryBeanName();
        if (factoryBean != null && registry.containsBeanDefinition(factoryBean)) {
            String factoryClass = registry.getBeanDefinition(factoryBean).getBeanClassName();
            if (factoryClass != null) {
                names.add(factoryClass);
            }
        }
        ConstructorArgumentValues arguments = definition.getConstructorArgumentValues();
        List<ConstructorArgumentValues.ValueHolder> values = new ArrayList<>(arguments.getGenericArgumentValues());
        values.addAll(arguments.getIndexedArgumentValues().values());
        for (ConstructorArgumentValues.ValueHolder holder : values) {
            Object value = holder.getValue();
            if (value instanceof Class<?> type) {
                names.add(type.getName());
            }
            else if (value instanceof String text && looksLikeClassName(text)) {
                names.add(text);
            }
        }
        return names;
    }

    private static boolean looksLikeClassName(String text) {
        return text.indexOf('.') > 0 && !text.contains(" ") && !text.contains("/")
                && Character.isUpperCase(text.charAt(text.lastIndexOf('.') + 1));
    }

    private static List<String> checkBoundaries(BeanDefinitionRegistry registry,
            ConfigurableListableBeanFactory beanFactory, ModulePackageResolver resolver,
            Collection<String> basePackages) {
        BoundaryChecker checker = new BoundaryChecker(resolver);
        Set<String> problems = new LinkedHashSet<>();
        ClassLoader classLoader = beanFactory.getBeanClassLoader();
        for (String beanName : registry.getBeanDefinitionNames()) {
            String className = registry.getBeanDefinition(beanName).getBeanClassName();
            if (className == null || !isUserClass(className, basePackages)) {
                continue;
            }
            try {
                problems.addAll(checker.check(ClassUtils.forName(className, classLoader)));
            }
            catch (ClassNotFoundException | LinkageError ex) {
                // Not loadable here; Spring reports it when creating the bean.
            }
        }
        return new ArrayList<>(problems);
    }

    private static boolean isUserClass(String className, Collection<String> basePackages) {
        if (className.startsWith(LIBRARY_PACKAGE + "core.")
                || className.startsWith(LIBRARY_PACKAGE + "autoconfigure.")) {
            return false;
        }
        for (String base : basePackages) {
            if (className.startsWith(base + ".")) {
                return true;
            }
        }
        return false;
    }

    private static void registerApiBeans(BeanDefinitionRegistry registry, ModuleSwitchRuntime runtime) {
        for (ModuleCatalog.ModuleInfo module : runtime.catalog().modules()) {
            ModuleMode mode = runtime.modeOf(module.name());
            if (mode == ModuleMode.OFF) {
                continue;
            }
            for (Class<?> api : module.apiInterfaces()) {
                RootBeanDefinition definition = new RootBeanDefinition(api);
                definition.setTargetType(api);
                definition.setInstanceSupplier(() -> runtime.createProxy(module.name(), api));
                // The local implementation also implements the API; callers get the wrapper.
                definition.setPrimary(true);
                registry.registerBeanDefinition(ModuleSwitchRuntime.apiBeanName(api), definition);
            }
        }
    }

    private static void logModes(ModuleSwitchProperties properties, ModuleCatalog catalog) {
        if (catalog.names().isEmpty()) {
            logger.warn("Module-Switch: no modules found. Declare modules with @Module and @ModuleApi in package-info.java.");
            return;
        }
        String summary = catalog.names()
            .stream()
            .map((name) -> name + "=" + mode(properties.modeOf(name)))
            .collect(Collectors.joining(", "));
        logger.info("Module-Switch modules: " + summary);
        boolean anyEnabled = catalog.names().stream().anyMatch((name) -> properties.modeOf(name) != ModuleMode.OFF);
        if (!anyEnabled) {
            logger.warn("Module-Switch: no modules enabled. Every module is off; set "
                    + ModuleSwitchProperties.PREFIX + ".modules.<name>.mode to local or http.");
        }
        ModuleSwitchProperties.Expose expose = properties.getExpose();
        if (expose.getPort() != null && !expose.getModules().isEmpty()) {
            logger.info("Module-Switch exposing " + expose.getModules() + " on internal port " + expose.getPort());
        }
    }

    private static String mode(ModuleMode mode) {
        return mode.name().toLowerCase(Locale.ROOT);
    }
}
