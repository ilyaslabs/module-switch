package io.github.ilyaslabs.moduleswitch.core;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.ilyaslabs.moduleswitch.Param;

/**
 * Rules for module API interfaces, and lookups of their methods by wire name.
 * <ul>
 * <li>No overloaded methods: the method name is the endpoint name.</li>
 * <li>Every parameter carries {@link Param} with a unique, non-blank name.</li>
 * </ul>
 */
public final class ApiContract {

    private ApiContract() {
    }

    /** The abstract methods callers can invoke, sorted by name. Default and static methods are excluded. */
    public static List<Method> operations(Class<?> api) {
        List<Method> methods = new ArrayList<>();
        for (Method method : api.getMethods()) {
            if (Modifier.isAbstract(method.getModifiers()) && !method.isDefault()) {
                methods.add(method);
            }
        }
        methods.sort(Comparator.comparing(Method::getName));
        return methods;
    }

    /** The operations of an API keyed by method name; assumes {@link #validate} passed. */
    public static Map<String, Method> operationsByName(Class<?> api) {
        Map<String, Method> byName = new LinkedHashMap<>();
        for (Method method : operations(api)) {
            byName.put(method.getName(), method);
        }
        return byName;
    }

    /** The wire names of a method's parameters, in declaration order. */
    public static String[] parameterNames(Method method) {
        Parameter[] parameters = method.getParameters();
        String[] names = new String[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Param param = parameters[i].getAnnotation(Param.class);
            names[i] = (param != null) ? param.value() : null;
        }
        return names;
    }

    /** Every rule violation of an API interface; empty when valid. */
    public static List<String> validate(Class<?> api) {
        List<String> problems = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Method method : operations(api)) {
            counts.merge(method.getName(), 1, Integer::sum);
        }
        counts.forEach((name, count) -> {
            if (count > 1) {
                problems.add(api.getName() + " overloads method '" + name
                        + "'. Module APIs may not overload methods; give each variant its own name.");
            }
        });
        for (Method method : operations(api)) {
            Set<String> seen = new HashSet<>();
            Parameter[] parameters = method.getParameters();
            for (int i = 0; i < parameters.length; i++) {
                Param param = parameters[i].getAnnotation(Param.class);
                String where = api.getSimpleName() + "." + method.getName() + " parameter " + (i + 1);
                if (param == null) {
                    problems.add(where + " has no @Param. Every module API parameter needs @Param(\"name\").");
                }
                else if (param.value().isBlank()) {
                    problems.add(where + " has a blank @Param name.");
                }
                else if (!seen.add(param.value())) {
                    problems.add(where + " repeats @Param name '" + param.value() + "'.");
                }
            }
        }
        return problems;
    }
}
