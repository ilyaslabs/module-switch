package io.github.ilyaslabs.moduleswitch;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a package, and every package beneath it, as the public API of a module.
 * <p>
 * The package holds only interfaces and DTOs. It is always available: callers inject its
 * interfaces and receive the local implementation or a generated HTTP client, depending on
 * the module's mode.
 * <pre>{@code
 * @ModuleApi("catalog")
 * package com.example.catalog.api;
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PACKAGE)
public @interface ModuleApi {

    /** The module name, as used under {@code module-switch.modules} in configuration. */
    String value();
}
