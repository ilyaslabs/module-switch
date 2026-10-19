package io.github.ilyaslabs.moduleswitch.core;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * Configuration under the {@code module-switch} prefix.
 * <pre>
 * module-switch:
 *   modules:
 *     catalog:
 *       mode: http
 *       url: http://catalog-host:8081
 *   expose:
 *     port: 8081
 *     modules: [orders]
 * </pre>
 */
@ConfigurationProperties(prefix = ModuleSwitchProperties.PREFIX)
public class ModuleSwitchProperties {

    public static final String PREFIX = "module-switch";

    /** Per-module settings, keyed by module name. Modules not listed are off. */
    private Map<String, ModuleProperties> modules = new LinkedHashMap<>();

    /** Internal HTTP server that makes local modules callable from other deployments. */
    private Expose expose = new Expose();

    /** Incoming request headers forwarded on every remote module call. */
    private List<String> propagateHeaders = new ArrayList<>(List.of("Authorization"));

    /**
     * Extra packages to search for {@code package-info} module declarations, in addition to the
     * application's auto-configuration packages.
     */
    private List<String> basePackages = new ArrayList<>();

    /** Binds the properties from an environment, before the context exists. */
    public static ModuleSwitchProperties bind(Environment environment) {
        return Binder.get(environment)
                .bind(PREFIX, Bindable.of(ModuleSwitchProperties.class))
                .orElseGet(ModuleSwitchProperties::new);
    }

    /** The mode of a module; {@link ModuleMode#OFF} when not configured. */
    public ModuleMode modeOf(String module) {
        ModuleProperties properties = this.modules.get(module);
        return (properties != null) ? properties.getMode() : ModuleMode.OFF;
    }

    /** The settings of a module, or defaults when not configured. */
    public ModuleProperties module(String module) {
        return this.modules.getOrDefault(module, new ModuleProperties());
    }

    public Map<String, ModuleProperties> getModules() {
        return this.modules;
    }

    public void setModules(Map<String, ModuleProperties> modules) {
        this.modules = modules;
    }

    public Expose getExpose() {
        return this.expose;
    }

    public void setExpose(Expose expose) {
        this.expose = expose;
    }

    public List<String> getPropagateHeaders() {
        return this.propagateHeaders;
    }

    public void setPropagateHeaders(List<String> propagateHeaders) {
        this.propagateHeaders = propagateHeaders;
    }

    public List<String> getBasePackages() {
        return this.basePackages;
    }

    public void setBasePackages(List<String> basePackages) {
        this.basePackages = basePackages;
    }

    /** Settings of one module. */
    public static class ModuleProperties {

        /** local, http or off. */
        private ModuleMode mode = ModuleMode.OFF;

        /** Base URL of the deployment that exposes this module; required in http mode. */
        private URI url;

        /** Shared token sent as X-Module-Switch-Token on calls to this module. */
        private String token;

        /** Time allowed to open a connection, in http mode. */
        private Duration connectTimeout = Duration.ofSeconds(2);

        /** Time allowed for the response, in http mode. */
        private Duration readTimeout = Duration.ofSeconds(10);

        private Retry retry = new Retry();

        public ModuleMode getMode() {
            return this.mode;
        }

        public void setMode(ModuleMode mode) {
            this.mode = mode;
        }

        public URI getUrl() {
            return this.url;
        }

        public void setUrl(URI url) {
            this.url = url;
        }

        public String getToken() {
            return this.token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public Duration getConnectTimeout() {
            return this.connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return this.readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }

        public Retry getRetry() {
            return this.retry;
        }

        public void setRetry(Retry retry) {
            this.retry = retry;
        }
    }

    /** Retry settings for remote calls. */
    public static class Retry {

        /** Total attempts including the first; 1 disables retries. */
        private int maxAttempts = 3;

        /** Wait before the first retry; doubles on each attempt, with jitter. */
        private Duration initialBackoff = Duration.ofMillis(100);

        public int getMaxAttempts() {
            return this.maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getInitialBackoff() {
            return this.initialBackoff;
        }

        public void setInitialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
        }
    }

    /** Settings of the internal server. */
    public static class Expose {

        /** Internal port. When unset, nothing is exposed. 0 picks a free port. */
        private Integer port;

        /** Local modules to make callable on the internal port, each under /{module}. */
        private List<String> modules = new ArrayList<>();

        /** When set, every call must carry this value in X-Module-Switch-Token. */
        private String token;

        public Integer getPort() {
            return this.port;
        }

        public void setPort(Integer port) {
            this.port = port;
        }

        public List<String> getModules() {
            return this.modules;
        }

        public void setModules(List<String> modules) {
            this.modules = modules;
        }

        public String getToken() {
            return this.token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }
}
