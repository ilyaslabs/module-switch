package io.github.ilyaslabs.moduleswitch.autoconfigure;

import io.github.ilyaslabs.moduleswitch.core.ModulePackageResolver;
import io.github.ilyaslabs.moduleswitch.core.ModuleSwitchProperties;
import io.github.ilyaslabs.moduleswitch.core.ModuleTypeExcludeFilter;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Registers {@link ModuleTypeExcludeFilter} before {@code @SpringBootApplication}'s component scan
 * runs, so classes of modules that aren't local are never loaded.
 */
public class ModuleSwitchContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    static final String FILTER_BEAN_NAME = "moduleSwitchTypeExcludeFilter";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        if (context.getBeanFactory().containsSingleton(FILTER_BEAN_NAME)) {
            return;
        }
        ModuleSwitchProperties properties = ModuleSwitchProperties.bind(context.getEnvironment());
        context.getBeanFactory()
            .registerSingleton(FILTER_BEAN_NAME,
                    new ModuleTypeExcludeFilter(properties, new ModulePackageResolver(context)));
    }
}
