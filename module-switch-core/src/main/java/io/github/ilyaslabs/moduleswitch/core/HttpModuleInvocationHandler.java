package io.github.ilyaslabs.moduleswitch.core;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import io.github.ilyaslabs.moduleswitch.Idempotent;
import io.github.ilyaslabs.moduleswitch.ModuleException;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.RequestReplySenderContext;

/**
 * Generated client for a module running in another deployment.
 * <p>
 * Each call is a {@code POST} to {@code {url}/{module}/{Interface}/{method}}. Error responses are
 * rebuilt as the same {@link ModuleException} subtype; failures to reach the module become
 * {@link ModuleException.Unavailable} after retries.
 */
final class HttpModuleInvocationHandler implements InvocationHandler {

    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final String module;

    private final Class<?> api;

    private final URI baseUrl;

    private final ModuleSwitchProperties.ModuleProperties settings;

    private final List<String> propagateHeaders;

    private final HttpClient client;

    private final Supplier<WireFormat> wire;

    private final Supplier<ObservationRegistry> observations;

    private final HeaderSource headerSource;

    private final RetryPolicy retryPolicy;

    private final Sleeper sleeper;

    HttpModuleInvocationHandler(String module, Class<?> api, ModuleSwitchProperties.ModuleProperties settings,
            List<String> propagateHeaders, HttpClient client, Supplier<WireFormat> wire,
            Supplier<ObservationRegistry> observations, HeaderSource headerSource, Sleeper sleeper) {
        this.module = module;
        this.api = api;
        this.baseUrl = stripTrailingSlash(settings.getUrl());
        this.settings = settings;
        this.propagateHeaders = List.copyOf(propagateHeaders);
        this.client = client;
        this.wire = wire;
        this.observations = observations;
        this.headerSource = headerSource;
        this.retryPolicy = new RetryPolicy(settings.getRetry().getMaxAttempts(), settings.getRetry().getInitialBackoff());
        this.sleeper = sleeper;
    }

    static HttpClient newClient(ModuleSwitchProperties.ModuleProperties settings) {
        return HttpClient.newBuilder()
            .connectTimeout(settings.getConnectTimeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return ObjectMethods.handle(proxy, method, args, "remote " + this.api.getName() + " at " + this.baseUrl);
        }
        if (method.isDefault()) {
            return InvocationHandler.invokeDefault(proxy, method, args);
        }
        URI uri = URI.create(this.baseUrl + WireFormat.path(this.module, this.api, method));
        byte[] body = this.wire.get().writeArguments(method, (args != null) ? args : new Object[0]);
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
            .timeout(this.settings.getReadTimeout())
            .header("Content-Type", WireFormat.JSON)
            .header("Accept", WireFormat.JSON + ", " + WireFormat.PROBLEM_JSON)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (this.settings.getToken() != null) {
            request.header(WireFormat.TOKEN_HEADER, this.settings.getToken());
        }
        for (String name : this.propagateHeaders) {
            this.headerSource.header(name).ifPresent((value) -> request.header(name, value));
        }

        RequestReplySenderContext<HttpRequest.Builder, HttpResponse<byte[]>> context = new RequestReplySenderContext<>(
                (carrier, key, value) -> carrier.setHeader(key, value));
        context.setCarrier(request);
        context.setRemoteServiceName(this.module);
        Observation observation = Observation
            .createNotStarted(CallObservations.NAME, () -> context, this.observations.get())
            .contextualName(CallObservations.contextualName(this.api, method))
            .lowCardinalityKeyValues(CallObservations.tags(this.module, this.api, method, ModuleMode.HTTP))
            .start();
        Throwable failure = null;
        try (Observation.Scope scope = observation.openScope()) {
            HttpResponse<byte[]> response = send(request.build(), method);
            context.setResponse(response);
            return this.wire.get().readResult(method, response.statusCode() == 204 || method.getReturnType() == void.class
                    ? new byte[0] : response.body());
        }
        catch (ModuleException ex) {
            failure = ex;
            throw ex;
        }
        catch (RuntimeException ex) {
            failure = new ModuleException.Internal("Call to module '" + this.module + "' failed: " + ex, null, null, ex);
            throw failure;
        }
        finally {
            CallObservations.outcome(observation, failure);
            observation.stop();
        }
    }

    private HttpResponse<byte[]> send(HttpRequest request, Method method) {
        boolean idempotent = method.isAnnotationPresent(Idempotent.class);
        for (int attempt = 1;; attempt++) {
            RetryPolicy.Failure kind;
            ModuleException error;
            try {
                HttpResponse<byte[]> response = this.client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    return response;
                }
                error = this.wire.get().readProblem(status, response.body(),
                        "Module '" + this.module + "' answered HTTP " + status);
                if (status == 503 && response.headers().firstValue(WireFormat.NOT_READY_HEADER).isPresent()) {
                    kind = RetryPolicy.Failure.NEVER_SENT;
                }
                else if (status == 502 || status == 503 || status == 504) {
                    kind = RetryPolicy.Failure.OUTCOME_UNKNOWN;
                }
                else {
                    kind = RetryPolicy.Failure.FINAL;
                }
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw unavailable("was interrupted", ex);
            }
            catch (IOException | UnresolvedAddressException ex) {
                kind = neverSent(ex) ? RetryPolicy.Failure.NEVER_SENT : RetryPolicy.Failure.OUTCOME_UNKNOWN;
                error = unavailable(describe(ex), ex);
            }
            if (!this.retryPolicy.shouldRetry(kind, idempotent, attempt)) {
                throw error;
            }
            try {
                this.sleeper.sleep(this.retryPolicy.backoff(attempt));
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw error;
            }
        }
    }

    static boolean neverSent(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpConnectTimeoutException || cause instanceof ConnectException
                    || cause instanceof UnknownHostException || cause instanceof UnresolvedAddressException) {
                return true;
            }
        }
        return false;
    }

    private String describe(Throwable ex) {
        if (ex instanceof HttpConnectTimeoutException) {
            return "did not accept a connection within " + this.settings.getConnectTimeout();
        }
        if (ex instanceof HttpTimeoutException) {
            return "did not answer within " + this.settings.getReadTimeout();
        }
        if (neverSent(ex)) {
            return "is unreachable (" + ex + ")";
        }
        return "failed mid-call (" + ex + ")";
    }

    private ModuleException unavailable(String what, Throwable cause) {
        return new ModuleException.Unavailable("Module '" + this.module + "' at " + this.baseUrl + " " + what,
                "MODULE_UNAVAILABLE", Map.of("module", this.module), cause);
    }

    private static URI stripTrailingSlash(URI url) {
        String value = url.toString();
        return value.endsWith("/") ? URI.create(value.substring(0, value.length() - 1)) : url;
    }
}
