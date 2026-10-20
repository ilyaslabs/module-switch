package io.github.ilyaslabs.moduleswitch.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Checks that a class only depends on another module through that module's API.
 * <p>
 * Looks at the types of fields, constructor and method parameters, method return types,
 * the superclass and interfaces, including generic type arguments.
 */
public class BoundaryChecker {

    private final ModulePackageResolver resolver;

    public BoundaryChecker(ModulePackageResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * Every reference from {@code type} to another module's implementation packages.
     * A class outside any module may reference no module implementation at all.
     */
    public List<String> check(Class<?> type) {
        Optional<ModuleMembership> owner = this.resolver.forClass(type);
        List<String> problems = new ArrayList<>();
        for (Class<?> referenced : referencedTypes(type)) {
            Optional<ModuleMembership> target = this.resolver.forClass(referenced);
            if (target.isEmpty() || !target.get().isImpl()) {
                continue;
            }
            if (owner.isPresent() && owner.get().module().equals(target.get().module())) {
                continue;
            }
            if (owner.isPresent() && owner.get().isApi()) {
                problems.add(type.getName() + " in the API of module '" + owner.get().module()
                        + "' uses " + referenced.getName() + " from the implementation of module '"
                        + target.get().module() + "'. API packages may only use API types.");
            }
            else {
                String from = owner.map((m) -> "module '" + m.module() + "'").orElse("code outside any module");
                problems.add(type.getName() + " (" + from + ") uses " + referenced.getName()
                        + " from the implementation of module '" + target.get().module()
                        + "'. Depend on that module's API instead.");
            }
        }
        return problems;
    }

    static Set<Class<?>> referencedTypes(Class<?> type) {
        Set<Class<?>> types = new LinkedHashSet<>();
        try {
            collect(type.getGenericSuperclass(), types);
            for (Type each : type.getGenericInterfaces()) {
                collect(each, types);
            }
            for (Field field : type.getDeclaredFields()) {
                if (!field.isSynthetic()) {
                    collect(field.getGenericType(), types);
                }
            }
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                if (!constructor.isSynthetic()) {
                    for (Type each : constructor.getGenericParameterTypes()) {
                        collect(each, types);
                    }
                }
            }
            for (Method method : type.getDeclaredMethods()) {
                if (method.isSynthetic() || method.isBridge()) {
                    continue;
                }
                collect(method.getGenericReturnType(), types);
                for (Type each : method.getGenericParameterTypes()) {
                    collect(each, types);
                }
            }
        }
        catch (LinkageError ex) {
            // A referenced type is missing from the classpath; nothing more can be checked.
        }
        types.remove(type);
        return types;
    }

    private static void collect(Type type, Set<Class<?>> into) {
        switch (type) {
            case null -> {
            }
            case Class<?> clazz -> {
                Class<?> component = clazz;
                while (component.isArray()) {
                    component = component.getComponentType();
                }
                if (!component.isPrimitive()) {
                    into.add(component);
                }
            }
            case ParameterizedType parameterized -> {
                collect(parameterized.getRawType(), into);
                for (Type argument : parameterized.getActualTypeArguments()) {
                    collect(argument, into);
                }
            }
            case GenericArrayType array -> collect(array.getGenericComponentType(), into);
            case WildcardType wildcard -> {
                for (Type bound : wildcard.getUpperBounds()) {
                    collect(bound, into);
                }
                for (Type bound : wildcard.getLowerBounds()) {
                    collect(bound, into);
                }
            }
            case TypeVariable<?> variable -> {
                // Bounds of type variables are declared elsewhere; skipping avoids infinite recursion.
            }
            default -> {
            }
        }
    }
}
