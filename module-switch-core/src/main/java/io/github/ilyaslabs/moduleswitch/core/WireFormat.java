package io.github.ilyaslabs.moduleswitch.core;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.ilyaslabs.moduleswitch.ModuleException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The JSON exchanged between a module client and the internal server.
 * <ul>
 * <li>Request: {@code POST /{module}/{Interface}/{method}} with a JSON object keyed by
 * {@code @Param} names. Missing parameters are {@code null} (or zero for primitives); unknown
 * fields are ignored, so callers and servers on different versions keep working.</li>
 * <li>Success: 200 with the result as JSON, or 204 for {@code void} methods and {@code null}
 * results.</li>
 * <li>Error: RFC 9457 {@code application/problem+json} with {@code code}, {@code details} and
 * {@code exception} extensions.</li>
 * </ul>
 */
public final class WireFormat {

    public static final String TOKEN_HEADER = "X-Module-Switch-Token";

    public static final String NOT_READY_HEADER = "X-Module-Switch-Not-Ready";

    public static final String JSON = "application/json";

    public static final String PROBLEM_JSON = "application/problem+json";

    private final JsonMapper mapper;

    public WireFormat(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public static String path(String module, Class<?> api, Method method) {
        return "/" + module + "/" + api.getSimpleName() + "/" + method.getName();
    }

    public byte[] writeArguments(Method method, Object[] args) {
        ObjectNode body = this.mapper.createObjectNode();
        String[] names = ApiContract.parameterNames(method);
        for (int i = 0; i < names.length; i++) {
            body.set(names[i], this.mapper.valueToTree(args[i]));
        }
        return this.mapper.writeValueAsBytes(body);
    }

    public Object[] readArguments(Method method, byte[] body) {
        JsonNode tree;
        try {
            tree = (body.length == 0) ? this.mapper.createObjectNode() : this.mapper.readTree(body);
        }
        catch (JacksonException ex) {
            throw new ModuleException.BadRequest("Request body is not valid JSON: " + ex.getOriginalMessage(),
                    "INVALID_BODY", null, ex);
        }
        if (!tree.isObject()) {
            throw new ModuleException.BadRequest("Request body must be a JSON object keyed by parameter name.");
        }
        String[] names = ApiContract.parameterNames(method);
        Type[] types = method.getGenericParameterTypes();
        Object[] args = new Object[names.length];
        for (int i = 0; i < names.length; i++) {
            JsonNode value = tree.get(names[i]);
            Class<?> raw = method.getParameterTypes()[i];
            if (value == null || value.isNull()) {
                args[i] = raw.isPrimitive() ? defaultValue(raw) : null;
                continue;
            }
            try {
                args[i] = this.mapper.readerFor(javaType(types[i])).readValue(value);
            }
            catch (JacksonException ex) {
                throw new ModuleException.BadRequest("Parameter '" + names[i] + "' is not a valid "
                        + raw.getSimpleName() + ": " + ex.getOriginalMessage(), "INVALID_PARAMETER",
                        Map.of("parameter", names[i]), ex);
            }
        }
        return args;
    }

    public byte[] writeResult(Object result) {
        return this.mapper.writeValueAsBytes(result);
    }

    public Object readResult(Method method, byte[] body) {
        Class<?> raw = method.getReturnType();
        if (raw == void.class) {
            return null;
        }
        if (body.length == 0) {
            if (raw.isPrimitive()) {
                throw new ModuleException.Internal("Empty response for " + method.getName() + " returning " + raw);
            }
            return null;
        }
        return this.mapper.readValue(body, javaType(method.getGenericReturnType()));
    }

    public byte[] writeProblem(ModuleException exception) {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", exception.getClass().getSimpleName());
        problem.put("status", exception.status());
        problem.put("detail", exception.getMessage());
        problem.put("exception", exception.getClass().getSimpleName());
        if (exception.code() != null) {
            problem.put("code", exception.code());
        }
        if (!exception.details().isEmpty()) {
            problem.put("details", exception.details());
        }
        return this.mapper.writeValueAsBytes(problem);
    }

    /** Rebuilds the exception from an error response; tolerates bodies that are not problem JSON. */
    public ModuleException readProblem(int status, byte[] body, String fallbackMessage) {
        String detail = null;
        String code = null;
        Map<String, Object> details = null;
        if (body.length > 0) {
            try {
                JsonNode tree = this.mapper.readTree(body);
                if (tree.isObject()) {
                    detail = text(tree, "detail");
                    code = text(tree, "code");
                    JsonNode node = tree.get("details");
                    if (node != null && node.isObject()) {
                        details = this.mapper.convertValue(node,
                                this.mapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
                    }
                }
            }
            catch (JacksonException ex) {
                // Not JSON, for example an HTML error page from a proxy.
            }
        }
        return ModuleException.forStatus(status, (detail != null) ? detail : fallbackMessage, code, details, null);
    }

    private JavaType javaType(Type type) {
        return this.mapper.getTypeFactory().constructType(type);
    }

    private static String text(JsonNode tree, String field) {
        JsonNode node = tree.get(field);
        return (node != null && node.isString()) ? node.asString() : null;
    }

    private static Object defaultValue(Class<?> primitive) {
        return java.lang.reflect.Array.get(java.lang.reflect.Array.newInstance(primitive, 1), 0);
    }
}
