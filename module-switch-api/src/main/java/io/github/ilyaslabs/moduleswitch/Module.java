package io.github.ilyaslabs.moduleswitch;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a package, and every package beneath it, as implementation code of a module.
 * <p>
 * Declare it once in the root {@code package-info.java} of each implementation child:
 * <pre>{@code
 * @Module("catalog")
 * package com.example.catalog.impl;
 * }</pre>
 * Classes in the package are loaded only when the module's mode is {@code local}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PACKAGE)
public @interface Module {

    /** The module name, as used under {@code module-switch.modules} in configuration. */
    String value();
}
