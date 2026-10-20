package io.github.ilyaslabs.moduleswitch.core;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Headers of the module call currently being served on the internal port.
 * <p>
 * The internal port runs no web filters, so implementations read forwarded headers, such as
 * {@code Authorization}, from here. They are also forwarded again when the implementation calls
 * another remote module.
 */
public final class ModuleCallContext {

    private static final ThreadLocal<ModuleCallContext> CURRENT = new ThreadLocal<>();

    private final String module;

    private final Map<String, String> headers;

    ModuleCallContext(String module, Map<String, String> headers) {
        this.module = module;
        Map<String, String> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        copy.putAll(headers);
        this.headers = Collections.unmodifiableMap(copy);
    }

    /** The context of the call being served on this thread, if any. */
    public static Optional<ModuleCallContext> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** The module being called. */
    public String module() {
        return this.module;
    }

    /** A forwarded header, case-insensitive. */
    public Optional<String> header(String name) {
        return Optional.ofNullable(this.headers.get(name));
    }

    /** All forwarded headers, with case-insensitive keys. */
    public Map<String, String> headers() {
        return this.headers;
    }

    static <T> T runWith(ModuleCallContext context, Supplier<T> action) {
        ModuleCallContext previous = CURRENT.get();
        CURRENT.set(context);
        try {
            return action.get();
        }
        finally {
            if (previous != null) {
                CURRENT.set(previous);
            }
            else {
                CURRENT.remove();
            }
        }
    }
}
