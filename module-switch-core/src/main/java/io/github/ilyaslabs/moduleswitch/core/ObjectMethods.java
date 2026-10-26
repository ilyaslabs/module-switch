package io.github.ilyaslabs.moduleswitch.core;

import java.lang.reflect.Method;

/** {@code equals}, {@code hashCode} and {@code toString} for Module-Switch proxies. */
final class ObjectMethods {

    private ObjectMethods() {
    }

    static Object handle(Object proxy, Method method, Object[] args, String description) {
        return switch (method.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "Module-Switch proxy for " + description;
            default -> throw new UnsupportedOperationException(method.toString());
        };
    }
}
