package io.github.ilyaslabs.moduleswitch;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a module API method can safely run more than once.
 * <p>
 * Remote calls are normally retried only when the request certainly never reached the other
 * module. Idempotent methods are also retried after a read timeout and on 502, 503 and 504.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Idempotent {
}
