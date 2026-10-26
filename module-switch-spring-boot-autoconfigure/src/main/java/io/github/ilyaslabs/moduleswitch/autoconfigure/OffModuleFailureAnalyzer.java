package io.github.ilyaslabs.moduleswitch.autoconfigure;

import java.util.Locale;
import java.util.Optional;

import io.github.ilyaslabs.moduleswitch.core.ModuleMembership;
import io.github.ilyaslabs.moduleswitch.core.ModuleMode;
import io.github.ilyaslabs.moduleswitch.core.ModulePackageResolver;
import io.github.ilyaslabs.moduleswitch.core.ModuleSwitchProperties;

import org.springframework.beans.factory.InjectionPoint;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.core.env.Environment;

/**
 * Explains a missing module API bean: the module that provides it is off.
 */
class OffModuleFailureAnalyzer extends AbstractFailureAnalyzer<NoSuchBeanDefinitionException> {

    private final Environment environment;

    OffModuleFailureAnalyzer(Environment environment) {
        this.environment = environment;
    }

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, NoSuchBeanDefinitionException cause) {
        Class<?> type = cause.getBeanType();
        if (type == null || !type.isInterface()) {
            return null;
        }
        Optional<ModuleMembership> membership = new ModulePackageResolver().forClass(type);
        if (membership.isEmpty() || !membership.get().isApi()) {
            return null;
        }
        String module = membership.get().module();
        ModuleSwitchProperties properties = ModuleSwitchProperties.bind(this.environment);
        ModuleMode mode = properties.modeOf(module);
        if (mode != ModuleMode.OFF) {
            return null;
        }
        String consumer = consumer(rootFailure);
        String description = consumer + " needs " + type.getSimpleName() + ", but module '" + module + "' is "
                + mode.name().toLowerCase(Locale.ROOT) + ".";
        String action = "Set " + ModuleSwitchProperties.PREFIX + ".modules." + module
                + ".mode to local (run it here) or http (call it on another deployment).";
        return new FailureAnalysis(description, action, cause);
    }

    private String consumer(Throwable rootFailure) {
        UnsatisfiedDependencyException unsatisfied = findCause(rootFailure, UnsatisfiedDependencyException.class);
        if (unsatisfied != null) {
            InjectionPoint injectionPoint = unsatisfied.getInjectionPoint();
            if (injectionPoint != null && injectionPoint.getMember() != null) {
                return injectionPoint.getMember().getDeclaringClass().getSimpleName();
            }
            if (unsatisfied.getBeanName() != null) {
                return "Bean '" + unsatisfied.getBeanName() + "'";
            }
        }
        return "A bean";
    }
}
