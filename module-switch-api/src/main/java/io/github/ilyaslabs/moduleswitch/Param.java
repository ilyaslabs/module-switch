package io.github.ilyaslabs.moduleswitch;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Names a parameter of a module API method on the wire.
 * <p>
 * Required on every parameter: the name is the key in the JSON request body, so callers keep
 * working when parameters are reordered or renamed in Java.
 * <pre>{@code
 * List<Product> getProducts(@Param("category") String category, @Param("page") int page);
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Param {

    /** The JSON field name of this parameter. */
    String value();
}
