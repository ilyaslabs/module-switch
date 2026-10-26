package io.github.ilyaslabs.moduleswitch.it.shop.support;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** A custom scan that bypasses Module-Switch. Enabled by a test property. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("test.bypass")
@ComponentScan("io.github.ilyaslabs.moduleswitch.it.shop.users.impl")
class BypassConfiguration {
}
