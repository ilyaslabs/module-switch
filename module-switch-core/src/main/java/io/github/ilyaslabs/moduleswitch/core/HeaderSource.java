package io.github.ilyaslabs.moduleswitch.core;

import java.util.Optional;

import org.springframework.util.ClassUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Looks up headers of the request being handled on the current thread, so they can be forwarded
 * on remote module calls.
 * <p>
 * Checks, in order, the module call being served on the internal port and the current servlet
 * request (when Spring Web is present).
 */
public interface HeaderSource {

    Optional<String> header(String name);

    /** The default source: internal-port calls, then servlet requests. */
    static HeaderSource standard() {
        boolean servlet = ClassUtils.isPresent("org.springframework.web.context.request.RequestContextHolder", null)
                && ClassUtils.isPresent("jakarta.servlet.http.HttpServletRequest", null);
        return (name) -> {
            Optional<String> value = ModuleCallContext.current().flatMap((context) -> context.header(name));
            if (value.isEmpty() && servlet) {
                value = ServletHeaders.header(name);
            }
            return value;
        };
    }

    /** Kept separate so Spring Web classes are only loaded when present. */
    final class ServletHeaders {

        private ServletHeaders() {
        }

        static Optional<String> header(String name) {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes servlet) {
                return Optional.ofNullable(servlet.getRequest().getHeader(name));
            }
            return Optional.empty();
        }
    }
}
