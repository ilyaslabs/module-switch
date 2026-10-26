package io.github.ilyaslabs.moduleswitch.autoconfigure;

import io.github.ilyaslabs.moduleswitch.core.ModuleSwitchProperties;
import io.github.ilyaslabs.moduleswitch.core.ModuleSwitchRegistrar;
import io.github.ilyaslabs.moduleswitch.core.ModuleSwitchRuntime;
import io.github.ilyaslabs.moduleswitch.core.ModuleSwitchServer;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextClosedEvent;

/**
 * Auto-configuration for Module-Switch.
 * <p>
 * The component-scan filter that skips modules which aren't local is registered earlier, by
 * {@link ModuleSwitchContextInitializer}, because scanning happens before auto-configuration.
 */
@AutoConfiguration
public final class ModuleSwitchAutoConfiguration {

    @Bean
    static ModuleSwitchRegistrar moduleSwitchRegistrar() {
        return new ModuleSwitchRegistrar();
    }

    @Bean(destroyMethod = "")
    @ConditionalOnProperty(prefix = ModuleSwitchProperties.PREFIX + ".expose", name = "port")
    ModuleSwitchServer moduleSwitchServer(ModuleSwitchRuntime runtime) {
        return runtime.createServer();
    }

    @Bean
    @ConditionalOnProperty(prefix = ModuleSwitchProperties.PREFIX + ".expose", name = "port")
    static ApplicationListener<ApplicationReadyEvent> moduleSwitchServerReady(
            org.springframework.beans.factory.ObjectProvider<ModuleSwitchServer> server) {
        return (event) -> server.ifAvailable((it) -> it.setReady(true));
    }

    @Bean
    @ConditionalOnProperty(prefix = ModuleSwitchProperties.PREFIX + ".expose", name = "port")
    static ApplicationListener<ContextClosedEvent> moduleSwitchServerClosing(
            org.springframework.beans.factory.ObjectProvider<ModuleSwitchServer> server) {
        return (event) -> server.ifAvailable((it) -> it.setReady(false));
    }
}
