package io.github.ilyaslabs.moduleswitch;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The only exceptions a module API may use to signal errors to its callers.
 * <p>
 * Each subtype maps to one HTTP status, so a caller sees the same exception whether the module
 * runs locally or remotely. Any other exception thrown by an implementation reaches callers as
 * {@link Internal}, in both modes.
 * <pre>{@code
 * throw new ModuleException.NotFound("Product 42 not found", "PRODUCT_NOT_FOUND", Map.of("id", 42));
 * }</pre>
 */
public abstract sealed class ModuleException extends RuntimeException
        permits ModuleException.BadRequest, ModuleException.Unauthorized, ModuleException.Forbidden, ModuleException.NotFound, ModuleException.Conflict, ModuleException.Internal, ModuleException.Unavailable {

    private static final long serialVersionUID = 1L;

    private final int status;

    private final String code;

    private final Map<String, Object> details;

    ModuleException(int status, String message, String code, Map<String, ?> details, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
        this.details = (details == null || details.isEmpty())
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    /** The HTTP status this exception maps to. */
    public int status() {
        return this.status;
    }

    /** An optional machine-readable error code, such as {@code PRODUCT_NOT_FOUND}; may be {@code null}. */
    public String code() {
        return this.code;
    }

    /** Optional structured details; never {@code null}. */
    public Map<String, Object> details() {
        return this.details;
    }

    /**
     * Creates the subtype for an HTTP status. Unknown 4xx statuses become {@link BadRequest},
     * 502 and 504 become {@link Unavailable}, and anything else becomes {@link Internal}.
     */
    public static ModuleException forStatus(int status, String message, String code, Map<String, ?> details,
            Throwable cause) {
        return switch (status) {
            case 400 -> new BadRequest(message, code, details, cause);
            case 401 -> new Unauthorized(message, code, details, cause);
            case 403 -> new Forbidden(message, code, details, cause);
            case 404 -> new NotFound(message, code, details, cause);
            case 409 -> new Conflict(message, code, details, cause);
            case 500 -> new Internal(message, code, details, cause);
            case 502, 503, 504 -> new Unavailable(message, code, details, cause);
            default -> (status >= 400 && status < 500)
                    ? new BadRequest(message, code, details, cause)
                    : new Internal(message, code, details, cause);
        };
    }

    /** 400: the request was invalid. */
    public static final class BadRequest extends ModuleException {

        public BadRequest(String message) {
            this(message, null, null, null);
        }

        public BadRequest(String message, String code) {
            this(message, code, null, null);
        }

        public BadRequest(String message, String code, Map<String, ?> details) {
            this(message, code, details, null);
        }

        public BadRequest(String message, String code, Map<String, ?> details, Throwable cause) {
            super(400, message, code, details, cause);
        }
    }

    /** 401: the caller is not authenticated. */
    public static final class Unauthorized extends ModuleException {

        public Unauthorized(String message) {
            this(message, null, null, null);
        }

        public Unauthorized(String message, String code) {
            this(message, code, null, null);
        }

        public Unauthorized(String message, String code, Map<String, ?> details) {
            this(message, code, details, null);
        }

        public Unauthorized(String message, String code, Map<String, ?> details, Throwable cause) {
            super(401, message, code, details, cause);
        }
    }

    /** 403: the caller may not perform this operation. */
    public static final class Forbidden extends ModuleException {

        public Forbidden(String message) {
            this(message, null, null, null);
        }

        public Forbidden(String message, String code) {
            this(message, code, null, null);
        }

        public Forbidden(String message, String code, Map<String, ?> details) {
            this(message, code, details, null);
        }

        public Forbidden(String message, String code, Map<String, ?> details, Throwable cause) {
            super(403, message, code, details, cause);
        }
    }

    /** 404: the requested resource does not exist. */
    public static final class NotFound extends ModuleException {

        public NotFound(String message) {
            this(message, null, null, null);
        }

        public NotFound(String message, String code) {
            this(message, code, null, null);
        }

        public NotFound(String message, String code, Map<String, ?> details) {
            this(message, code, details, null);
        }

        public NotFound(String message, String code, Map<String, ?> details, Throwable cause) {
            super(404, message, code, details, cause);
        }
    }

    /** 409: the request conflicts with the current state. */
    public static final class Conflict extends ModuleException {

        public Conflict(String message) {
            this(message, null, null, null);
        }

        public Conflict(String message, String code) {
            this(message, code, null, null);
        }

        public Conflict(String message, String code, Map<String, ?> details) {
            this(message, code, details, null);
        }

        public Conflict(String message, String code, Map<String, ?> details, Throwable cause) {
            super(409, message, code, details, cause);
        }
    }

    /** 500: the module failed unexpectedly. */
    public static final class Internal extends ModuleException {

        public Internal(String message) {
            this(message, null, null, null);
        }

        public Internal(String message, String code) {
            this(message, code, null, null);
        }

        public Internal(String message, String code, Map<String, ?> details) {
            this(message, code, details, null);
        }

        public Internal(String message, String code, Map<String, ?> details, Throwable cause) {
            super(500, message, code, details, cause);
        }
    }

    /** 503: the module could not be reached or is not ready. */
    public static final class Unavailable extends ModuleException {

        public Unavailable(String message) {
            this(message, null, null, null);
        }

        public Unavailable(String message, String code) {
            this(message, code, null, null);
        }

        public Unavailable(String message, String code, Map<String, ?> details) {
            this(message, code, details, null);
        }

        public Unavailable(String message, String code, Map<String, ?> details, Throwable cause) {
            super(503, message, code, details, cause);
        }
    }
}
